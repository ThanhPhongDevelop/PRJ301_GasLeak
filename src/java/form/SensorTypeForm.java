package form;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import model.SensorType;
import util.FormUtils;

/**
 * Fields and checks of the sensor type form. The valid range is optional, but both ends go together:
 * the rules report SENSOR_FAULT for readings outside it.
 */
public final class SensorTypeForm {

    public static final String[] FIELDS = {"id", "name", "unit", "minValid", "maxValid"};

    private SensorTypeForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        return Fields.empty(FIELDS);
    }

    public static Map<String, String> of(SensorType t) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(t.getSensorTypeId()));
        form.put("name", t.getName());
        form.put("unit", Fields.text(t.getUnit()));
        form.put("minValid", t.getMinValid() == null ? "" : FormUtils.formatNumber(t.getMinValid()));
        form.put("maxValid", t.getMaxValid() == null ? "" : FormUtils.formatNumber(t.getMaxValid()));
        return form;
    }

    public static SensorType validate(Map<String, String> form, Map<String, String> errors) {
        SensorType t = new SensorType();
        t.setSensorTypeId(Fields.idOf(form));
        t.setName(Fields.required(form, errors, "name", "name of the sensor type", 40));
        t.setUnit(Fields.optional(form, errors, "unit", 20));
        String minText = form.get("minValid") == null ? "" : form.get("minValid");
        String maxText = form.get("maxValid") == null ? "" : form.get("maxValid");
        if (minText.isEmpty() && maxText.isEmpty()) {
            return t; // no range check for this type
        }
        Double min = FormUtils.parseDouble(minText);
        Double max = FormUtils.parseDouble(maxText);
        if (min == null) {
            errors.put("minValid", minText.isEmpty()
                    ? "Enter both ends of the valid range, or leave both empty." : "Enter a number.");
        }
        if (max == null) {
            errors.put("maxValid", maxText.isEmpty()
                    ? "Enter both ends of the valid range, or leave both empty." : "Enter a number.");
        } else if (min != null && min >= max) {
            errors.put("maxValid", "The highest valid value must be above the lowest.");
        }
        t.setMinValid(min);
        t.setMaxValid(max);
        return t;
    }
}
