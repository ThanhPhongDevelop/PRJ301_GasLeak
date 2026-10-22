package form;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import model.EmergencyContact;
import model.Labels;
import util.FormUtils;

/** Fields and server-side checks of the emergency contact form. */
public final class EmergencyContactForm {

    public static final String[] FIELDS = {"id", "homeId", "fullName", "phone", "email", "relation", "priority",
        "notifyMinLabel"};
    /** Choices for "notify from", least serious first (same values as the database check). */
    public static final List<String> NOTIFY_LABELS = Collections.unmodifiableList(Arrays.asList(
            Labels.MINOR_LEAK, Labels.SENSOR_FAULT, Labels.MAJOR_LEAK, Labels.FLAME_DETECTED));

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ().-]{3,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmergencyContactForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        Map<String, String> form = Fields.empty(FIELDS);
        form.put("priority", "1");
        form.put("notifyMinLabel", Labels.MINOR_LEAK);
        return form;
    }

    public static Map<String, String> of(EmergencyContact c) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(c.getContactId()));
        form.put("homeId", String.valueOf(c.getHomeId()));
        form.put("fullName", c.getFullName());
        form.put("phone", c.getPhone());
        form.put("email", Fields.text(c.getEmail()));
        form.put("relation", Fields.text(c.getRelation()));
        form.put("priority", String.valueOf(c.getPriority()));
        form.put("notifyMinLabel", c.getNotifyMinLabel());
        return form;
    }

    public static EmergencyContact validate(Map<String, String> form, Map<String, String> errors) {
        EmergencyContact c = new EmergencyContact();
        c.setContactId(Fields.idOf(form));
        Integer homeId = Fields.requiredId(form, errors, "homeId", "Choose the home this contact is for.");
        c.setHomeId(homeId == null ? 0 : homeId);
        c.setFullName(Fields.required(form, errors, "fullName", "full name", 80));
        String phone = form.get("phone") == null ? "" : form.get("phone");
        if (!PHONE.matcher(phone).matches() || phone.replaceAll("[^0-9]", "").length() < 3) {
            errors.put("phone", "Enter a phone number with at least 3 digits, for example 0900 000 001 or 114.");
        }
        c.setPhone(phone);
        String email = Fields.optional(form, errors, "email", 100);
        if (email != null && !EMAIL.matcher(email).matches()) {
            errors.put("email", "Enter an address like name@example.com, or leave it empty.");
        }
        c.setEmail(email);
        c.setRelation(Fields.optional(form, errors, "relation", 40));
        Integer priority = FormUtils.parseInt(form.get("priority"));
        if (priority == null || priority < 1 || priority > 99) {
            errors.put("priority", "Enter a number from 1 (called first) to 99.");
        } else {
            c.setPriority(priority);
        }
        String label = form.get("notifyMinLabel");
        if (!NOTIFY_LABELS.contains(label)) {
            errors.put("notifyMinLabel", "Choose from which alert this contact is called.");
        }
        c.setNotifyMinLabel(label);
        return c;
    }
}
