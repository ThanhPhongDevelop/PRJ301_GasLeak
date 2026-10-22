package api;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

/** Writes JSON replies for the APIs. */
final class JsonResponse {

    private JsonResponse() {
    }

    static void send(HttpServletResponse res, int status, JsonObject body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.getWriter().write(body.toString());
    }

    static void error(HttpServletResponse res, int status, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        send(res, status, body);
    }
}
