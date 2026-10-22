package tool;

import dao.DeviceDao;
import dao.ZoneThresholdDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import service.CompensationModel;
import service.DeviceSetup;
import util.DbContext;

/**
 * Step after sql/04_public_data.sql (Run File in NetBeans):
 * 1. measures raw versus compensated false alarms on the public sessions (grouped folds),
 * 2. fits the compensation on every SAFE session and stores it as the profile of the replay device,
 * 3. rewrites PublicSession.compensated_score with the same formula the server uses, so the model
 *    trained next sees exactly the values the application computes,
 * 4. suggests warning and alarm levels and writes them to the zone of the replay device.
 * Run tool.ModelTrainer afterwards, because the compensated_score column has changed.
 */
public class CompensationFitter {

    private static final int DEVICE_ID = 1; // ESP32-KITCHEN-01, the device DataReplayer sends as
    private static final boolean APPLY_LEVELS = true;
    private static final double TARGET_DETECTION = 0.90; // the warning level catches 90% of leak sessions
    private static final int FOLDS = 5;
    private static final String QUERY = "SELECT public_id, induction_id, mq2_raw, mq135_raw, temp_c, rh_pct, label "
            + "FROM v_PublicSession_Features ORDER BY public_id";

    public static void main(String[] args) throws Exception {
        List<CompensationAnalysis.Sample> samples;
        try (Connection conn = DbContext.getConnection()) {
            samples = load(conn);
        }
        if (samples.isEmpty()) {
            System.out.println("PublicSession is empty: run sql/04_public_data.sql first.");
            return;
        }
        System.out.println("Sessions: " + samples.size());

        CompensationAnalysis.Report report = CompensationAnalysis.evaluate(samples, FOLDS, TARGET_DETECTION, 42);
        printReport(report);

        CompensationModel model = CompensationAnalysis.fitSafe(samples, "PublicSession SAFE windows");
        System.out.println();
        System.out.println("Final compensation (all SAFE sessions): " + model.describe());
        System.out.printf(Locale.ROOT, "MQ-2 clean-air spread: %.1f raw -> %.1f compensated (standard deviation)%n",
                rawSafeSd(samples), model.getMq2().getNoiseSd());

        try (Connection conn = DbContext.getConnection()) {
            int updated = rewriteCompensatedScore(conn, samples, model);
            System.out.println("PublicSession.compensated_score rewritten for " + updated + " sessions.");
        }
        DeviceDao devices = new DeviceDao();
        int profileId = devices.saveProfile(null, DEVICE_ID, model.toJson());
        System.out.println("Saved as OperatingProfile " + profileId + " of device " + DEVICE_ID + ".");

        double[][] levels = CompensationAnalysis.suggestLevels(samples, model, TARGET_DETECTION);
        System.out.printf(Locale.ROOT, "Suggested levels: MQ-2 warning %.1f, alarm %.1f; MQ-135 warning %.1f, alarm %.1f%n",
                levels[0][0], levels[0][1], levels[1][0], levels[1][1]);
        if (APPLY_LEVELS) {
            applyLevels(devices, DeviceSetup.MQ2, levels[0]);
            applyLevels(devices, DeviceSetup.MQ135, levels[1]);
        }
        System.out.println("Next: run tool.ModelTrainer, then redeploy the web application.");
    }

    static void printReport(CompensationAnalysis.Report report) {
        System.out.println();
        System.out.printf(Locale.ROOT, "Leak threshold set to catch %.0f%% of leak sessions, %d recording folds:%n",
                report.getTargetDetection() * 100, FOLDS);
        System.out.printf(Locale.ROOT, "%-12s %10s %16s %19s %14s %6s%n", "signal", "detection",
                "false alarm SAFE", "false alarm COOKING", "false alarm all", "AUC");
        printRow("raw MQ-2", report.getRaw());
        printRow("compensated", report.getCompensated());
        CompensationAnalysis.Outcome raw = report.getRaw();
        CompensationAnalysis.Outcome comp = report.getCompensated();
        if (raw.getSafeAlarms() > 0) {
            int change = comp.getSafeAlarms() - raw.getSafeAlarms();
            System.out.printf(Locale.ROOT, "False alarms on SAFE sessions: %d -> %d (%.0f%% %s)%n",
                    raw.getSafeAlarms(), comp.getSafeAlarms(), 100.0 * Math.abs(change) / raw.getSafeAlarms(),
                    change <= 0 ? "fewer" : "more");
        }
    }

    private static void printRow(String name, CompensationAnalysis.Outcome o) {
        System.out.printf(Locale.ROOT, "%-12s %10.3f %16.3f %19.3f %14.3f %6.3f%n", name, o.detectionRate(),
                o.falseAlarmRateSafe(), o.falseAlarmRateCooking(), o.falseAlarmRateAll(), o.auc());
    }

    private static List<CompensationAnalysis.Sample> load(Connection conn) throws SQLException {
        List<CompensationAnalysis.Sample> samples = new ArrayList<CompensationAnalysis.Sample>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(QUERY)) {
            while (rs.next()) {
                double mq2 = rs.getDouble("mq2_raw");
                boolean complete = !rs.wasNull();
                double mq135 = rs.getDouble("mq135_raw");
                complete &= !rs.wasNull();
                double temp = rs.getDouble("temp_c");
                complete &= !rs.wasNull();
                double rh = rs.getDouble("rh_pct");
                complete &= !rs.wasNull();
                if (complete) {
                    samples.add(new CompensationAnalysis.Sample(rs.getLong("public_id"), rs.getInt("induction_id"),
                            mq2, mq135, temp, rh, rs.getString("label")));
                }
            }
        }
        return samples;
    }

    private static double rawSafeSd(List<CompensationAnalysis.Sample> samples) {
        List<Double> values = new ArrayList<Double>();
        for (CompensationAnalysis.Sample s : samples) {
            if ("SAFE".equals(s.label)) {
                values.add(s.mq2);
            }
        }
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return service.SignalMath.sd(array);
    }

    private static int rewriteCompensatedScore(Connection conn, List<CompensationAnalysis.Sample> samples,
            CompensationModel model) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE PublicSession SET compensated_score = ? WHERE public_id = ?")) {
            for (CompensationAnalysis.Sample s : samples) {
                ps.setDouble(1, model.compensateMq2(s.mq2, s.tempC, s.rhPct));
                ps.setLong(2, s.id);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
        return samples.size();
    }

    private static void applyLevels(DeviceDao devices, String sensorName, double[] levels) throws SQLException {
        Integer zoneId = devices.zoneOfSensor(DEVICE_ID, sensorName);
        Integer typeId = devices.sensorTypeId(sensorName);
        if (zoneId == null || typeId == null) {
            System.out.println("No " + sensorName + " sensor of device " + DEVICE_ID + " is placed in a zone; "
                    + "levels not written.");
            return;
        }
        new ZoneThresholdDao().save(null, zoneId, typeId, levels[0], levels[1]);
        System.out.println(sensorName + " levels written to zone " + zoneId + ".");
    }
}
