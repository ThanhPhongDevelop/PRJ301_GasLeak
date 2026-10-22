package form;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import util.SecurityUtils;

/**
 * An administrator creates an account. The password may be left empty: a temporary password is then
 * generated and shown once to the administrator, who passes it on.
 */
public final class UserForm {

    public static final String[] FIELDS = {"username", "fullName", "roleName", "password"};
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,50}$");

    private UserForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    /** New accounts default to the least powerful role. */
    public static Map<String, String> empty() {
        Map<String, String> form = Fields.empty(FIELDS);
        form.put("roleName", "END_USER");
        return form;
    }

    public static Account validate(Map<String, String> form, Map<String, String> errors, Collection<String> roles) {
        String username = form.get("username") == null ? "" : form.get("username");
        if (!USERNAME.matcher(username).matches()) {
            errors.put("username", "Use 3 to 50 letters, digits, dots or underscores.");
        }
        String fullName = Fields.required(form, errors, "fullName", "full name", 80);
        String role = form.get("roleName");
        if (role == null || !roles.contains(role)) {
            errors.put("roleName", "Choose a role.");
        }
        String password = form.get("password") == null ? "" : form.get("password");
        boolean generated = password.isEmpty();
        if (generated) {
            password = SecurityUtils.newTemporaryPassword();
        } else if (!SecurityUtils.isStrongPassword(password)) {
            errors.put("password", "Use at least 8 characters with a letter and a digit, or leave it empty.");
        }
        form.put("password", ""); // never sent back to the browser
        return new Account(username, fullName, role, password, generated);
    }

    /** Checked values of a new account. */
    public static final class Account {

        private final String username;
        private final String fullName;
        private final String roleName;
        private final String password;
        private final boolean generated;

        Account(String username, String fullName, String roleName, String password, boolean generated) {
            this.username = username;
            this.fullName = fullName;
            this.roleName = roleName;
            this.password = password;
            this.generated = generated;
        }

        public String getUsername() {
            return username;
        }

        public String getFullName() {
            return fullName;
        }

        public String getRoleName() {
            return roleName;
        }

        public String getPassword() {
            return password;
        }

        /** True when the password was generated and must be shown once to the administrator. */
        public boolean isGenerated() {
            return generated;
        }
    }
}
