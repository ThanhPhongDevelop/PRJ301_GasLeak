package form;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Which form field a stored procedure error belongs to. The controller shows the message (written in
 * the procedure) next to that field; when there is no field, it shows the message as a notice.
 */
public final class ProcErrors {

    private static final Map<Integer, String> FIELD_BY_ERROR;

    static {
        Map<Integer, String> m = new HashMap<Integer, String>();
        m.put(50060, "alarmLevel"); // ZoneThreshold_Upsert: warning level not below alarm level
        m.put(50061, "kitchenZoneId");
        m.put(50062, "sensorTypeId");
        m.put(50070, "ownerUserId"); // Home_Save
        m.put(50073, "status"); // GasSensor_Save
        m.put(50074, "deviceId");
        m.put(50075, "sensorTypeId");
        m.put(50076, "kitchenZoneId");
        m.put(50077, "sensorCode");
        m.put(50079, "homeId"); // EmergencyContact_Save
        m.put(50082, "priority");
        m.put(50083, "notifyMinLabel");
        m.put(50086, "deviceCode"); // Device_Register
        m.put(50087, "status"); // Device_Update
        m.put(50051, "deviceId"); // OperatingProfile_Save
        m.put(50090, "name"); // SensorType_Save
        m.put(50091, "minValid");
        m.put(50092, "maxValid");
        m.put(50093, "name");
        m.put(50095, "name");
        m.put(50098, "deviceId"); // Calibration_Save
        m.put(50099, "sensorTypeId");
        m.put(50100, "scaleVal");
        FIELD_BY_ERROR = Collections.unmodifiableMap(m);
    }

    private ProcErrors() {
    }

    /** The field name, or null when the error is not about one field (for example "no longer exists"). */
    public static String fieldOf(SQLException e) {
        return FIELD_BY_ERROR.get(e.getErrorCode());
    }

    static Map<Integer, String> fieldsByError() {
        return FIELD_BY_ERROR;
    }
}
