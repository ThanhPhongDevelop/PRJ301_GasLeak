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
import model.EmergencyContact;
import model.PageResult;
import util.DbContext;

/** Emergency contacts of a home, called in priority order. */
public class EmergencyContactDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT contact_id, home_id, home_name, full_name, phone, email, "
            + "relation, priority, notify_min_label, created_at FROM v_EmergencyContactDetail";

    public PageResult<EmergencyContact> search(String keyword, Integer homeId, int page, int pageSize)
            throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, homeId, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_EmergencyContactDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<EmergencyContact>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY home_id, priority, contact_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams),
                    page, pageSize, total);
        }
    }

    public List<EmergencyContact> listForExport(String keyword, Integer homeId) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, homeId, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY home_id, priority, contact_id", params);
        }
    }

    public EmergencyContact findById(int contactId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<EmergencyContact> found = query(conn, SELECT_COLUMNS + " WHERE contact_id = ?",
                    Collections.<Object>singletonList(contactId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Creates the contact when its id is 0, otherwise changes it. Returns the id. */
    public int save(int actorId, EmergencyContact c) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call EmergencyContact_Save(?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            bindSaveId(cs, 2, c.getContactId());
            cs.setInt(3, c.getHomeId());
            cs.setString(4, c.getFullName());
            cs.setString(5, c.getPhone());
            setNullableString(cs, 6, c.getEmail());
            setNullableString(cs, 7, c.getRelation());
            cs.setInt(8, c.getPriority());
            cs.setString(9, c.getNotifyMinLabel());
            cs.execute();
            return cs.getInt(2);
        }
    }

    public boolean delete(int actorId, int contactId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call EmergencyContact_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, contactId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(String keyword, Integer homeId, List<Object> params) {
        String k = keyword == null ? "" : keyword;
        StringBuilder where = new StringBuilder(" WHERE (full_name LIKE ? OR phone LIKE ?)");
        params.add("%" + k + "%");
        params.add("%" + k + "%");
        if (homeId != null) {
            where.append(" AND home_id = ?");
            params.add(homeId);
        }
        return where.toString();
    }

    private static List<EmergencyContact> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<EmergencyContact> items = new ArrayList<EmergencyContact>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EmergencyContact c = new EmergencyContact();
                    c.setContactId(rs.getInt("contact_id"));
                    c.setHomeId(rs.getInt("home_id"));
                    c.setHomeName(rs.getString("home_name"));
                    c.setFullName(rs.getString("full_name"));
                    c.setPhone(rs.getString("phone"));
                    c.setEmail(rs.getString("email"));
                    c.setRelation(rs.getString("relation"));
                    c.setPriority(rs.getInt("priority"));
                    c.setNotifyMinLabel(rs.getString("notify_min_label"));
                    c.setCreatedAt(rs.getTimestamp("created_at"));
                    items.add(c);
                }
            }
        }
        return items;
    }
}
