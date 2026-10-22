package service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import model.EvidenceItem;
import model.Labels;

/**
 * First tier of the alerting: fixed rules that give one of the six labels and the evidence for it.
 *
 * Order (the first rule that matches wins, the most dangerous first):
 * 1. FLAME_DETECTED  the flame sensor reports a flame
 * 2. SENSOR_FAULT    the MQ-2 reading cannot be used (missing, at the bottom of its range, or stuck)
 * 3. FLAME_DETECTED  the temperature reaches the heat limit
 * 4. MAJOR_LEAK      MQ-2 at or above the alarm level of the zone
 * 5. MINOR_LEAK      MQ-2 at or above the warning level of the zone
 * 6. SENSOR_FAULT    another reading (MQ-135, temperature, humidity) is missing or out of range
 * 7. COOKING_FUMES   MQ-135 at or above its warning level while MQ-2 stays below its own
 * 8. SAFE
 *
 * Gas levels use the compensated values. When temperature or humidity is unusable the raw value is
 * used instead, so a broken climate sensor never hides a leak.
 */
public class RuleEngine {

    public Outcome evaluate(Reading reading, Config config, List<Double> previousMq2) {
        List<EvidenceItem> evidence = new ArrayList<EvidenceItem>();
        List<String> faults = new ArrayList<String>();

        String mq2Problem = gasProblem(reading.mq2Raw, config.mq2Range);
        if (mq2Problem == null && isStuck(reading.mq2Raw, previousMq2, config)) {
            mq2Problem = "the same value " + fmt(reading.mq2Raw) + " in the last " + config.stuckCount + " sessions";
        }
        String mq135Problem = gasProblem(reading.mq135Raw, config.mq135Range);
        String tempProblem = climateProblem(reading.tempC, config.tempRange, " °C");
        String rhProblem = climateProblem(reading.rhPct, config.rhRange, " %");
        addFault(faults, "mq135_raw", mq135Problem);
        addFault(faults, "temp_c", tempProblem);
        addFault(faults, "rh_pct", rhProblem);
        boolean climateOk = tempProblem == null && rhProblem == null;

        if (Boolean.TRUE.equals(reading.flame)) {
            evidence.add(new EvidenceItem("flame_sensor", "flame reported by the device"));
            addReadings(evidence, reading);
            return new Outcome(Labels.FLAME_DETECTED, "The flame sensor reports a flame", evidence);
        }
        if (mq2Problem != null) {
            evidence.add(new EvidenceItem("mq2_raw", mq2Problem));
            addFaultEvidence(evidence, faults);
            addReadings(evidence, reading);
            return new Outcome(Labels.SENSOR_FAULT, "The MQ-2 gas reading cannot be trusted", evidence);
        }

        boolean useCompensated = climateOk && reading.mq2Compensated != null;
        double gas = useCompensated ? reading.mq2Compensated : reading.mq2Raw;
        String gasItem = useCompensated ? "mq2_compensated" : "mq2_raw";
        if (!useCompensated) {
            evidence.add(new EvidenceItem("compensation",
                    "not applied: temperature or humidity unusable, raw MQ-2 value used"));
        }
        if (reading.mq2Raw >= config.mq2Range.max) {
            evidence.add(new EvidenceItem("mq2_range", "reading at the top of the range, the real level may be higher"));
        }

        if (tempProblem == null && reading.tempC >= config.heatLimitC) {
            evidence.add(new EvidenceItem("temp_c", fmt(reading.tempC) + " °C at or above the heat limit "
                    + fmt(config.heatLimitC) + " °C"));
            evidence.add(new EvidenceItem(gasItem, fmt(gas)));
            addReadings(evidence, reading);
            return new Outcome(Labels.FLAME_DETECTED, "Temperature near the sensor suggests an open flame or fire",
                    evidence);
        }
        if (gas >= config.mq2.alarm) {
            evidence.add(new EvidenceItem(gasItem, fmt(gas) + " at or above the alarm level " + fmt(config.mq2.alarm)
                    + " (" + config.thresholdSource + ")"));
            addFaultEvidence(evidence, faults);
            addReadings(evidence, reading);
            return new Outcome(Labels.MAJOR_LEAK, "MQ-2 gas level is at or above the alarm level", evidence);
        }
        if (gas >= config.mq2.warn) {
            evidence.add(new EvidenceItem(gasItem, fmt(gas) + " at or above the warning level " + fmt(config.mq2.warn)
                    + " (" + config.thresholdSource + ")"));
            addFaultEvidence(evidence, faults);
            addReadings(evidence, reading);
            return new Outcome(Labels.MINOR_LEAK, "MQ-2 gas level is at or above the warning level", evidence);
        }
        if (!faults.isEmpty()) {
            addFaultEvidence(evidence, faults);
            addReadings(evidence, reading);
            return new Outcome(Labels.SENSOR_FAULT, "A sensor reading is missing or out of range", evidence);
        }

        boolean airCompensated = reading.mq135Compensated != null;
        double air = airCompensated ? reading.mq135Compensated : reading.mq135Raw;
        if (air >= config.mq135.warn) {
            String level = air >= config.mq135.alarm ? "alarm level " + fmt(config.mq135.alarm)
                    : "warning level " + fmt(config.mq135.warn);
            evidence.add(new EvidenceItem(airCompensated ? "mq135_compensated" : "mq135_raw",
                    fmt(air) + " at or above the " + level + " (" + config.thresholdSource + ")"));
            evidence.add(new EvidenceItem(gasItem, fmt(gas) + " below the MQ-2 warning level " + fmt(config.mq2.warn)));
            addReadings(evidence, reading);
            return new Outcome(Labels.COOKING_FUMES, "Air quality sensor rises while the gas sensor stays low",
                    evidence);
        }
        evidence.add(new EvidenceItem(gasItem, fmt(gas) + " below the warning level " + fmt(config.mq2.warn)));
        return new Outcome(Labels.SAFE, "All readings within their levels", evidence);
    }

