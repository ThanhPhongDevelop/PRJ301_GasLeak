package form;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import model.Calibration;
import util.FormUtils;

/**
 * Fields and checks of the calibration form (corrected = raw * scale + offset). When changing a
 * calibration, device and sensor type are shown but not editable: the procedure refuses to move it.
 */
public final class CalibrationForm {

    public static final String[] FIELDS = {"id", "deviceId", "sensorTypeId", "offsetVal", "scaleVal", "validUntil"};
    static final double MAX_OFFSET = 100000;
    static final double MAX_SCALE = 1000;

    private CalibrationForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        Map<String, String> form = Fields.empty(FIELDS);
        form.put("offsetVal", "0");
        form.put("scaleVal", "1");
        return form;
    }

    public static Map<String, String> of(Calibration c) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(c.getCalibId()));
        form.put("deviceId", String.valueOf(c.getDeviceId()));
        form.put("sensorTypeId", String.valueOf(c.getSensorTypeId()));
        form.put("offsetVal", FormUtils.formatNumber(c.getOffsetVal()));
        form.put("scaleVal", FormUtils.formatNumber(c.getScaleVal()));
        form.put("validUntil", Fields.text(c.getValidUntil()));
        return form;
    }

    public static Calibration validate(Map<String, String> form, Map<String, String> errors) {
        Calibration c = new Calibration();
        c.setCalibId(Fields.idOf(form));
        Integer deviceId = Fields.requiredId(form, errors, "deviceId", "Choose the device.");
        c.setDeviceId(deviceId == null ? 0 : deviceId);
        Integer typeId = Fields.requiredId(form, errors, "sensorTypeId", "Choose the sensor type.");
        c.setSensorTypeId(typeId == null ? 0 : typeId);
        Double offset = FormUtils.parseDouble(form.get("offsetVal"));
        if (offset == null || Math.abs(offset) > MAX_OFFSET) {
            errors.put("offsetVal", "Enter a number from -100000 to 100000 (0 for no offset).");
        } else {
            c.setOffsetVal(offset);
        }
        Double scale = FormUtils.parseDouble(form.get("scaleVal"));
        if (scale == null || scale <= 0 || scale > MAX_SCALE) {
            errors.put("scaleVal", "Enter a number above 0 and up to 1000 (1 for no scaling).");
        } else {
            c.setScaleVal(scale);
        }
        String until = form.get("validUntil") == null ? "" : form.get("validUntil");
        if (!until.isEmpty()) {
            try {
                c.setValidUntil(Date.valueOf(LocalDate.parse(until)));
            } catch (DateTimeParseException e) {
                errors.put("validUntil", "Enter a date like 2026-12-31, or leave it empty.");
            }
        }
        return c;
    }
}
