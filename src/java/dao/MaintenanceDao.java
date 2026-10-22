package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.MaintenanceTicket;
import model.PageResult;
import util.DbContext;

public class MaintenanceDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT ticket_id, alert_id, alert_label, device_id, device_code, "
            + "assigned_to, assignee_name, note, status, opened_at, closed_at FROM v_MaintenanceTicketDetail";
    private static final int EXPORT_LIMIT = 50000;

    public int open(int actorId, Integer alertId, int deviceId, Integer assignedTo, String note) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call MaintenanceTicket_Open(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            setNullableInt(cs, 2, alertId);
            cs.setInt(3, deviceId);
            setNullableInt(cs, 4, assignedTo);
            setNullableString(cs, 5, note);
            cs.registerOutParameter(6, Types.INTEGER);
            cs.execute();
            return cs.getInt(6);
        }
    }

    public boolean close(int actorId, int ticketId, String note) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call MaintenanceTicket_Close(?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, ticketId);
            setNullableString(cs, 3, note);
            cs.registerOutParameter(4, Types.BIT);
            cs.execute();
            return cs.getBoolean(4);
        }
    }

    public PageResult<MaintenanceTicket> search(String status, Integer assignedTo, String keyword, int page,
            int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(status, assignedTo, keyword, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_MaintenanceTicketDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            List<MaintenanceTicket> items = query(conn, SELECT_COLUMNS + where
                    + " ORDER BY ticket_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams);
            return new PageResult<MaintenanceTicket>(items, page, pageSize, total);
        }
    }

    public List<MaintenanceTicket> listForExport(String status, Integer assignedTo, String keyword) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        params.add(EXPORT_LIMIT);
        String where = buildWhere(status, assignedTo, keyword, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS.replaceFirst("SELECT ", "SELECT TOP (?) ") + where
                    + " ORDER BY ticket_id DESC", params);
        }
    }

    public List<MaintenanceTicket> forAlert(int alertId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + " WHERE alert_id = ? ORDER BY ticket_id",
                    Collections.<Object>singletonList(alertId));
        }
    }

    private static String buildWhere(String status, Integer assignedTo, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (status != null && !status.isEmpty()) {
            where.append(" AND status = ?");
            params.add(status);
        }
        if (assignedTo != null) {
            where.append(" AND assigned_to = ?");
            params.add(assignedTo);
        }
        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND (note LIKE ? OR device_code LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        return where.toString();
    }

    private static List<MaintenanceTicket> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<MaintenanceTicket> items = new ArrayList<MaintenanceTicket>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MaintenanceTicket t = new MaintenanceTicket();
                    t.setTicketId(rs.getInt("ticket_id"));
                    t.setAlertId(getNullableInt(rs, "alert_id"));
                    t.setAlertLabel(rs.getString("alert_label"));
                    t.setDeviceId(rs.getInt("device_id"));
                    t.setDeviceCode(rs.getString("device_code"));
                    t.setAssignedTo(getNullableInt(rs, "assigned_to"));
                    t.setAssigneeName(rs.getString("assignee_name"));
                    t.setNote(rs.getString("note"));
                    t.setStatus(rs.getString("status"));
                    t.setOpenedAt(rs.getTimestamp("opened_at"));
                    t.setClosedAt(rs.getTimestamp("closed_at"));
                    items.add(t);
                }
            }
        }
        return items;
    }
}
