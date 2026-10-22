package service;

import api.BatchParser.SessionInput;
import dao.AlertDao;
import dao.DeviceDao;
import dao.GasEventDao;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import model.DeviceCommand;

/**
 * Pipeline for the sessions of one request: compensate, apply the rules, ask the model, store the
 * session with both results, then raise alerts. Each session is stored once with its results because
 * GasEvent is append-only.
 */
public class IngestService {

    private final RiskScorer scorer; // null when no model is trained yet
    private final RuleEngine rules = new RuleEngine();
    private final DeviceDao deviceDao = new DeviceDao();
    private final GasEventDao eventDao = new GasEventDao();
    private final AlertDao alertDao = new AlertDao();

    public IngestService(RiskScorer scorer) {
        this.scorer = scorer;
    }

    public Result ingest(int deviceId, List<SessionInput> sessions) throws SQLException {
        DeviceSetup setup = DeviceSetup.load(deviceDao, deviceId);
        CompensationModel compensation = setup.getCompensation();
        int historySize = setup.getRules().getStuckCount() - 1;
        LinkedList<Double> history = eventDao.recentMq2(deviceId, historySize);

        List<SessionInput> ordered = new ArrayList<SessionInput>(sessions);
        Collections.sort(ordered, new Comparator<SessionInput>() {
            @Override
            public int compare(SessionInput a, SessionInput b) {
                return Integer.compare(a.getSeq(), b.getSeq());
            }
        });

        Result result = new Result();
        for (SessionInput s : ordered) {
            Double mq2Compensated = compensation.compensateMq2(s.getMq2Raw(), s.getTempC(), s.getRhPct());
            Double mq135Compensated = compensation.compensateMq135(s.getMq135Raw(), s.getTempC(), s.getRhPct());
            RuleEngine.Outcome outcome = rules.evaluate(new RuleEngine.Reading(s.getMq2Raw(), s.getMq135Raw(),
                    s.getTempC(), s.getRhPct(), mq2Compensated, mq135Compensated, s.getFlame()),
                    setup.getRules(), history);
            Prediction prediction = predict(s, mq2Compensated);

            long sessionId = eventDao.insert(deviceId, s.getSeq(), Timestamp.valueOf(s.getMeasuredAt()),
                    s.getMq2Raw(), s.getMq135Raw(), s.getTempC(), s.getRhPct(), mq2Compensated, mq135Compensated,
                    s.getFlame(), outcome.getLabel(), prediction == null ? null : prediction.getLabel(),
                    prediction == null ? null : prediction.getConfidence());
            if (sessionId < 0) {
                result.duplicates++;
                continue;
            }
            result.saved++;
            history.addFirst(s.getMq2Raw());
            while (history.size() > historySize) {
                history.removeLast();
            }
            for (AlertService.Draft draft : AlertService.decide(outcome, prediction)) {
                int alertId = alertDao.raise(deviceId, sessionId, draft.getSource(), draft.getLabel(),
                        draft.getRiskScore(), draft.getEvidence(), AlertService.DEDUPE_MINUTES);
                if (alertId > 0) {
                    result.alertsRaised++;
                }
            }
        }
        result.commands = deviceDao.takePendingCommands(deviceId);
        deviceDao.touch(deviceId);
        return result;
    }

    private Prediction predict(SessionInput s, Double mq2Compensated) {
        if (scorer == null) {
            return null;
        }
        try {
            return scorer.score(s.getMq2Raw(), s.getMq135Raw(), s.getTempC(), s.getRhPct(), mq2Compensated);
        } catch (Exception e) {
            return null; // the rules still protect the kitchen when the model fails
        }
    }

    /** Counts for the API reply, and the commands the device must carry out. */
    public static final class Result {

        private int saved;
        private int duplicates;
        private int alertsRaised;
        private List<DeviceCommand> commands = Collections.emptyList();

        public int getSaved() {
            return saved;
        }

        public int getDuplicates() {
            return duplicates;
        }

        public int getAlertsRaised() {
            return alertsRaised;
        }

        public List<DeviceCommand> getCommands() {
            return commands;
        }
    }
}
