package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dao.DashboardDao;
import dao.DeviceDao;
import dao.GasEventDao;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.DashboardSummary;
import model.DeviceInfo;
import model.GasEvent;
import model.Labels;
import service.DeviceSetup;
import service.RiskScorer;
import util.FormUtils;

/**
 * GET /api/dashboard?device=1 for the live dashboard (polled every 5 seconds, needs GASEVENT READ).
 * Returns the device state, the zone levels, the last 60 sessions with raw and compensated MQ-2,
 * the latest session for the gauge, and the alert counts.
 */
@WebServlet("/api/dashboard")
public class DashboardDataApi extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int POINTS = 60;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonObject body = new JsonObject();
        try {
            DeviceDao devices = new DeviceDao();
            Integer deviceId = FormUtils.parseInt(req.getParameter("device"));
            if (deviceId == null) {
                deviceId = devices.firstActiveDeviceId();
            }
            DeviceInfo device = deviceId == null ? null : devices.findDevice(deviceId);
            DashboardSummary summary = new DashboardDao().summary();
            JsonObject alerts = new JsonObject();
            alerts.addProperty("open", summary.getOpenAlerts());
            alerts.addProperty("urgent", summary.getUrgentAlerts());
            body.add("alerts", alerts);
            if (device == null) {
                body.add("device", null);
                JsonResponse.send(res, HttpServletResponse.SC_OK, body);
                return;
            }
            body.add("device", deviceJson(device));

            DeviceSetup setup = DeviceSetup.load(devices, device.getDeviceId());
            JsonObject levels = new JsonObject();
            levels.addProperty("mq2Warn", setup.getRules().getMq2().getWarn());
            levels.addProperty("mq2Alarm", setup.getRules().getMq2().getAlarm());
            levels.addProperty("mq135Warn", setup.getRules().getMq135().getWarn());
            levels.addProperty("mq135Alarm", setup.getRules().getMq135().getAlarm());
            levels.addProperty("source", setup.getRules().getThresholdSource());
            body.add("levels", levels);

            JsonObject compensation = new JsonObject();
            compensation.addProperty("fitted", setup.getCompensation().isFitted());
            compensation.addProperty("text", setup.getCompensation().describe());
            body.add("compensation", compensation);

            RiskScorer scorer = RiskScorer.forContext(getServletContext());
            JsonObject model = new JsonObject();
            model.addProperty("name", scorer == null ? null : scorer.getModelName());
            model.addProperty("problem", RiskScorer.problemOf(getServletContext()));
            body.add("model", model);

            List<GasEvent> sessions = new GasEventDao().recentForDevice(device.getDeviceId(), POINTS);
            JsonArray series = new JsonArray();
            for (GasEvent s : sessions) {
                JsonObject point = new JsonObject();
                point.addProperty("id", s.getSessionId());
                point.addProperty("t", text(s.getMeasuredAt()));
                point.addProperty("raw", s.getMq2Raw());
                point.addProperty("comp", s.getCompensatedScore());
                point.addProperty("label", s.getRuleLabel());
                series.add(point);
            }
            body.add("series", series);
            body.add("latest", sessions.isEmpty() ? null : latestJson(sessions.get(sessions.size() - 1)));
        } catch (SQLException e) {
            log("Dashboard data failed", e);
            JsonResponse.error(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database unavailable");
            return;
        }
        JsonResponse.send(res, HttpServletResponse.SC_OK, body);
    }

    private static JsonObject deviceJson(DeviceInfo device) {
        JsonObject json = new JsonObject();
        json.addProperty("id", device.getDeviceId());
        json.addProperty("code", device.getDeviceCode());
        json.addProperty("location", device.getLocation());
        json.addProperty("online", device.isOnline());
        json.addProperty("lastSeen", text(device.getLastSeen()));
        json.addProperty("controlOwner", device.getControlOwnerName());
        return json;
    }

    private static JsonObject latestJson(GasEvent s) {
        JsonObject json = new JsonObject();
        json.addProperty("id", s.getSessionId());
        json.addProperty("seq", s.getDeviceSeq());
        json.addProperty("measuredAt", text(s.getMeasuredAt()));
        json.addProperty("mq2Raw", s.getMq2Raw());
        json.addProperty("mq2Compensated", s.getCompensatedScore());
        json.addProperty("mq135Raw", s.getMq135Raw());
        json.addProperty("mq135Compensated", s.getMq135Compensated());
        json.addProperty("tempC", s.getTempC());
        json.addProperty("rhPct", s.getRhPct());
        json.addProperty("flame", s.getFlameDetected());
        json.addProperty("ruleLabel", s.getRuleLabel());
        json.addProperty("ruleLabelText", Labels.displayNameOf(s.getRuleLabel()));
        json.addProperty("severity", Labels.severityOf(s.getRuleLabel()));
        json.addProperty("mlLabel", s.getMlLabel());
        json.addProperty("mlConfidence", s.getMlConfidence());
        return json;
    }

    /** "2026-10-08 05:31:02" (UTC, seconds only). */
    private static String text(Timestamp time) {
        if (time == null) {
            return null;
        }
        String value = time.toString();
        return value.length() > 19 ? value.substring(0, 19) : value;
    }
}
