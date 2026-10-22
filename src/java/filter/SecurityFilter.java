package filter;

import dao.PermissionDao;
import dao.UserDao;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.User;
import util.SecurityUtils;

/**
 * Lets public pages through, lets devices through with a valid X-API-Key, and for every
 * other path requires a login, a CSRF token on POST, and the READ or WRITE permission
 * on the resource mapped to the servlet path.
 */
@WebFilter(urlPatterns = {"/*"})
public class SecurityFilter implements Filter {

    public static final String LOGIN_USER = "LOGIN_USER";
    public static final String CSRF_TOKEN = "CSRF_TOKEN";
    public static final String CSRF_PARAM = "csrfToken";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    /** Request attribute with the device id proven by the API key; APIs must use it, not the body. */
    public static final String DEVICE_ID = "DEVICE_ID";

    private static final Set<String> PUBLIC_PATHS = new HashSet<String>(Arrays.asList(
            "/index.jsp", "/login.jsp", "/register.jsp", "/error.jsp",
            "/LoginController", "/RegisterController", "/LogoutController"));

    private static final Set<String> DEVICE_API_PATHS = new HashSet<String>(Arrays.asList(
            "/api/gas/batch", "/api/health"));

    // servlet path -> protected resource name (Permissions table). Add a line for every new controller.
    private static final Map<String, String> RESOURCE_BY_PATH = new HashMap<String, String>();

    static {
        RESOURCE_BY_PATH.put("/KitchenZoneController", "KITCHENZONE");
        RESOURCE_BY_PATH.put("/GasEventController", "GASEVENT");
        RESOURCE_BY_PATH.put("/AlertController", "ALERT");
        RESOURCE_BY_PATH.put("/UserController", "USER");
        RESOURCE_BY_PATH.put("/AuditController", "AUDIT");
        RESOURCE_BY_PATH.put("/HomeController", "HOME");
        RESOURCE_BY_PATH.put("/DeviceController", "DEVICE");
        RESOURCE_BY_PATH.put("/GasSensorController", "GAS_SENSOR");
        RESOURCE_BY_PATH.put("/ZoneThresholdController", "ZONE_THRESHOLD");
        RESOURCE_BY_PATH.put("/EmergencyContactController", "EMERGENCY_CONTACT");
        RESOURCE_BY_PATH.put("/SensorTypeController", "SENSOR_TYPE");
        RESOURCE_BY_PATH.put("/CalibrationController", "CALIBRATION");
        RESOURCE_BY_PATH.put("/OperatingProfileController", "PROFILE");
        RESOURCE_BY_PATH.put("/MaintenanceController", "MAINTENANCE");
        RESOURCE_BY_PATH.put("/ControlController", "CONTROL");
        RESOURCE_BY_PATH.put("/api/dashboard", "GASEVENT");
    }

    /** Resource that protects this servlet path, or null when only a login is needed. */
    public static String resourceFor(String servletPath) {
        return RESOURCE_BY_PATH.get(servletPath);
    }

    /** Paths open without login: sign-in pages and device paths (the latter need an API key). */
    public static boolean isOpenPath(String servletPath) {
        return PUBLIC_PATHS.contains(servletPath) || DEVICE_API_PATHS.contains(servletPath);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        if (req.getCharacterEncoding() == null) {
            req.setCharacterEncoding("UTF-8"); // Vietnamese form input
        }
        addSecurityHeaders(res);
        String path = req.getServletPath();

        if (isStatic(path) || PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            // devices authenticate with a header key instead of a login session
            if (DEVICE_API_PATHS.contains(path)) {
                int deviceId = SecurityUtils.findDeviceIdByApiKey(req.getHeader("X-API-Key"));
                if (deviceId <= 0) {
                    sendJsonError(res, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                    return;
                }
                req.setAttribute(DEVICE_ID, deviceId);
                chain.doFilter(request, response);
                return;
            }

            HttpSession session = req.getSession(false);
            User user = session == null ? null : (User) session.getAttribute(LOGIN_USER);
            if (user == null || !new UserDao().isActive(user.getUserId())) {
                if (user != null) {
                    session.invalidate(); // the account was locked while signed in
                }
                if (path.startsWith("/api/")) {
                    sendJsonError(res, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
                } else {
                    res.sendRedirect(req.getContextPath() + "/LoginController");
                }
                return;
            }

            // JSP views are reached only through their controller (forwards skip this filter)
            if (path.endsWith(".jsp")) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (!"GET".equals(req.getMethod()) && !hasValidCsrfToken(req, session)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
                return;
            }

            String resource = RESOURCE_BY_PATH.get(path);
            String action = "GET".equals(req.getMethod()) ? "READ" : "WRITE";
            if (resource != null && !new PermissionDao().hasPermission(user.getUserId(), resource, action)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Permission denied");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException("Security check failed", e);
        }
        res.setHeader("Cache-Control", "no-store"); // the back button must not show data after logout
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private static boolean isStatic(String path) {
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || "/favicon.ico".equals(path);
    }

    private static boolean hasValidCsrfToken(HttpServletRequest req, HttpSession session) {
        String sent = req.getParameter(CSRF_PARAM);
        if (sent == null) {
            sent = req.getHeader(CSRF_HEADER); // fetch() calls send the token as a header
        }
        return SecurityUtils.safeEquals((String) session.getAttribute(CSRF_TOKEN), sent);
    }

    private static void addSecurityHeaders(HttpServletResponse res) {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "same-origin");
    }

    private static void sendJsonError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
