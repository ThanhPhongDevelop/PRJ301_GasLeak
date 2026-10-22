package tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import model.Labels;
import service.CompensationModel;
import service.SignalMath;

/**
 * The measurement behind the main claim of the project: how many false alarms the temperature and
 * humidity compensation removes. A leak threshold is set on the MQ-2 value so that it catches a fixed
 * share of the leak sessions, once on the raw value and once on the compensated value, and the share
 * of non-leak sessions above the threshold (false alarms) is compared. Recordings are split into
 * folds so the compensation and the threshold are always tested on recordings they were not fitted on.
 */
public final class CompensationAnalysis {

    private CompensationAnalysis() {
    }

    public static CompensationModel fitSafe(List<Sample> samples, String source) {
        List<Sample> safe = new ArrayList<Sample>();
        for (Sample s : samples) {
            if (Labels.SAFE.equals(s.label)) {
                safe.add(s);
            }
        }
        double[] mq2 = new double[safe.size()];
        double[] mq135 = new double[safe.size()];
        double[] t = new double[safe.size()];
        double[] rh = new double[safe.size()];
        for (int i = 0; i < safe.size(); i++) {
            mq2[i] = safe.get(i).mq2;
            mq135[i] = safe.get(i).mq135;
            t[i] = safe.get(i).tempC;
            rh[i] = safe.get(i).rhPct;
        }
        return CompensationModel.fit(mq2, mq135, t, rh, source);
    }

    public static Report evaluate(List<Sample> samples, int folds, double targetDetection, long seed) {
        List<Integer> groups = new ArrayList<Integer>(new TreeSet<Integer>(groupsOf(samples)));
        Collections.shuffle(groups, new Random(seed));
        Map<Integer, Integer> foldOf = new HashMap<Integer, Integer>();
        for (int i = 0; i < groups.size(); i++) {
            foldOf.put(groups.get(i), i % folds);
        }
        Report report = new Report(targetDetection);
        for (int fold = 0; fold < folds; fold++) {
            List<Sample> train = new ArrayList<Sample>();
            List<Sample> test = new ArrayList<Sample>();
            for (Sample s : samples) {
                (foldOf.get(s.group) == fold ? test : train).add(s);
            }
            if (test.isEmpty() || values(train, null, true).length == 0) {
                continue;
            }
            CompensationModel model;
            try {
                model = fitSafe(train, "fold " + fold);
            } catch (IllegalArgumentException e) {
                continue; // not enough clean-air variety in this split
            }
            report.raw.add(train, test, null, targetDetection);
            report.compensated.add(train, test, model, targetDetection);
        }
        return report;
    }

    /** Warning and alarm levels for the replay device, from all sessions and the final model. */
    public static double[][] suggestLevels(List<Sample> samples, CompensationModel model, double targetDetection) {
        double[] leakMq2 = values(samples, model, true);
        double[] cookingMq135 = new double[0];
        List<Double> cooking = new ArrayList<Double>();
        for (Sample s : samples) {
            Double value = model.compensateMq135(s.mq135, s.tempC, s.rhPct);
            if (Labels.COOKING_FUMES.equals(s.label) && value != null) {
                cooking.add(value);
            }
        }
        if (!cooking.isEmpty()) {
            cookingMq135 = new double[cooking.size()];
            for (int i = 0; i < cookingMq135.length; i++) {
                cookingMq135[i] = cooking.get(i);
            }
        }
        double[] mq2 = levelsFrom(leakMq2, model.getMq2(), targetDetection);
        double[] mq135 = levelsFrom(cookingMq135, model.getMq135(), targetDetection);
        return new double[][]{mq2, mq135};
    }

    /**
     * Warning level: catches the target share of the given sessions, never closer to the clean-air
     * baseline than 3 noise widths. Alarm level: twice as far from the baseline as the warning level
     * (a placeholder until real MAJOR_LEAK data exists).
     */
    private static double[] levelsFrom(double[] values, CompensationModel.Channel channel, double targetDetection) {
        double baseline = channel.getBaseline();
        double noise = Math.max(channel.getNoiseSd(), 1);
        double warn = values.length == 0 ? baseline + 6 * noise
                : SignalMath.percentile(values, 1 - targetDetection);
        warn = Math.max(warn, baseline + 3 * noise);
        double alarm = baseline + 2 * (warn - baseline);
        warn = Math.min(warn, 4000);
        alarm = Math.min(Math.max(alarm, warn + noise), 4094);
        return new double[]{round(warn), round(alarm)};
    }

