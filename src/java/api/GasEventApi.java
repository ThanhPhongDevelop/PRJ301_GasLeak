package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import filter.SecurityFilter;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.DeviceCommand;
import service.IngestService;
import service.RiskScorer;

/**
 * POST /api/gas/batch with header X-API-Key. The device is the one that owns the key (checked by
 * SecurityFilter); a device_id in the body must match it. Reply:
 * {"saved":3,"duplicates":0,"alerts":1,"rejected":[],"commands":[{"id":7,"command":"CLOSE_VALVE"}]}
 */
@WebServlet("/api/gas/batch")
public class GasEventApi extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_BODY_CHARS = 1000000;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        int deviceId = (Integer) req.getAttribute(SecurityFilter.DEVICE_ID);
        String body = readBody(req);
        if (body == null) {
            JsonResponse.error(res, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request too large: send at most " + BatchParser.MAX_SESSIONS + " sessions per request");
            return;
        }
        BatchParser.Batch batch;
        try {
            JsonElement root = JsonParser.parseString(body);
            batch = BatchParser.parse(root, LocalDateTime.now(ZoneOffset.UTC));
        } catch (JsonParseException e) {
            JsonResponse.error(res, HttpServletResponse.SC_BAD_REQUEST, "The body is not valid JSON");
            return;
        } catch (IllegalArgumentException e) {
            JsonResponse.error(res, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        if (batch.getClaimedDeviceId() != null && batch.getClaimedDeviceId() != deviceId) {
            JsonResponse.error(res, HttpServletResponse.SC_FORBIDDEN, "device_id does not match the API key");
            return;
        }

        IngestService.Result result;
        try {
            result = new IngestService(RiskScorer.forContext(getServletContext())).ingest(deviceId,
                    batch.getSessions());
        } catch (SQLException e) {
            log("Could not store the sessions of device " + deviceId, e);
            JsonResponse.error(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The server could not store the sessions; send them again later");
            return;
        }

        JsonObject reply = new JsonObject();
        reply.addProperty("saved", result.getSaved());
        reply.addProperty("duplicates", result.getDuplicates());
        reply.addProperty("alerts", result.getAlertsRaised());
        JsonArray rejected = new JsonArray();
        for (BatchParser.Rejection r : batch.getRejected()) {
            JsonObject item = new JsonObject();
            item.addProperty("index", r.getIndex());
            item.addProperty("reason", r.getReason());
            rejected.add(item);
        }
        reply.add("rejected", rejected);
        reply.add("commands", commandsJson(result.getCommands()));
        JsonResponse.send(res, HttpServletResponse.SC_OK, reply);
    }

    static JsonArray commandsJson(Iterable<DeviceCommand> commands) {
        JsonArray array = new JsonArray();
        for (DeviceCommand c : commands) {
            JsonObject item = new JsonObject();
            item.addProperty("id", c.getCommandId());
            item.addProperty("command", c.getCommand());
            array.add(item);
        }
        return array;
    }

    /** Body as text, or null when it is longer than the limit. */
    private static String readBody(HttpServletRequest req) throws IOException {
        if (req.getContentLengthLong() > MAX_BODY_CHARS * 4L) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        BufferedReader reader = req.getReader();
        int n;
        while ((n = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, n);
            if (sb.length() > MAX_BODY_CHARS) {
                return null;
            }
        }
        return sb.toString();
    }
}
