package controller;

import dao.BaseDao;
import dao.DeviceDao;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.CommandType;
import model.User;
import util.FormUtils;
import util.WebUtils;

/**
 * Device control: take or release the control lock, and queue actuator commands.
 * Only one person controls a device at a time; every command asks for confirmation in the browser
 * (data-confirm), is checked again by DeviceCommand_Issue and written to the audit log.
 */
@WebServlet("/ControlController")
public class ControlController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            DeviceDao dao = new DeviceDao();
            req.setAttribute("devices", dao.listDevices());
            req.setAttribute("recentCommands", dao.recentCommands(15));
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.setAttribute("commandTypes", CommandType.ALL);
        req.getRequestDispatcher("/control.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        User user = WebUtils.currentUser(req);
        String action = FormUtils.trim(req.getParameter("action"));
        Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
        if (deviceId == null) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        DeviceDao dao = new DeviceDao();
        try {
            if ("acquire".equals(action)) {
                boolean ok = dao.acquireControl(deviceId, user.getUserId());
                WebUtils.flash(req, ok ? "ok" : "error", ok ? "You now control this device."
                        : "Another user controls this device, or it is not active.");
            } else if ("release".equals(action)) {
                boolean ok = dao.releaseControl(deviceId, user.getUserId());
                WebUtils.flash(req, ok ? "ok" : "error", ok ? "Control released." : "You were not controlling this device.");
            } else if ("forceRelease".equals(action)) {
                if (!"ADMIN".equals(user.getRoleName())) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                boolean ok = dao.forceRelease(user.getUserId(), deviceId);
                WebUtils.flash(req, ok ? "ok" : "error", ok ? "Control lock removed." : "Nobody was controlling this device.");
            } else if ("command".equals(action)) {
                String command = FormUtils.trim(req.getParameter("command"));
                if (!CommandType.isValid(command)) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown command");
                    return;
                }
                long commandId = dao.issueCommand(user.getUserId(), deviceId, command);
                WebUtils.flash(req, "ok", "Command #" + commandId + " queued. The device receives it at its next "
                        + "connection; release control when you are done.");
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            if (!BaseDao.isBusinessRule(e)) {
                throw new ServletException(e);
            }
            WebUtils.flash(req, "error", e.getMessage()); // lock held by someone else, open alerts, ...
        }
        Integer returnAlert = FormUtils.parseInt(req.getParameter("returnAlert"));
        res.sendRedirect(req.getContextPath() + (returnAlert == null ? "/ControlController"
                : "/AlertController?action=view&id=" + returnAlert));
    }
}
