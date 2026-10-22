package controller;

import dao.BaseDao;
import dao.UserDao;
import form.UserForm;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.PageResult;
import model.User;
import util.FormUtils;
import util.SecurityUtils;
import util.WebUtils;

/**
 * Account administration: list, create an account, change role, lock or unlock (locking replaces
 * deleting), reset a password. A generated password is shown once in the next page's notice and
 * stored only as a BCrypt hash.
 */
@WebServlet("/UserController")
public class UserController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        showList(req, res, UserForm.empty(), new HashMap<String, String>());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        User me = WebUtils.currentUser(req);
        String action = FormUtils.trim(req.getParameter("action"));
        UserDao dao = new UserDao();
        try {
            if ("create".equals(action)) {
                create(req, res, me, dao);
                return;
            }
            Integer userId = FormUtils.parseInt(req.getParameter("userId"));
            if (userId == null) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if ("changeRole".equals(action)) {
                dao.changeRole(me.getUserId(), userId, FormUtils.trim(req.getParameter("roleName")));
                WebUtils.flash(req, "ok", "Role changed. It applies from the user's next request.");
            } else if ("setStatus".equals(action)) {
                String status = FormUtils.trim(req.getParameter("status"));
                dao.setStatus(me.getUserId(), userId, status);
                WebUtils.flash(req, "ok", "LOCKED".equals(status) ? "Account locked." : "Account unlocked.");
            } else if ("resetPassword".equals(action)) {
                if (userId == me.getUserId()) {
                    WebUtils.flash(req, "error", "Change your own password under My account.");
                } else {
                    String temporary = SecurityUtils.newTemporaryPassword();
                    dao.setPassword(me.getUserId(), userId, temporary);
                    WebUtils.flash(req, "ok", "Password reset. Temporary password: " + temporary
                            + " (shown only now). Give it to the user privately and ask them to change it "
                            + "under My account.");
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            if (!BaseDao.isBusinessRule(e)) {
                throw new ServletException(e);
            }
            WebUtils.flash(req, "error", e.getMessage()); // message written in the stored procedure
        }
        res.sendRedirect(req.getContextPath() + "/UserController");
    }

    private void create(HttpServletRequest req, HttpServletResponse res, User me, UserDao dao)
            throws SQLException, ServletException, IOException {
        Map<String, String> form = UserForm.read(req);
        Map<String, String> errors = new HashMap<String, String>();
        UserForm.Account account = UserForm.validate(form, errors, dao.listRoleNames());
        if (errors.isEmpty() && dao.create(me.getUserId(), account.getUsername(), account.getPassword(),
                account.getFullName(), account.getRoleName()) <= 0) {
            errors.put("username", "This username is already taken.");
        }
        if (!errors.isEmpty()) {
            showList(req, res, form, errors);
            return;
        }
        WebUtils.flash(req, "ok", account.isGenerated()
                ? "Account " + account.getUsername() + " created. Temporary password: " + account.getPassword()
                        + " (shown only now). Give it to the user privately and ask them to change it."
                : "Account " + account.getUsername() + " created.");
        res.sendRedirect(req.getContextPath() + "/UserController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res, Map<String, String> newUser,
            Map<String, String> newUserErrors) throws ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        String role = FormUtils.trim(req.getParameter("role"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        try {
            UserDao dao = new UserDao();
            PageResult<User> result = dao.search(keyword, role, page, PAGE_SIZE);
            Map<String, String> roles = new LinkedHashMap<String, String>();
            for (String name : dao.listRoleNames()) {
                roles.put(name, User.displayNameOf(name));
            }
            req.setAttribute("result", result);
            req.setAttribute("roles", roles);
            req.setAttribute("q", keyword);
            req.setAttribute("role", role);
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.setAttribute("newUser", newUser);
        req.setAttribute("newUserErrors", newUserErrors);
        req.getRequestDispatcher("/user_list.jsp").forward(req, res);
    }
}
