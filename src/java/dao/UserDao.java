package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.PageResult;
import model.User;
import util.DbContext;
import util.SecurityUtils;

public class UserDao extends BaseDao {

    // BCrypt hash of a random string: checking against it when the username does not exist keeps
    // the response time the same, so nobody can probe which usernames exist
    private static final String DUMMY_HASH = "$2a$10$EDoxZBEAntwC4Dzls1haZeMAjs3hnT.88MgGJC3qYyHtzG.Qlme.2";

    /** Returns the user when the password matches (whatever the status), otherwise null. */
    public User checkLogin(String username, String password) throws SQLException {
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.full_name, u.status, r.role_name "
                + "FROM Users u JOIN UserRole ur ON u.user_id = ur.user_id "
                + "JOIN Roles r ON r.role_id = ur.role_id "
                + "WHERE u.username = ?";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    SecurityUtils.checkPassword(password, DUMMY_HASH);
                    return null;
                }
                if (!SecurityUtils.checkPassword(password, rs.getString("password_hash"))) {
                    return null;
                }
                User user = new User(rs.getInt("user_id"), rs.getString("username"),
                        rs.getString("full_name"), rs.getString("role_name"));
                user.setStatus(rs.getString("status"));
                return user;
            }
        }
    }

    /** Creates an account with the Guest viewer role. Returns the new id, or -1 when the username is taken. */
    public int register(String username, String password, String fullName) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call User_Register(?, ?, ?, ?)}")) {
            cs.setString(1, username);
            cs.setString(2, SecurityUtils.hashPassword(password));
            cs.setString(3, fullName);
            cs.registerOutParameter(4, Types.INTEGER);
            cs.execute();
            return cs.getInt(4);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2627 || e.getErrorCode() == 2601) {
                return -1; // two people registered the same name at the same moment
            }
            throw e;
        }
    }

    /** An administrator creates an account with its role. Returns the new id, or -1 when the username is taken. */
    public int create(int actorId, String username, String password, String fullName, String roleName)
            throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call User_Create(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setString(2, username);
            cs.setString(3, SecurityUtils.hashPassword(password));
            cs.setString(4, fullName);
            cs.setString(5, roleName);
            cs.registerOutParameter(6, Types.INTEGER);
            cs.execute();
            return cs.getInt(6);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2627 || e.getErrorCode() == 2601) {
                return -1;
            }
            throw e;
        }
    }

    /** Stores a new password. Logged as CHANGE_PASSWORD (own) or RESET_PASSWORD (administrator). */
    public void setPassword(int actorId, int userId, String newPassword) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call User_SetPassword(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, userId);
            cs.setString(3, SecurityUtils.hashPassword(newPassword));
            cs.execute();
        }
    }

    /** True when the password is the current password of this user (checked before a change). */
    public boolean verifyPassword(int userId, String password) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM Users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && SecurityUtils.checkPassword(password, rs.getString(1));
            }
        }
    }

    /**
     * Failed logins for this username in the last minutes, counted from the audit log and only since
     * the user's last successful login. LoginController refuses further tries above a limit.
     */
    public int recentFailedLogins(String username, int minutes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM AuditLog a WHERE a.action = 'LOGIN_FAILED' "
                + "AND a.ts >= DATEADD(MINUTE, -?, SYSUTCDATETIME()) "
                + "AND (CASE WHEN ISJSON(a.diff_json) = 1 THEN JSON_VALUE(a.diff_json, '$.username') END) = ? "
                + "AND a.ts > ISNULL((SELECT MAX(l.ts) FROM AuditLog l JOIN Users u ON u.user_id = l.object_id "
                + "WHERE l.action = 'LOGIN' AND l.object_type = 'USER' AND u.username = ?), '19000101')";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, minutes);
            ps.setString(2, username);
            ps.setString(3, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Account with its role, for the My account page. */
    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, username, full_name, status, created_at, role_name FROM v_UserAccount "
                + "WHERE user_id = ?";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                User user = new User(rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"),
                        rs.getString("role_name"));
                user.setStatus(rs.getString("status"));
                user.setCreatedAt(rs.getTimestamp("created_at"));
                return user;
            }
        }
    }

    /** Checked on every request, so locking an account ends sessions that are already open. */
    public boolean isActive(int userId) throws SQLException {
        String sql = "SELECT 1 FROM Users WHERE user_id = ? AND status = 'ACTIVE'";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public PageResult<User> search(String keyword, String roleName, int page, int pageSize) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE (username LIKE ? OR full_name LIKE ?)");
        List<Object> params = new ArrayList<Object>();
        params.add("%" + keyword + "%");
        params.add("%" + keyword + "%");
        if (roleName != null && !roleName.isEmpty()) {
            where.append(" AND role_name = ?");
            params.add(roleName);
        }
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_UserAccount" + where, params);
            String sql = "SELECT user_id, username, full_name, status, created_at, role_name FROM v_UserAccount"
                    + where + " ORDER BY user_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<User> items = new ArrayList<User>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = bind(ps, params);
                ps.setInt(index++, (page - 1) * pageSize);
                ps.setInt(index, pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        User user = new User(rs.getInt("user_id"), rs.getString("username"),
                                rs.getString("full_name"), rs.getString("role_name"));
                        user.setStatus(rs.getString("status"));
                        user.setCreatedAt(rs.getTimestamp("created_at"));
                        items.add(user);
                    }
                }
            }
            return new PageResult<User>(items, page, pageSize, total);
        }
    }

    public List<String> listRoleNames() throws SQLException {
        List<String> names = new ArrayList<String>();
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT role_name FROM Roles ORDER BY role_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** Throws SQLException with a readable message (error >= 50000) when a rule blocks the change. */
    public void changeRole(int actorId, int userId, String roleName) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call User_ChangeRole(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, userId);
            cs.setString(3, roleName);
            cs.execute();
        }
    }

    public void setStatus(int actorId, int userId, String status) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call User_SetStatus(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, userId);
            cs.setString(3, status);
            cs.execute();
        }
    }
}
