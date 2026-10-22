package form;

import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import model.DeviceInfo;

/** Fields and checks of the device form. The code is set once at registration and never changes. */
public final class DeviceForm {

    public static final String[] FIELDS = {"id", "deviceCode", "location", "firmware", "status"};
    private static final Pattern DEVICE_CODE = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");

    private DeviceForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        Map<String, String> form = Fields.empty(FIELDS);
        form.put("status", "ACTIVE");
        return form;
    }

    public static Map<String, String> of(DeviceInfo d) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(d.getDeviceId()));
        form.put("deviceCode", d.getDeviceCode());
        form.put("location", Fields.text(d.getLocation()));
        form.put("firmware", Fields.text(d.getFirmware()));
        form.put("status", d.getStatus());
        return form;
    }

    /** creating = true checks the device code too. Returns the values in a DeviceInfo. */
    public static DeviceInfo validate(Map<String, String> form, Map<String, String> errors, boolean creating) {
        DeviceInfo d = new DeviceInfo();
        d.setDeviceId(Fields.idOf(form));
        String code = form.get("deviceCode") == null ? "" : form.get("deviceCode");
        if (creating && !DEVICE_CODE.matcher(code).matches()) {
            errors.put("deviceCode", "Use 3 to 30 letters, digits, dots, dashes or underscores, for example ESP32-KITCHEN-02.");
        }
        d.setDeviceCode(code);
        d.setLocation(Fields.optional(form, errors, "location", 80));
        d.setFirmware(Fields.optional(form, errors, "firmware", 20));
        String status = form.get("status");
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            errors.put("status", "Choose Active or Inactive.");
        }
        d.setStatus(status);
        return d;
    }
}
