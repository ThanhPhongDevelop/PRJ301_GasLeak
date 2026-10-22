package service;

import dao.DeviceDao;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import model.SensorLevels;

/** Everything the pipeline needs to judge the sessions of one device: compensation and rule levels. */
public final class DeviceSetup {

    /** Sensor type names as stored in SensorType (seed data). */
    public static final String MQ2 = "MQ-2";
    public static final String MQ135 = "MQ-135";
    public static final String TEMPERATURE = "DHT22 temperature";
    public static final String HUMIDITY = "DHT22 humidity";

    private final CompensationModel compensation;
    private final RuleEngine.Config rules;

    private DeviceSetup(CompensationModel compensation, RuleEngine.Config rules) {
        this.compensation = compensation;
        this.rules = rules;
    }

    public static DeviceSetup load(DeviceDao dao, int deviceId) throws SQLException {
        CompensationModel compensation = CompensationModel.fromJson(dao.latestProfileJson(deviceId));
        RuleEngine.Config rules = new RuleEngine.Config();
        String zone = null;
        List<SensorLevels> levels = dao.zoneLevels(deviceId);
        for (SensorLevels level : levels) {
            if (level.getWarnLevel() >= level.getAlarmLevel()) {
                continue; // the database check forbids this; skip rather than fail the whole batch
            }
            if (MQ2.equals(level.getSensorName())) {
                rules.mq2Levels(new RuleEngine.Levels(level.getWarnLevel(), level.getAlarmLevel()));
                zone = level.getZoneName();
            } else if (MQ135.equals(level.getSensorName())) {
                rules.mq135Levels(new RuleEngine.Levels(level.getWarnLevel(), level.getAlarmLevel()));
                if (zone == null) {
                    zone = level.getZoneName();
                }
            }
        }
        rules.thresholdSource(zone == null ? "default levels" : "zone " + zone);
        Map<String, double[]> limits = dao.sensorLimits();
        if (limits.containsKey(MQ2)) {
            rules.mq2Range(range(limits.get(MQ2)));
        }
        if (limits.containsKey(MQ135)) {
            rules.mq135Range(range(limits.get(MQ135)));
        }
        if (limits.containsKey(TEMPERATURE)) {
            rules.tempRange(range(limits.get(TEMPERATURE)));
        }
        if (limits.containsKey(HUMIDITY)) {
            rules.rhRange(range(limits.get(HUMIDITY)));
        }
        return new DeviceSetup(compensation, rules);
    }

    private static RuleEngine.Range range(double[] minMax) {
        return new RuleEngine.Range(minMax[0], minMax[1]);
    }

    public CompensationModel getCompensation() {
        return compensation;
    }

    public RuleEngine.Config getRules() {
        return rules;
    }
}
