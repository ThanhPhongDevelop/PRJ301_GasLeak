package dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.Alert;
import model.EvidenceItem;
import model.PageResult;
import util.DbContext;

public class AlertDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT alert_id, device_id, device_code, session_id, source, label, "
            + "risk_score, status, created_at, evidence_count, ticket_count FROM v_AlertOverview";
    private static final int EXPORT_LIMIT = 50000;

    /**
     * Raises an alert with its evidence through Alert_Raise. Returns the alert id, positive when a new
     * alert was created and negative when an unresolved alert of the same device and label already
     * covered it (no new alert, nothing to notify).
     */
    public int raise(int deviceId, long sessionId, String source, String label, Double riskScore,
            List<EvidenceItem> evidence, int dedupeMinutes) throws SQLException {
        JsonArray items = new JsonArray();
        for (EvidenceItem e : evidence) {
            JsonObject item = new JsonObject();
            item.addProperty("item", e.getItem());
            item.addProperty("value", e.getValue());
            items.add(item);
        }
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Alert_Raise(?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, deviceId);
            cs.setLong(2, sessionId);
            cs.setString(3, source);
            cs.setString(4, label);
            setNullableDouble(cs, 5, riskScore);
            cs.setString(6, items.toString());
            cs.setInt(7, dedupeMinutes);
            cs.registerOutParameter(8, Types.INTEGER);
            cs.registerOutParameter(9, Types.BIT);
            cs.execute();
            int alertId = cs.getInt(8);
            return cs.getBoolean(9) ? alertId : -alertId;
        }
    }

    public PageResult<Alert> search(String status, String label, String source, int page, int pageSize)
            throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(status, label, source, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_AlertOverview" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            List<Alert> items = query(conn, SELECT_COLUMNS + where
                    + " ORDER BY alert_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams);
            return new PageResult<Alert>(items, page, pageSize, total);
        }
    }

    public List<Alert> listForExport(String status, String label, String source) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        params.add(EXPORT_LIMIT);
        String where = buildWhere(status, label, source, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS.replaceFirst("SELECT ", "SELECT TOP (?) ") + where
                    + " ORDER BY alert_id DESC", params);
        }
    }

    public Alert findById(int alertId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<Alert> found = query(conn, SELECT_COLUMNS + " WHERE alert_id = ?",
                    Collections.<Object>singletonList(alertId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    public List<Alert> forSession(long sessionId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + " WHERE session_id = ? ORDER BY alert_id",
                    Collections.<Object>singletonList(sessionId));
        }
    }

    public List<EvidenceItem> evidence(int alertId) throws SQLException {
        List<EvidenceItem> items = new ArrayList<EvidenceItem>();
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT item, value_text FROM AlertEvidence WHERE alert_id = ? ORDER BY evidence_id")) {
            ps.setInt(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new EvidenceItem(rs.getString("item"), rs.getString("value_text")));
                }
            }
        }
        return items;
    }

    public boolean acknowledge(int actorId, int alertId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Alert_Acknowledge(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, alertId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    /** "Confirm the incident was handled". */
    public boolean resolve(int actorId, int alertId, String note) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Alert_Resolve(?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, alertId);
            setNullableString(cs, 3, note);
            cs.registerOutParameter(4, Types.BIT);
            cs.execute();
            return cs.getBoolean(4);
        }
    }

    private static String buildWhere(String status, String label, String source, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if ("UNRESOLVED".equals(status)) {
            where.append(" AND status <> 'RESOLVED'");
        } else if (status != null && !status.isEmpty()) {
            where.append(" AND status = ?");
            params.add(status);
        }
        if (label != null && !label.isEmpty()) {
            where.append(" AND label = ?");
            params.add(label);
        }
        if (source != null && !source.isEmpty()) {
            where.append(" AND source = ?");
            params.add(source);
        }
        return where.toString();
    }

    private static List<Alert> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<Alert> items = new ArrayList<Alert>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Alert a = new Alert();
                    a.setAlertId(rs.getInt("alert_id"));
                    a.setDeviceId(rs.getInt("device_id"));
                    a.setDeviceCode(rs.getString("device_code"));
                    a.setSessionId(getNullableLong(rs, "session_id"));
                    a.setSource(rs.getString("source"));
                    a.setLabel(rs.getString("label"));
                    a.setRiskScore(getNullableDouble(rs, "risk_score"));
                    a.setStatus(rs.getString("status"));
                    a.setCreatedAt(rs.getTimestamp("created_at"));
                    a.setEvidenceCount(rs.getInt("evidence_count"));
                    a.setTicketCount(rs.getInt("ticket_count"));
                    items.add(a);
                }
            }
        }
        return items;
    }
}
