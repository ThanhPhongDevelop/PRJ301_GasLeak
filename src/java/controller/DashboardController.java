package controller;

import dao.DashboardDao;
import dao.DeviceDao;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Landing page after login; needs a login but no specific permission. */
@WebServlet("/DashboardController")
public class DashboardController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            req.setAttribute("summary", new DashboardDao().summary());
            req.setAttribute("devices", new DeviceDao().listDevices()); // live panel device picker
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.getRequestDispatcher("/dashboard.jsp").forward(req, res);
    }
}
