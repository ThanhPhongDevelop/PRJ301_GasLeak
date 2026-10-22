package api;

import com.google.gson.JsonObject;
import dao.DeviceDao;
import dao.GasEventDao;
import filter.SecurityFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Device heartbeat, header X-API-Key.
 * GET  /api/health : checks the key and returns last_seq (DataReplayer continues numbering from it)
 *                    and how many commands are waiting.
 * POST /api/health : same, and hands over the waiting commands (for a device with no session to send).
 */
@WebServlet("/api/health")
public class HealthApi extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        reply(req, res, false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        reply(req, res, true);
    }

    private void reply(HttpServletRequest req, HttpServletResponse res, boolean deliverCommands) throws IOException {
        int deviceId = (Integer) req.getAttribute(SecurityFilter.DEVICE_ID); // set by SecurityFilter
        JsonObject body = new JsonObject();
        try {
            DeviceDao devices = new DeviceDao();
            devices.touch(deviceId);
            body.addProperty("status", "ok");
            body.addProperty("device_id", deviceId);
            body.addProperty("last_seq", new GasEventDao().lastSeq(deviceId));
            if (deliverCommands) {
                body.add("commands", GasEventApi.commandsJson(devices.takePendingCommands(deviceId)));
            } else {
                body.addProperty("pending_commands", devices.countPendingCommands(deviceId));
            }
            body.addProperty("server_time", Instant.now().toString());
        } catch (SQLException e) {
            log("Health check failed for device " + deviceId, e);
            JsonResponse.error(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database unavailable");
            return;
        }
        JsonResponse.send(res, HttpServletResponse.SC_OK, body);
    }
}
