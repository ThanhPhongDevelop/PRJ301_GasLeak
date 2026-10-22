package form;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import model.OperatingProfile;
import service.CompensationModel;
import util.FormUtils;

/**
 * Fields and checks of the compensation profile form. Saving always creates a new version of the
 * device's profile; "edit" opens a version with its values filled in and saves the result as the newest.
 * Coefficients are in sensor units (ADC counts) per °C and per %RH.
 */
public final class OperatingProfileForm {

    public static final String[] FIELDS = {"id", "deviceId", "tRef", "rhRef", "mq2BT", "mq2BRh", "mq135BT",
        "mq135BRh"};
    static final double MAX_COEFFICIENT = 1000;

    private OperatingProfileForm() {
    }

    /** What to save: the device and the coefficients. */
    public static final class Draft {

        private final int deviceId;
        private final CompensationModel model;

        Draft(int deviceId, CompensationModel model) {
            this.deviceId = deviceId;
            this.model = model;
        }

        public int getDeviceId() {
            return deviceId;
        }

        public CompensationModel getModel() {
            return model;
        }
    }

    public static Map<String, String> read(HttpServletRequest req) {
        return Fields.read(req, FIELDS);
    }

    public static Map<String, String> empty() {
        Map<String, String> form = Fields.empty(FIELDS);
        form.put("tRef", "25");
        form.put("rhRef", "50");
        form.put("mq2BT", "0");
        form.put("mq2BRh", "0");
        form.put("mq135BT", "0");
        form.put("mq135BRh", "0");
        return form;
    }

    /** The values of a saved version, to start a corrected one from. */
    public static Map<String, String> of(OperatingProfile p) {
        Map<String, String> form = empty();
        form.put("id", String.valueOf(p.getProfileId()));
        form.put("deviceId", String.valueOf(p.getDeviceId()));
        CompensationModel m = p.getModel();
        if (m.isFitted()) {
            form.put("tRef", round(m.getTRef()));
            form.put("rhRef", round(m.getRhRef()));
            form.put("mq2BT", round(m.getMq2().getBT()));
            form.put("mq2BRh", round(m.getMq2().getBRh()));
            form.put("mq135BT", round(m.getMq135().getBT()));
            form.put("mq135BRh", round(m.getMq135().getBRh()));
        }
        return form;
    }

    /**
     * basedOn = the version the form was opened from, or null for a new profile. When it is given its
     * device is used, whatever the form says: a version always stays with its device.
     */
    public static Draft validate(Map<String, String> form, Map<String, String> errors, OperatingProfile basedOn) {
        int deviceId;
        if (basedOn != null) {
            deviceId = basedOn.getDeviceId();
        } else {
            Integer id = Fields.requiredId(form, errors, "deviceId", "Choose the device.");
            deviceId = id == null ? 0 : id;
        }
        double tRef = number(form, errors, "tRef", -40, 80, "Enter a temperature from -40 to 80 °C.");
        double rhRef = number(form, errors, "rhRef", 0, 100, "Enter a humidity from 0 to 100 %.");
        String coefficient = "Enter a number from -1000 to 1000 (0 for no correction).";
        double mq2BT = number(form, errors, "mq2BT", -MAX_COEFFICIENT, MAX_COEFFICIENT, coefficient);
        double mq2BRh = number(form, errors, "mq2BRh", -MAX_COEFFICIENT, MAX_COEFFICIENT, coefficient);
        double mq135BT = number(form, errors, "mq135BT", -MAX_COEFFICIENT, MAX_COEFFICIENT, coefficient);
        double mq135BRh = number(form, errors, "mq135BRh", -MAX_COEFFICIENT, MAX_COEFFICIENT, coefficient);
        return new Draft(deviceId, CompensationModel.manual(tRef, rhRef, mq2BT, mq2BRh, mq135BT, mq135BRh));
    }

    private static double number(Map<String, String> form, Map<String, String> errors, String field, double min,
            double max, String message) {
        Double value = FormUtils.parseDouble(form.get(field));
        if (value == null || value < min || value > max) {
            errors.put(field, message);
            return 0;
        }
        return value;
    }

    private static String round(double value) {
        return FormUtils.formatNumber(Math.round(value * 10000) / 10000.0);
    }
}
