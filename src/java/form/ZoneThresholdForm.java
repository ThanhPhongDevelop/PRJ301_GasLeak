package form;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import model.ZoneThreshold;
import util.FormUtils;

/** Fields and server-side checks of the zone levels form (values in sensor units, ADC counts for MQ sensors). */
public final class ZoneThresholdForm {

    public static final String[] FIELDS = {"id", "kitchenZoneId", "sensorTypeId", "warnLevel", "alarmLevel"};

    private ZoneThresholdForm() {
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        return Fields.empty(FIELDS);
    }

    public static Map<String, String> of(ZoneThreshold t) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(t.getZoneThresholdId()));
        form.put("kitchenZoneId", String.valueOf(t.getKitchenZoneId()));
        form.put("sensorTypeId", String.valueOf(t.getSensorTypeId()));
        form.put("warnLevel", FormUtils.formatNumber(t.getWarnLevel()));
        form.put("alarmLevel", FormUtils.formatNumber(t.getAlarmLevel()));
        return form;
    }

    public static ZoneThreshold validate(Map<String, String> form, Map<String, String> errors) {
        ZoneThreshold t = new ZoneThreshold();
        t.setZoneThresholdId(Fields.idOf(form));
        Integer zoneId = Fields.requiredId(form, errors, "kitchenZoneId", "Choose the kitchen zone.");
        t.setKitchenZoneId(zoneId == null ? 0 : zoneId);
        Integer typeId = Fields.requiredId(form, errors, "sensorTypeId", "Choose the sensor type.");
        t.setSensorTypeId(typeId == null ? 0 : typeId);
        Double warn = FormUtils.parseDouble(form.get("warnLevel"));
        Double alarm = FormUtils.parseDouble(form.get("alarmLevel"));
        if (warn == null || warn < 0) {
            errors.put("warnLevel", "Enter a number of 0 or more.");
        }
        if (alarm == null || alarm < 0) {
            errors.put("alarmLevel", "Enter a number of 0 or more.");
        } else if (warn != null && warn >= alarm) {
            errors.put("alarmLevel", "The alarm level must be above the warning level.");
        }
        t.setWarnLevel(warn == null ? 0 : warn);
        t.setAlarmLevel(alarm == null ? 0 : alarm);
        return t;
    }
}
