package form;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import model.GasSensor;

/** Fields and server-side checks of the gas sensor form. */
public final class GasSensorForm {

    public static final String[] FIELDS = {"id", "sensorCode", "deviceId", "sensorTypeId", "kitchenZoneId",
        "pinLabel", "installedAt", "status"};

    private GasSensorForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    /** New sensors start ACTIVE. */
    public static Map<String, String> empty() {
        Map<String, String> form = Fields.empty(FIELDS);
        form.put("status", "ACTIVE");
        return form;
    }

    public static Map<String, String> of(GasSensor s) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(s.getGasSensorId()));
        form.put("sensorCode", s.getSensorCode());
        form.put("deviceId", String.valueOf(s.getDeviceId()));
        form.put("sensorTypeId", String.valueOf(s.getSensorTypeId()));
        form.put("kitchenZoneId", Fields.text(s.getKitchenZoneId()));
        form.put("pinLabel", Fields.text(s.getPinLabel()));
        form.put("installedAt", Fields.text(s.getInstalledAt()));
        form.put("status", s.getStatus());
        return form;
    }

    public static GasSensor validate(Map<String, String> form, Map<String, String> errors) {
        GasSensor s = new GasSensor();
        s.setGasSensorId(Fields.idOf(form));
        String code = form.get("sensorCode") == null ? "" : form.get("sensorCode");
        if (!Fields.CODE.matcher(code).matches()) {
            errors.put("sensorCode", "Use 2 to 30 letters, digits, dots, dashes or underscores, for example MQ2-01.");
        }
        s.setSensorCode(code);
        Integer deviceId = Fields.requiredId(form, errors, "deviceId", "Choose the device the sensor is wired to.");
        s.setDeviceId(deviceId == null ? 0 : deviceId);
        Integer typeId = Fields.requiredId(form, errors, "sensorTypeId", "Choose the sensor type.");
        s.setSensorTypeId(typeId == null ? 0 : typeId);
        s.setKitchenZoneId(Fields.optionalId(form, errors, "kitchenZoneId"));
        s.setPinLabel(Fields.optional(form, errors, "pinLabel", 20));
        String installed = form.get("installedAt") == null ? "" : form.get("installedAt");
        if (!installed.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(installed);
                if (date.isAfter(LocalDate.now().plusDays(1))) {
                    errors.put("installedAt", "The installation date cannot be in the future.");
                } else {
                    s.setInstalledAt(Date.valueOf(date));
                }
            } catch (DateTimeParseException e) {
                errors.put("installedAt", "Enter a date like 2026-10-08.");
            }
        }
        String status = form.get("status");
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            errors.put("status", "Choose Active or Inactive.");
        }
        s.setStatus(status);
        return s;
    }
}
