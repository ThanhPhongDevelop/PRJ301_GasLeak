package controller;

import dao.AlertDao;
import dao.DeviceDao;
import dao.GasEventDao;
import dao.LookupDao;
import dao.MaintenanceDao;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Alert;
import model.Labels;
import model.PageResult;
import model.User;
import util.CsvWriter;
import util.FormUtils;
import util.WebUtils;

/** Alert list and detail; acknowledge, and "confirm the incident was handled" (resolve). */
@WebServlet("/AlertController")
public class AlertController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 15;
    private static final List<String> STATUSES = Arrays.asList("UNRESOLVED", "OPEN", "ACKNOWLEDGED", "RESOLVED");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String action = FormUtils.trim(req.getParameter("action"));
        try {
            if ("view".equals(action)) {
                showDetail(req, res);
            } else if ("export".equals(action)) {
                exportCsv(req, res);
            } else {
                showList(req, res);
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        User user = WebUtils.currentUser(req);
        String action = FormUtils.trim(req.getParameter("action"));
        Integer alertId = FormUtils.parseInt(req.getParameter("id"));
        if (alertId == null) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        try {
            AlertDao dao = new AlertDao();
            if ("acknowledge".equals(action)) {
                boolean changed = dao.acknowledge(user.getUserId(), alertId);
                WebUtils.flash(req, changed ? "ok" : "error",
                        changed ? "Alert acknowledged: others can see someone is on it." : "Only open alerts can be acknowledged.");
            } else if ("resolve".equals(action)) {
                String note = FormUtils.trim(req.getParameter("note"));
                if (note.isEmpty()) {
                    WebUtils.flash(req, "error", "Describe what was done before marking the incident as handled.");
                } else {
                    boolean changed = dao.resolve(user.getUserId(), alertId, note.length() > 400 ? note.substring(0, 400) : note);
                    WebUtils.flash(req, changed ? "ok" : "error",
                            changed ? "Incident marked as handled." : "This alert is already resolved.");
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/AlertController?action=view&id=" + alertId);
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String status = statusFilter(req.getParameter("status"));
        String label = labelFilter(req.getParameter("label"));
        String source = sourceFilter(req.getParameter("source"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        AlertDao dao = new AlertDao();
        PageResult<Alert> result = dao.search(status, label, source, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(status, label, source, result.getTotalPages(), PAGE_SIZE);
        }
        req.setAttribute("result", result);
        req.setAttribute("status", status);
        req.setAttribute("label", label);
        req.setAttribute("source", source);
        req.setAttribute("statuses", STATUSES);
        req.setAttribute("labels", Labels.ALL);
        req.getRequestDispatcher("/alert_list.jsp").forward(req, res);
    }

    private void showDetail(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Integer id = FormUtils.parseInt(req.getParameter("id"));
        AlertDao dao = new AlertDao();
        Alert alert = id == null ? null : dao.findById(id);
        if (alert == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("alert", alert);
        req.setAttribute("evidence", dao.evidence(alert.getAlertId()));
        req.setAttribute("tickets", new MaintenanceDao().forAlert(alert.getAlertId()));
        if (alert.getSessionId() != null) {
            req.setAttribute("event", new GasEventDao().findById(alert.getSessionId()));
        }
        req.setAttribute("episode", new GasEventDao().episode(alert.getDeviceId(), alert.getLabel(),
                alert.getCreatedAt(), 20));
        DeviceDao devices = new DeviceDao();
        Integer valveId = devices.valveDeviceFor(alert.getDeviceId());
        if (valveId != null) {
            req.setAttribute("valve", devices.findDevice(valveId));
        }
        if (WebUtils.can(req, "MAINTENANCE", "WRITE")) {
            req.setAttribute("technicians", new LookupDao().technicians());
        }
        req.getRequestDispatcher("/alert_detail.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"alerts.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("alert_id", "created_at_utc", "device", "session_id", "source", "label", "severity",
                "risk_score", "status", "evidence_items", "tickets"));
        for (Alert a : new AlertDao().listForExport(statusFilter(req.getParameter("status")),
                labelFilter(req.getParameter("label")), sourceFilter(req.getParameter("source")))) {
            out.write(CsvWriter.row(a.getAlertId(), a.getCreatedAt(), a.getDeviceCode(), a.getSessionId(),
                    a.getSource(), a.getLabel(), a.getSeverity(), a.getRiskScore(), a.getStatus(),
                    a.getEvidenceCount(), a.getTicketCount()));
        }
        out.flush();
    }

    private static String statusFilter(String value) {
        String status = FormUtils.trim(value);
        return STATUSES.contains(status) ? status : "";
    }

    private static String labelFilter(String value) {
        String label = FormUtils.trim(value);
        return Labels.isValid(label) ? label : "";
    }

    private static String sourceFilter(String value) {
        String source = FormUtils.trim(value);
        return "RULE".equals(source) || "ML".equals(source) ? source : "";
    }
}
