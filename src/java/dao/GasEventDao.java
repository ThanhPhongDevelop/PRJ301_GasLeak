package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import model.GasEvent;
import model.PageResult;
import util.DbContext;

/** Sessions sent by devices. Rows are never changed; labels live in GasEventLabel. */
public class GasEventDao extends BaseDao {

    /** Filter value for sessions nobody has labelled yet. */
    public static final String UNLABELLED = "NONE";

    private static final String SELECT_COLUMNS = "SELECT session_id, device_id, device_code, device_seq, measured_at, "
            + "ingested_at, mq2_raw, mq135_raw, temp_c, rh_pct, compensated_score, mq135_compensated, flame_detected, "
            + "rule_label, ml_label, ml_confidence, human_label, labeled_at, labeled_by_name, alert_count "
            + "FROM v_GasEventDetail";
    private static final int EXPORT_LIMIT = 50000;

    /** Stores one session with its rule and model results; returns the new id, or -1 for a duplicate. */
    public long insert(int deviceId, int deviceSeq, Timestamp measuredAt, Double mq2Raw, Double mq135Raw,
            Double tempC, Double rhPct, Double compensatedScore, Double mq135Compensated, Boolean flameDetected,
            String ruleLabel, String mlLabel, Double mlConfidence) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall(
                        "{call GasEvent_Insert(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, deviceId);
            cs.setInt(2, deviceSeq);
            cs.setTimestamp(3, measuredAt);
            setNullableDouble(cs, 4, mq2Raw);
            setNullableDouble(cs, 5, mq135Raw);
            setNullableDouble(cs, 6, tempC);
            setNullableDouble(cs, 7, rhPct);
            setNullableDouble(cs, 8, compensatedScore);
            setNullableDouble(cs, 9, mq135Compensated);
            setNullableBoolean(cs, 10, flameDetected);
            setNullableString(cs, 11, ruleLabel);
            setNullableString(cs, 12, mlLabel);
            setNullableDouble(cs, 13, mlConfidence);
            cs.registerOutParameter(14, Types.BIGINT);
            cs.execute();
            return cs.getLong(14);
        }
    }

    /** Last MQ-2 readings of a device, newest first (for the stuck sensor rule). */
    public LinkedList<Double> recentMq2(int deviceId, int count) throws SQLException {
        LinkedList<Double> values = new LinkedList<Double>();
        String sql = "SELECT TOP (?) mq2_raw FROM GasEvent WHERE device_id = ? ORDER BY device_seq DESC";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, count));
            ps.setInt(2, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(getNullableDouble(rs, "mq2_raw"));
                }
            }
        }
        return values;
    }

    /** Highest sequence number stored for a device, 0 when none (DataReplayer continues from it). */
    public int lastSeq(int deviceId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT ISNULL(MAX(device_seq), 0) FROM GasEvent WHERE device_id = ?")) {
            ps.setInt(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public PageResult<GasEvent> search(Integer deviceId, String ruleLabel, String humanLabel, int page, int pageSize)
            throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(deviceId, ruleLabel, humanLabel, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_GasEventDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            List<GasEvent> items = query(conn, SELECT_COLUMNS + where
                    + " ORDER BY session_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams);
            return new PageResult<GasEvent>(items, page, pageSize, total);
        }
    }

    public List<GasEvent> listForExport(Integer deviceId, String ruleLabel, String humanLabel) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        params.add(EXPORT_LIMIT);
        String where = buildWhere(deviceId, ruleLabel, humanLabel, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS.replaceFirst("SELECT ", "SELECT TOP (?) ") + where
                    + " ORDER BY session_id DESC", params);
        }
    }

    public GasEvent findById(long sessionId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<GasEvent> found = query(conn, SELECT_COLUMNS + " WHERE session_id = ?",
                    Collections.<Object>singletonList(sessionId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Newest sessions of a device, returned oldest first for charts. */
    public List<GasEvent> recentForDevice(int deviceId, int count) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        params.add(count);
        params.add(deviceId);
        try (Connection conn = DbContext.getConnection()) {
            List<GasEvent> items = query(conn, SELECT_COLUMNS.replaceFirst("SELECT ", "SELECT TOP (?) ")
                    + " WHERE device_id = ? ORDER BY device_seq DESC", params);
            Collections.reverse(items);
            return items;
        }
    }

    /** Sessions of a device that got the same rule label since an alert was raised (the rest of the episode). */
    public List<GasEvent> episode(int deviceId, String ruleLabel, Timestamp from, int limit) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        params.add(limit);
        params.add(deviceId);
        params.add(ruleLabel);
        params.add(from);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS.replaceFirst("SELECT ", "SELECT TOP (?) ")
                    + " WHERE device_id = ? AND rule_label = ? AND ingested_at >= ? ORDER BY session_id", params);
        }
    }

    public void setLabel(int actorId, long sessionId, String label) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call GasEventLabel_Set(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setLong(2, sessionId);
            cs.setString(3, label);
            cs.execute();
        }
    }

    public boolean removeLabel(int actorId, long sessionId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call GasEventLabel_Remove(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setLong(2, sessionId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(Integer deviceId, String ruleLabel, String humanLabel, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (deviceId != null) {
            where.append(" AND device_id = ?");
            params.add(deviceId);
        }
        if (ruleLabel != null && !ruleLabel.isEmpty()) {
            where.append(" AND rule_label = ?");
            params.add(ruleLabel);
        }
        if (UNLABELLED.equals(humanLabel)) {
            where.append(" AND human_label IS NULL");
        } else if (humanLabel != null && !humanLabel.isEmpty()) {
            where.append(" AND human_label = ?");
            params.add(humanLabel);
        }
        return where.toString();
    }

    private static List<GasEvent> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<GasEvent> items = new ArrayList<GasEvent>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GasEvent e = new GasEvent();
                    e.setSessionId(rs.getLong("session_id"));
                    e.setDeviceId(rs.getInt("device_id"));
                    e.setDeviceCode(rs.getString("device_code"));
                    e.setDeviceSeq(rs.getInt("device_seq"));
                    e.setMeasuredAt(rs.getTimestamp("measured_at"));
                    e.setIngestedAt(rs.getTimestamp("ingested_at"));
                    e.setMq2Raw(getNullableDouble(rs, "mq2_raw"));
                    e.setMq135Raw(getNullableDouble(rs, "mq135_raw"));
                    e.setTempC(getNullableDouble(rs, "temp_c"));
                    e.setRhPct(getNullableDouble(rs, "rh_pct"));
                    e.setCompensatedScore(getNullableDouble(rs, "compensated_score"));
                    e.setMq135Compensated(getNullableDouble(rs, "mq135_compensated"));
                    e.setFlameDetected(getNullableBoolean(rs, "flame_detected"));
                    e.setRuleLabel(rs.getString("rule_label"));
                    e.setMlLabel(rs.getString("ml_label"));
                    e.setMlConfidence(getNullableDouble(rs, "ml_confidence"));
                    e.setHumanLabel(rs.getString("human_label"));
                    e.setLabeledAt(rs.getTimestamp("labeled_at"));
                    e.setLabeledByName(rs.getString("labeled_by_name"));
                    e.setAlertCount(rs.getInt("alert_count"));
                    items.add(e);
                }
            }
        }
        return items;
    }
}