    /** MQ-2 values of leak (leaks = true) or non-leak sessions; raw when model is null. */
    static double[] values(List<Sample> samples, CompensationModel model, boolean leaks) {
        List<Double> out = new ArrayList<Double>();
        for (Sample s : samples) {
            Double value = model == null ? Double.valueOf(s.mq2) : model.compensateMq2(s.mq2, s.tempC, s.rhPct);
            if (Labels.isLeak(s.label) == leaks && value != null) {
                out.add(value);
            }
        }
        double[] array = new double[out.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = out.get(i);
        }
        return array;
    }

    private static List<Integer> groupsOf(List<Sample> samples) {
        List<Integer> groups = new ArrayList<Integer>();
        for (Sample s : samples) {
            groups.add(s.group);
        }
        return groups;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** One public session: recording id, readings and label. */
    public static final class Sample {

        final long id;
        final int group;
        final double mq2;
        final double mq135;
        final double tempC;
        final double rhPct;
        final String label;

        public Sample(long id, int group, double mq2, double mq135, double tempC, double rhPct, String label) {
            for (double v : new double[]{mq2, mq135, tempC, rhPct}) {
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new IllegalArgumentException("Session " + id + " has a missing reading");
                }
            }
            this.id = id;
            this.group = group;
            this.mq2 = mq2;
            this.mq135 = mq135;
            this.tempC = tempC;
            this.rhPct = rhPct;
            this.label = label;
        }
    }

    /** Detection and false alarm counts of one signal (raw or compensated), summed over the folds. */
    public static final class Outcome {

        private int leaks;
        private int detected;
        private int safe;
        private int safeAlarms;
        private int cooking;
        private int cookingAlarms;
        private final List<Double> leakScores = new ArrayList<Double>();
        private final List<Double> otherScores = new ArrayList<Double>();

        void add(List<Sample> train, List<Sample> test, CompensationModel model, double targetDetection) {
            double threshold = SignalMath.percentile(values(train, model, true), 1 - targetDetection);
            for (Sample s : test) {
                double v = model == null ? s.mq2 : model.compensateMq2(s.mq2, s.tempC, s.rhPct);
                boolean alarm = v >= threshold;
                if (Labels.isLeak(s.label)) {
                    leaks++;
                    detected += alarm ? 1 : 0;
                    leakScores.add(v);
                } else {
                    otherScores.add(v);
                    if (Labels.COOKING_FUMES.equals(s.label)) {
                        cooking++;
                        cookingAlarms += alarm ? 1 : 0;
                    } else {
                        safe++;
                        safeAlarms += alarm ? 1 : 0;
                    }
                }
            }
        }

        public double detectionRate() {
            return leaks == 0 ? Double.NaN : (double) detected / leaks;
        }

        public double falseAlarmRateSafe() {
            return safe == 0 ? Double.NaN : (double) safeAlarms / safe;
        }

        public double falseAlarmRateCooking() {
            return cooking == 0 ? Double.NaN : (double) cookingAlarms / cooking;
        }

        public double falseAlarmRateAll() {
            return safe + cooking == 0 ? Double.NaN : (double) (safeAlarms + cookingAlarms) / (safe + cooking);
        }

        public double auc() {
            return SignalMath.auc(toArray(leakScores), toArray(otherScores));
        }

        public int getSafe() {
            return safe;
        }

        public int getSafeAlarms() {
            return safeAlarms;
        }

        private static double[] toArray(List<Double> list) {
            double[] array = new double[list.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = list.get(i);
            }
            return array;
        }
    }

    /** Raw versus compensated. */
    public static final class Report {

        private final double targetDetection;
        private final Outcome raw = new Outcome();
        private final Outcome compensated = new Outcome();

        Report(double targetDetection) {
            this.targetDetection = targetDetection;
        }

        public Outcome getRaw() {
            return raw;
        }

        public Outcome getCompensated() {
            return compensated;
        }

        public double getTargetDetection() {
            return targetDetection;
        }
    }
}
