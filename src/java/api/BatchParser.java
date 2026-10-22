package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the body of POST /api/gas/batch:
 * {"device_id": 1, "sessions": [{"seq": 1, "measured_at": "2026-10-08 05:30:00", "mq2_raw": 1830.5,
 *   "mq135_raw": 1650.2, "temp_c": 27.4, "rh_pct": 61.0, "flame": false}, ...]}
 *
 * measured_at is UTC, either "yyyy-MM-dd HH:mm:ss" or ISO 8601 with an offset. Sensor values may be
 * missing or null: the session is still stored and the rules report SENSOR_FAULT. A session with a
 * missing seq or time, or a value of the wrong type, is rejected alone; the rest of the batch is kept.
 * compensated_score sent by a device is ignored: the server computes it from the device profile.
 */
public final class BatchParser {

    public static final int MAX_SESSIONS = 200;
    private static final LocalDateTime EARLIEST = LocalDateTime.of(2015, 1, 1, 0, 0);

    private BatchParser() {
    }

    /** Throws IllegalArgumentException (answered with 400) when the body as a whole is unusable. */
    public static Batch parse(JsonElement root, LocalDateTime nowUtc) {
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("The body must be a JSON object");
        }
        JsonObject body = root.getAsJsonObject();
        JsonElement sessionsElement = body.get("sessions");
        if (sessionsElement == null || !sessionsElement.isJsonArray()) {
            throw new IllegalArgumentException("The body must contain a \"sessions\" array");
        }
        JsonArray sessions = sessionsElement.getAsJsonArray();
        if (sessions.size() > MAX_SESSIONS) {
            throw new IllegalArgumentException("At most " + MAX_SESSIONS + " sessions per request");
        }
        Integer claimedDevice = null;
        JsonElement device = body.get("device_id");
        if (device != null && !device.isJsonNull()) {
            Double id = number(device, "device_id");
            if (id == null || id != Math.rint(id)) {
                throw new IllegalArgumentException("device_id must be a whole number");
            }
            claimedDevice = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, id));
        }
        List<SessionInput> accepted = new ArrayList<SessionInput>();
        List<Rejection> rejected = new ArrayList<Rejection>();
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < sessions.size(); i++) {
            JsonElement element = sessions.get(i);
            try {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("each session must be a JSON object");
                }
                SessionInput input = parseSession(element.getAsJsonObject(), nowUtc);
                if (!seen.add(input.seq)) {
                    throw new IllegalArgumentException("seq " + input.seq + " appears twice in this request");
                }
                accepted.add(input);
            } catch (IllegalArgumentException e) {
                rejected.add(new Rejection(i, e.getMessage()));
            }
        }
        return new Batch(claimedDevice, accepted, rejected);
    }

    private static SessionInput parseSession(JsonObject json, LocalDateTime nowUtc) {
        Double seq = number(json.get("seq"), "seq");
        if (seq == null) {
            throw new IllegalArgumentException("seq is required");
        }
        if (seq != Math.rint(seq) || seq < 1 || seq > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("seq must be a whole number from 1");
        }
        JsonElement time = json.get("measured_at");
        if (time == null || time.isJsonNull() || !time.isJsonPrimitive() || !time.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("measured_at is required as text");
        }
        LocalDateTime measuredAt = parseTime(time.getAsString());
        if (measuredAt.isBefore(EARLIEST) || measuredAt.isAfter(nowUtc.plusDays(1))) {
            throw new IllegalArgumentException("measured_at " + time.getAsString()
                    + " is not a plausible UTC time; check the device clock");
        }
        return new SessionInput(seq.intValue(), measuredAt, number(json.get("mq2_raw"), "mq2_raw"),
                number(json.get("mq135_raw"), "mq135_raw"), number(json.get("temp_c"), "temp_c"),
                number(json.get("rh_pct"), "rh_pct"), flag(json.get("flame")));
    }

    /** UTC wall-clock time from "yyyy-MM-dd HH:mm:ss[.fff]" or ISO 8601 with Z or an offset. */
    static LocalDateTime parseTime(String text) {
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException notOffset) {
            try {
                return LocalDateTime.parse(value.replace(' ', 'T'));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("measured_at must look like 2026-10-08 05:30:00 (UTC)");
            }
        }
    }

    /** Missing, null, NaN and infinity become null (a missing reading); text is a type error. */
    private static Double number(JsonElement element, String name) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        double value = element.getAsDouble();
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    private static Boolean flag(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive p = element.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber() && (p.getAsDouble() == 0 || p.getAsDouble() == 1)) {
                return p.getAsDouble() == 1;
            }
        }
        throw new IllegalArgumentException("flame must be true, false, 0 or 1");
    }

    /** One accepted session. */
    public static final class SessionInput {

        private final int seq;
        private final LocalDateTime measuredAt;
        private final Double mq2Raw;
        private final Double mq135Raw;
        private final Double tempC;
        private final Double rhPct;
        private final Boolean flame;

        public SessionInput(int seq, LocalDateTime measuredAt, Double mq2Raw, Double mq135Raw, Double tempC,
                Double rhPct, Boolean flame) {
            this.seq = seq;
            this.measuredAt = measuredAt;
            this.mq2Raw = mq2Raw;
            this.mq135Raw = mq135Raw;
            this.tempC = tempC;
            this.rhPct = rhPct;
            this.flame = flame;
        }

        public int getSeq() {
            return seq;
        }

        public LocalDateTime getMeasuredAt() {
            return measuredAt;
        }

        public Double getMq2Raw() {
            return mq2Raw;
        }

        public Double getMq135Raw() {
            return mq135Raw;
        }

        public Double getTempC() {
            return tempC;
        }

        public Double getRhPct() {
            return rhPct;
        }

        public Boolean getFlame() {
            return flame;
        }
    }

    /** A session that was not stored, with its position in the request and the reason. */
    public static final class Rejection {

        private final int index;
        private final String reason;

        Rejection(int index, String reason) {
            this.index = index;
            this.reason = reason;
        }

        public int getIndex() {
            return index;
        }

        public String getReason() {
            return reason;
        }
    }

    /** Parsed request. */
    public static final class Batch {

        private final Integer claimedDeviceId;
        private final List<SessionInput> sessions;
        private final List<Rejection> rejected;

        Batch(Integer claimedDeviceId, List<SessionInput> sessions, List<Rejection> rejected) {
            this.claimedDeviceId = claimedDeviceId;
            this.sessions = Collections.unmodifiableList(sessions);
            this.rejected = Collections.unmodifiableList(rejected);
        }

        public Integer getClaimedDeviceId() {
            return claimedDeviceId;
        }

        public List<SessionInput> getSessions() {
            return sessions;
        }

        public List<Rejection> getRejected() {
            return rejected;
        }
    }
}
