package tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import util.DbContext;

/**
 * Sends the public sessions to the API exactly like the device: batches, a buffer that survives
 * simulated outages, and the X-API-Key header. Improvements over the workshop version:
 * - numbering continues from the last seq the server has (GET /api/health), so a second run is not
 *   rejected as duplicates;
 * - a session is marked sent in PublicSession only after the server confirmed it, and a failed
 *   request keeps the buffer for the next attempt instead of losing it;
 * - times are sent in UTC.
 * Run File in NetBeans while the web application is running. To replay everything again:
 * UPDATE PublicSession SET sent = 0;
 */
public class DataReplayer {

    private static final String BASE_URL = "http://localhost:8080/PRJ301";
    private static final String API_KEY = "device-1-api-key";
    private static final double DROP_RATE = 0.15; // probability of a simulated outage
    private static final int BATCH_LIMIT = 20;
    private static final int MAX_BUFFER = 100; // like the firmware queue: the oldest session is dropped when full
    private static final long DELAY_MS = 200;
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        JsonObject health = request("GET", "/api/health", null);
        if (health == null) {
            System.out.println("The API did not answer. Start the web application and check API_KEY.");
            return;
        }
        int seq = health.get("last_seq").getAsInt();
        System.out.println("Server has seq " + seq + " for this device; continuing from " + (seq + 1));

        Random random = new Random(42);
        List<Pending> buffer = new ArrayList<Pending>();
        int sent = 0;
        int dropped = 0;
        try (Connection conn = DbContext.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT public_id, mq2_raw, mq135_raw, temp_c, rh_pct "
                        + "FROM PublicSession WHERE sent = 0 ORDER BY public_id")) {
            while (rs.next()) {
                JsonObject session = new JsonObject();
                session.addProperty("seq", ++seq);
                session.addProperty("measured_at", LocalDateTime.now(ZoneOffset.UTC).format(UTC_TIME));
                session.addProperty("mq2_raw", rs.getDouble("mq2_raw"));
                session.addProperty("mq135_raw", rs.getDouble("mq135_raw"));
                session.addProperty("temp_c", rs.getDouble("temp_c"));
                session.addProperty("rh_pct", rs.getDouble("rh_pct"));
                buffer.add(new Pending(rs.getLong("public_id"), session));
                if (buffer.size() > MAX_BUFFER) {
                    buffer.remove(0); // stays unsent in PublicSession, so the next run sends it
                    dropped++;
                }
                if (random.nextDouble() < DROP_RATE) {
                    System.out.println("network down, buffered sessions: " + buffer.size());
                } else if (buffer.size() >= BATCH_LIMIT || random.nextDouble() < 0.5) {
                    sent += flush(buffer);
                }
                Thread.sleep(DELAY_MS);
            }
        }
        for (int attempt = 1; attempt <= 3 && !buffer.isEmpty(); attempt++) {
            sent += flush(buffer);
            if (!buffer.isEmpty()) {
                Thread.sleep(2000L * attempt);
            }
        }
        System.out.println("Done: " + sent + " sessions confirmed by the server, " + buffer.size()
                + " still buffered, " + dropped + " dropped from a full buffer (still unsent in PublicSession).");
    }

    /** Sends the buffer; on success marks the sessions sent and empties the buffer. Returns how many were sent. */
    private static int flush(List<Pending> buffer) throws SQLException {
        if (buffer.isEmpty()) {
            return 0;
        }
        JsonArray sessions = new JsonArray();
        for (Pending p : buffer) {
            sessions.add(p.json);
        }
        JsonObject body = new JsonObject();
        body.addProperty("device_id", 1);
        body.add("sessions", sessions);
        JsonObject reply = request("POST", "/api/gas/batch", body.toString());
        if (reply == null) {
            System.out.println("send failed, keeping " + buffer.size() + " sessions for the next attempt");
            return 0;
        }
        markSent(buffer);
        int count = buffer.size();
        buffer.clear();
        System.out.println("sent " + count + " sessions: saved " + reply.get("saved") + ", duplicates "
                + reply.get("duplicates") + ", alerts " + reply.get("alerts")
                + (reply.getAsJsonArray("commands").size() > 0 ? ", commands " + reply.get("commands") : ""));
        return count;
    }

    private static void markSent(List<Pending> buffer) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE PublicSession SET sent = 1 WHERE public_id = ?")) {
            for (Pending p : buffer) {
                ps.setLong(1, p.publicId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** JSON reply for HTTP 200, or null for any failure (printed). */
    private static JsonObject request(String method, String path, String body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("X-API-Key", API_KEY);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = conn.getResponseCode();
            String text = read(status < 400 ? conn.getInputStream() : conn.getErrorStream());
            if (status != 200) {
                System.out.println(method + " " + path + " answered " + status + ": " + text);
                return null;
            }
            JsonElement json = JsonParser.parseString(text);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            System.out.println(method + " " + path + " failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream input = in) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, n);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Pending {

        final long publicId;
        final JsonObject json;

        Pending(long publicId, JsonObject json) {
            this.publicId = publicId;
            this.json = json;
        }
    }
}
