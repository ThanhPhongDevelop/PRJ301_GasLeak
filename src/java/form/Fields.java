package form;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import util.FormUtils;

/** Shared helpers of the form classes. */
final class Fields {

    static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_.-]{2,30}$");

    private Fields() {
    }

    /** Trimmed values of the named request parameters ("" when missing). */
    static Map<String, String> read(HttpServletRequest req, String[] names) {
        Map<String, String> form = new HashMap<String, String>();
        for (String name : names) {
            form.put(name, FormUtils.trim(req.getParameter(name)));
        }
        return form;
    }

    static Map<String, String> empty(String[] names) {
        Map<String, String> form = new HashMap<String, String>();
        for (String name : names) {
            form.put(name, "");
        }
        return form;
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Required text up to max characters; returns the value or records an error. */
    static String required(Map<String, String> form, Map<String, String> errors, String field, String label, int max) {
        String value = form.get(field) == null ? "" : form.get(field);
        if (value.isEmpty()) {
            errors.put(field, "Enter the " + label + ".");
        } else if (value.length() > max) {
            errors.put(field, "Use " + max + " characters or fewer.");
        }
        return value;
    }

    /** Optional text up to max characters; null when empty. */
    static String optional(Map<String, String> form, Map<String, String> errors, String field, int max) {
        String value = form.get(field) == null ? "" : form.get(field);
        if (value.length() > max) {
            errors.put(field, "Use " + max + " characters or fewer.");
        }
        return value.isEmpty() ? null : value;
    }

    /** Required choice from a select box (an id). */
    static Integer requiredId(Map<String, String> form, Map<String, String> errors, String field, String message) {
        Integer id = FormUtils.parseInt(form.get(field));
        if (id == null || id <= 0) {
            errors.put(field, message);
            return null;
        }
        return id;
    }

    /** Optional choice; an unreadable value is an error, an empty one means none. */
    static Integer optionalId(Map<String, String> form, Map<String, String> errors, String field) {
        String value = form.get(field) == null ? "" : form.get(field);
        if (value.isEmpty()) {
            return null;
        }
        Integer id = FormUtils.parseInt(value);
        if (id == null || id <= 0) {
            errors.put(field, "Choose an item from the list.");
            return null;
        }
        return id;
    }

    static int idOf(Map<String, String> form) {
        Integer id = FormUtils.parseInt(form.get("id"));
        return id == null || id < 0 ? 0 : id;
    }
}
