package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.AuditEntry;
import model.PageResult;
import util.DbContext;

public class AuditDao extends BaseDao {

    /** Usual call from a controller: append(user.getUserId(), "UPDATE", "KITCHENZONE", id, diffJson). */
    public void append(int actorUserId, String action, String objectType, long objectId, String diffJson)
            throws SQLException {
        append(Integer.valueOf(actorUserId), action, objectType, Long.valueOf(objectId), diffJson);
    }

    /** Actor and object may be null, for example a failed login or a system event. */
    public void append(Integer actorUserId, String action, String objectType, Long objectId, String diffJson)
            throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Audit_Append(?, ?, ?, ?, ?)}")) {
            if (actorUserId == null) {
                cs.setNull(1, Types.INTEGER);
            } else {
                cs.setInt(1, actorUserId);
            }
            cs.setString(2, action);
            cs.setString(3, objectType);
            if (objectId == null) {
                cs.setNull(4, Types.BIGINT);
            } else {
                cs.setLong(4, objectId);
            }
            if (diffJson == null) {
                cs.setNull(5, Types.NVARCHAR);
            } else {
                cs.setString(5, diffJson);
            }
            cs.execute();
        }
    }

    public PageResult<AuditEntry> search(String action, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = "";
        if (action != null && !action.isEmpty()) {
            where = " WHERE a.action = ?";
            params.add(action);
        }
        String sql = "SELECT a.audit_id, CONVERT(NVARCHAR(33), a.ts, 126) AS ts_text, a.actor_user_id, u.username, "
                + "a.action, a.object_type, a.object_id, a.diff_json, a.row_hash "
                + "FROM AuditLog a LEFT JOIN Users u ON u.user_id = a.actor_user_id" + where
                + " ORDER BY a.audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM AuditLog a" + where, params);
            List<AuditEntry> items = new ArrayList<AuditEntry>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = bind(ps, params);
                ps.setInt(index++, (page - 1) * pageSize);
                ps.setInt(index, pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AuditEntry entry = new AuditEntry();
                        entry.setAuditId(rs.getLong("audit_id"));
                        entry.setTs(rs.getString("ts_text"));
                        entry.setActorUserId(getNullableInt(rs, "actor_user_id"));
                        entry.setActorUsername(rs.getString("username"));
                        entry.setAction(rs.getString("action"));
                        entry.setObjectType(rs.getString("object_type"));
                        entry.setObjectId(getNullableLong(rs, "object_id"));
                        entry.setDiffJson(rs.getString("diff_json"));
                        entry.setRowHash(rs.getString("row_hash"));
                        items.add(entry);
                    }
                }
            }
            return new PageResult<AuditEntry>(items, page, pageSize, total);
        }
    }

    public List<String> listActions() throws SQLException {
        List<String> actions = new ArrayList<String>();
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT action FROM AuditLog ORDER BY action");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
        }
        return actions;
    }
}
