package controller;

import dao.AuditDao;
import dao.DeviceDao;
import filter.SecurityFilter;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.User;

@WebServlet("/LogoutController")
public class LogoutController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            User user = (User) session.getAttribute(SecurityFilter.LOGIN_USER);
            if (user != null) {
                try {
                    new DeviceDao().releaseAllFor(user.getUserId()); // nobody stays locked out of a device
                    new AuditDao().append(user.getUserId(), "LOGOUT", "USER", user.getUserId(), null);
                } catch (SQLException e) {
                    log("Could not release devices or write the logout audit entry", e);
                }
            }
            session.invalidate(); // drop the login state
        }
        res.sendRedirect(req.getContextPath() + "/LoginController");
    }
}
