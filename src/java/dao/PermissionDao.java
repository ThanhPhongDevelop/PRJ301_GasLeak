package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import util.DbContext;

public class PermissionDao {

    private static final String FROM_USER_PERMISSIONS = "FROM UserRole ur "
            + "JOIN RolePerm rp ON rp.role_id = ur.role_id "
            + "JOIN Permissions p ON p.perm_id = rp.perm_id ";

    /** Checked by SecurityFilter on every request, so a role change applies immediately. */
    public boolean hasPermission(int userId, String resource, String action) throws SQLException {
        String sql = "SELECT 1 " + FROM_USER_PERMISSIONS + "WHERE ur.user_id = ? AND p.resource = ? AND p.action = ?";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, resource);
            ps.setString(3, action);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** All permissions as "RESOURCE:ACTION", loaded at login so JSP pages can hide buttons. */
    public Set<String> loadPermissions(int userId) throws SQLException {
        String sql = "SELECT p.resource, p.action " + FROM_USER_PERMISSIONS + "WHERE ur.user_id = ?";
        Set<String> permissions = new HashSet<String>();
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    permissions.add(rs.getString(1) + ":" + rs.getString(2));
                }
            }
        }
        return permissions;
    }
}
