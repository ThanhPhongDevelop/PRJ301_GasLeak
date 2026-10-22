package model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Signed-in user, kept in the session under the key LOGIN_USER. */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    // role codes stored in the database -> names shown in the interface
    private static final Map<String, String> ROLE_DISPLAY = new HashMap<String, String>();

    static {
        ROLE_DISPLAY.put("ADMIN", "Administrator");
        ROLE_DISPLAY.put("OPERATOR", "Homeowner");
        ROLE_DISPLAY.put("TECHNICIAN", "Technician");
        ROLE_DISPLAY.put("AUDITOR", "Auditor");
        ROLE_DISPLAY.put("END_USER", "Guest viewer");
    }

    private int userId;
    private String username;
    private String fullName;
    private String roleName;
    private String status;
    private Timestamp createdAt;
    private Set<String> permissions = new HashSet<String>();

    public User() {
    }

    public User(int userId, String username, String fullName, String roleName) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.roleName = roleName;
    }

    /** Interface name of a role code, for example OPERATOR becomes Homeowner. */
    public static String displayNameOf(String roleName) {
        String name = ROLE_DISPLAY.get(roleName);
        return name == null ? roleName : name;
    }

    /** For JSP pages: ${sessionScope.LOGIN_USER.can('KITCHENZONE', 'WRITE')} */
    public boolean can(String resource, String action) {
        return permissions.contains(resource + ":" + action);
    }

    public String getRoleDisplayName() {
        return displayNameOf(roleName);
    }

    /** Sorted for display, for example ALERT:READ. */
    public List<String> getPermissionList() {
        List<String> list = new ArrayList<String>(permissions);
        Collections.sort(list);
        return list;
    }

    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions == null ? new HashSet<String>() : new HashSet<String>(permissions);
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
