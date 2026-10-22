package util;

import filter.SecurityFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import model.User;

/** Helpers used by every controller. */
public final class WebUtils {

    public static final String FLASH = "FLASH";
    public static final String FLASH_TYPE = "FLASH_TYPE";

    private WebUtils() {
    }

    public static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (User) session.getAttribute(SecurityFilter.LOGIN_USER);
    }

    public static boolean can(HttpServletRequest req, String resource, String action) {
        User user = currentUser(req);
        return user != null && user.can(resource, action);
    }

    /** One-time message shown at the top of the next page; type is "ok" or "error". */
    public static void flash(HttpServletRequest req, String type, String message) {
        HttpSession session = req.getSession();
        session.setAttribute(FLASH, message);
        session.setAttribute(FLASH_TYPE, type);
    }
}