    /** Bottom of the range means a disconnected sensor; the top is treated as a very high gas level. */
    private static String gasProblem(Double value, Range range) {
        if (value == null || value.isNaN()) {
            return "no reading";
        }
        if (value <= range.min) {
            return fmt(value) + " at the bottom of the range, the sensor may be disconnected";
        }
        return null;
    }

    private static String climateProblem(Double value, Range range, String unit) {
        if (value == null || value.isNaN()) {
            return "no reading";
        }
        if (value < range.min || value > range.max) {
            return fmt(value) + unit + " outside the valid range " + fmt(range.min) + " to " + fmt(range.max) + unit;
        }
        return null;
    }

    /** A real sensor always moves a little, so identical averages several sessions in a row mean a fault. */
    private static boolean isStuck(Double current, List<Double> previous, Config config) {
        if (current == null || previous == null || current >= config.mq2Range.max
                || previous.size() < config.stuckCount - 1) {
            return false;
        }
        for (int i = 0; i < config.stuckCount - 1; i++) {
            if (!current.equals(previous.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static void addFault(List<String> faults, String item, String problem) {
        if (problem != null) {
            faults.add(item + ": " + problem);
        }
    }

    private static void addFaultEvidence(List<EvidenceItem> evidence, List<String> faults) {
        for (String fault : faults) {
            int colon = fault.indexOf(':');
            evidence.add(new EvidenceItem("fault_" + fault.substring(0, colon), fault.substring(colon + 2)));
        }
    }

    private static void addReadings(List<EvidenceItem> evidence, Reading r) {
        evidence.add(new EvidenceItem("readings", "MQ-2 " + fmt(r.mq2Raw) + ", MQ-135 " + fmt(r.mq135Raw)
                + ", " + fmt(r.tempC) + " °C, " + fmt(r.rhPct) + " %RH"));
    }

    static String fmt(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Readings of one session as the rules see them; any value may be null. */
    public static final class Reading {

        private final Double mq2Raw;
        private final Double mq135Raw;
        private final Double tempC;
        private final Double rhPct;
        private final Double mq2Compensated;
        private final Double mq135Compensated;
        private final Boolean flame;

        public Reading(Double mq2Raw, Double mq135Raw, Double tempC, Double rhPct, Double mq2Compensated,
                Double mq135Compensated, Boolean flame) {
            this.mq2Raw = mq2Raw;
            this.mq135Raw = mq135Raw;
            this.tempC = tempC;
            this.rhPct = rhPct;
            this.mq2Compensated = mq2Compensated;
            this.mq135Compensated = mq135Compensated;
            this.flame = flame;
        }
    }

    /** Warning and alarm levels of one gas sensor, in sensor units (ADC counts on the ESP32). */
    public static final class Levels {

        private final double warn;
        private final double alarm;

        public Levels(double warn, double alarm) {
            if (!(warn < alarm)) {
                throw new IllegalArgumentException("The warning level must be below the alarm level");
            }
            this.warn = warn;
            this.alarm = alarm;
        }

        public double getWarn() {
            return warn;
        }

        public double getAlarm() {
            return alarm;
        }
    }

    /** Valid range of a sensor (SensorType.min_valid and max_valid). */
    public static final class Range {

        private final double min;
        private final double max;

        public Range(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    /** Levels and limits for one device; defaults match the seed data. */
    public static final class Config {

        private Levels mq2 = new Levels(1500, 2500);
        private Levels mq135 = new Levels(1200, 2200);
        private Range mq2Range = new Range(0, 4095);
        private Range mq135Range = new Range(0, 4095);
        private Range tempRange = new Range(-40, 80);
        private Range rhRange = new Range(0, 100);
        private double heatLimitC = 65;
        private int stuckCount = 5;
        private String thresholdSource = "default levels";

        public Config mq2Levels(Levels levels) {
            this.mq2 = levels;
            return this;
        }

        public Config mq135Levels(Levels levels) {
            this.mq135 = levels;
            return this;
        }

        public Config mq2Range(Range range) {
            this.mq2Range = range;
            return this;
        }

        public Config mq135Range(Range range) {
            this.mq135Range = range;
            return this;
        }

        public Config tempRange(Range range) {
            this.tempRange = range;
            return this;
        }

        public Config rhRange(Range range) {
            this.rhRange = range;
            return this;
        }

        public Config heatLimitC(double limit) {
            this.heatLimitC = limit;
            return this;
        }

        public Config stuckCount(int count) {
            this.stuckCount = Math.max(2, count);
            return this;
        }

        public Config thresholdSource(String source) {
            this.thresholdSource = source;
            return this;
        }

        public Levels getMq2() {
            return mq2;
        }

        public Levels getMq135() {
            return mq135;
        }

        public int getStuckCount() {
            return stuckCount;
        }

        public String getThresholdSource() {
            return thresholdSource;
        }
    }

    /** Label, one-sentence reason and the evidence lines for it. */
    public static final class Outcome {

        private final String label;
        private final String reason;
        private final List<EvidenceItem> evidence;

        Outcome(String label, String reason, List<EvidenceItem> evidence) {
            this.label = label;
            this.reason = reason;
            this.evidence = Collections.unmodifiableList(new ArrayList<EvidenceItem>(evidence));
        }

        public String getLabel() {
            return label;
        }

        public String getReason() {
            return reason;
        }

        public List<EvidenceItem> getEvidence() {
            return evidence;
        }
    }
}
