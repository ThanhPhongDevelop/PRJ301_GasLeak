package controller;

import dao.AuditDao;
import dao.UserDao;
import filter.SecurityFilter;
import form.PasswordForm;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.User;
import util.SecurityUtils;
import util.WebUtils;

/**
 * My account: every signed-in user sees their account and changes their own password (needs only a
 * login, no permission). After a change the session gets a new id and a new CSRF token.
 */
@WebServlet("/AccountController")
public class AccountController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        show(req, res, new HashMap<String, String>());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if (!"changePassword".equals(req.getParameter("action"))) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            return;
        }
        User me = WebUtils.currentUser(req);
        Map<String, String> form = PasswordForm.read(req);
        Map<String, String> errors = new HashMap<String, String>();
        String newPassword = PasswordForm.validate(form, errors);
        try {
            UserDao dao = new UserDao();
            if (!errors.containsKey("currentPassword") && !dao.verifyPassword(me.getUserId(), form.get("currentPassword"))) {
                errors.put("currentPassword", "This is not your current password.");
                new AuditDao().append(me.getUserId(), "CHANGE_PASSWORD_FAILED", "USER", me.getUserId(), null);
            }
            if (!errors.isEmpty()) {
                show(req, res, errors);
                return;
            }
            dao.setPassword(me.getUserId(), me.getUserId(), newPassword);
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.changeSessionId(); // a session id seen before the change is useless now
        req.getSession().setAttribute(SecurityFilter.CSRF_TOKEN, SecurityUtils.newToken());
        WebUtils.flash(req, "ok", "Password changed. Use the new password next time you sign in.");
        res.sendRedirect(req.getContextPath() + "/AccountController");
    }

    private void show(HttpServletRequest req, HttpServletResponse res, Map<String, String> errors)
            throws ServletException, IOException {
        try {
            req.setAttribute("account", new UserDao().findById(WebUtils.currentUser(req).getUserId()));
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.setAttribute("errors", errors); // passwords are never sent back to the browser
        req.getRequestDispatcher("/account.jsp").forward(req, res);
    }
}
