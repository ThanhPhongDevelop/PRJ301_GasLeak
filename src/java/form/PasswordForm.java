package form;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import util.SecurityUtils;

/** A signed-in user changes their own password. Whether the current password is right is checked by the controller. */
public final class PasswordForm {

    public static final String[] FIELDS = {"currentPassword", "newPassword", "confirmPassword"};

    private PasswordForm() {
    }

    /** Raw values: passwords are not trimmed, a space can be part of one. */
    public static Map<String, String> read(HttpServletRequest req) {
        Map<String, String> form = Fields.empty(FIELDS);
        for (String field : FIELDS) {
            String value = req.getParameter(field);
            form.put(field, value == null ? "" : value);
        }
        return form;
    }

    /** Returns the new password, or null when errors were found. */
    public static String validate(Map<String, String> form, Map<String, String> errors) {
        String current = form.get("currentPassword");
        String next = form.get("newPassword");
        if (current == null || current.isEmpty()) {
            errors.put("currentPassword", "Enter your current password.");
        }
        if (!SecurityUtils.isStrongPassword(next)) {
            errors.put("newPassword", "Use at least 8 characters with a letter and a digit.");
        } else if (next.equals(current)) {
            errors.put("newPassword", "Choose a password different from the current one.");
        } else if (!next.equals(form.get("confirmPassword"))) {
            errors.put("confirmPassword", "The two new passwords do not match.");
        }
        return errors.isEmpty() ? next : null;
    }
}
