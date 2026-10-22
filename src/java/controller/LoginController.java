package controller;

import dao.AuditDao;
import dao.PermissionDao;
import dao.UserDao;
import filter.SecurityFilter;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.User;
import util.FormUtils;
import util.JsonText;
import util.SecurityUtils;

@WebServlet("/LoginController")
public class LoginController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    static final int MAX_FAILED = 5;
    static final int LOCK_MINUTES = 15;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute(SecurityFilter.LOGIN_USER) != null) {
            res.sendRedirect(req.getContextPath() + "/DashboardController");
            return;
        }
        req.getRequestDispatcher("/login.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String username = FormUtils.trim(req.getParameter("username"));
        String password = req.getParameter("password");
        req.setAttribute("username", username);
        if (username.isEmpty() || password == null || password.isEmpty()) {
            fail(req, res, "Enter your username and password.");
            return;
        }
        try {
            AuditDao audit = new AuditDao();
            UserDao users = new UserDao();
            String logged = username.length() > 50 ? username.substring(0, 50) : username;
            // guessing passwords: after MAX_FAILED wrong tries the username is refused for LOCK_MINUTES,
            // even with the right password (counted from the audit log, reset by a successful login)
            if (users.recentFailedLogins(logged, LOCK_MINUTES) >= MAX_FAILED) {
                audit.append(null, "LOGIN_THROTTLED", "USER", null, JsonText.object("username", logged));
                fail(req, res, "Too many failed sign-in attempts. Wait " + LOCK_MINUTES + " minutes and try again.");
                return;
            }
            User user = users.checkLogin(username, password);
            if (user == null) {
                audit.append(null, "LOGIN_FAILED", "USER", null, JsonText.object("username", logged));
                fail(req, res, "Wrong username or password.");
                return;
            }
            if (!"ACTIVE".equals(user.getStatus())) {
                audit.append(user.getUserId(), "LOGIN_BLOCKED", "USER", user.getUserId(), null);
                fail(req, res, "This account is locked. Ask an administrator to unlock it.");
                return;
            }
            user.setPermissions(new PermissionDao().loadPermissions(user.getUserId()));
            // a new session id after login makes a session id planted before login useless
            HttpSession old = req.getSession(false);
            if (old != null) {
                old.invalidate();
            }
            HttpSession session = req.getSession(true);
            session.setAttribute(SecurityFilter.LOGIN_USER, user);
            session.setAttribute(SecurityFilter.CSRF_TOKEN, SecurityUtils.newToken());
            audit.append(user.getUserId(), "LOGIN", "USER", user.getUserId(), null);
            res.sendRedirect(req.getContextPath() + "/DashboardController");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }

    private static void fail(HttpServletRequest req, HttpServletResponse res, String message)
            throws ServletException, IOException {
        req.setAttribute("error", message);
        req.getRequestDispatcher("/login.jsp").forward(req, res);
    }
}
