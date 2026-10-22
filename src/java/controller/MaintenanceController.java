package controller;

import dao.AlertDao;
import dao.BaseDao;
import dao.MaintenanceDao;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Alert;
import model.MaintenanceTicket;
import model.PageResult;
import model.User;
import util.CsvWriter;
import util.FormUtils;
import util.WebUtils;

/** Maintenance tickets: list (anyone with MAINTENANCE READ), open from an alert and close (WRITE). */
@WebServlet("/MaintenanceController")
public class MaintenanceController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 15;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String status = statusFilter(req.getParameter("status"));
        boolean mine = "1".equals(req.getParameter("mine"));
        String keyword = FormUtils.trim(req.getParameter("q"));
        Integer assignedTo = mine ? WebUtils.currentUser(req).getUserId() : null;
        try {
            MaintenanceDao dao = new MaintenanceDao();
            if ("export".equals(req.getParameter("action"))) {
                res.setContentType("text/csv;charset=UTF-8");
                res.setHeader("Content-Disposition", "attachment; filename=\"maintenance_tickets.csv\"");
                PrintWriter out = res.getWriter();
                out.write('﻿');
                out.write(CsvWriter.row("ticket_id", "status", "device", "alert_id", "alert_label", "assigned_to",
                        "opened_at_utc", "closed_at_utc", "note"));
                for (MaintenanceTicket t : dao.listForExport(status, assignedTo, keyword)) {
                    out.write(CsvWriter.row(t.getTicketId(), t.getStatus(), t.getDeviceCode(), t.getAlertId(),
                            t.getAlertLabel(), t.getAssigneeName(), t.getOpenedAt(), t.getClosedAt(), t.getNote()));
                }
                out.flush();
                return;
            }
            int page = FormUtils.parsePage(req.getParameter("page"));
            PageResult<MaintenanceTicket> result = dao.search(status, assignedTo, keyword, page, PAGE_SIZE);
            if (result.getItems().isEmpty() && page > result.getTotalPages()) {
                result = dao.search(status, assignedTo, keyword, result.getTotalPages(), PAGE_SIZE);
            }
            req.setAttribute("result", result);
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.setAttribute("status", status);
        req.setAttribute("mine", mine);
        req.setAttribute("q", keyword);
        req.getRequestDispatcher("/maintenance_list.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        User user = WebUtils.currentUser(req);
        String action = FormUtils.trim(req.getParameter("action"));
        String note = FormUtils.trim(req.getParameter("note"));
        if (note.length() > 400) {
            note = note.substring(0, 400);
        }
        try {
            if ("open".equals(action)) {
                Integer alertId = FormUtils.parseInt(req.getParameter("alertId"));
                Alert alert = alertId == null ? null : new AlertDao().findById(alertId);
                if (alert == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                if (note.isEmpty()) {
                    WebUtils.flash(req, "error", "Describe what the technician should check.");
                } else {
                    int ticketId = new MaintenanceDao().open(user.getUserId(), alert.getAlertId(), alert.getDeviceId(),
                            FormUtils.parseInt(req.getParameter("assignedTo")), note);
                    WebUtils.flash(req, "ok", "Maintenance ticket #" + ticketId + " opened.");
                }
                res.sendRedirect(req.getContextPath() + "/AlertController?action=view&id=" + alert.getAlertId());
                return;
            }
            if ("close".equals(action)) {
                Integer ticketId = FormUtils.parseInt(req.getParameter("ticketId"));
                if (ticketId == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                if (note.isEmpty()) {
                    WebUtils.flash(req, "error", "Write what was repaired or checked before closing the ticket.");
                } else {
                    boolean closed = new MaintenanceDao().close(user.getUserId(), ticketId, note);
                    WebUtils.flash(req, closed ? "ok" : "error",
                            closed ? "Ticket #" + ticketId + " closed." : "This ticket is already closed.");
                }
                res.sendRedirect(req.getContextPath() + "/MaintenanceController");
                return;
            }
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
        } catch (SQLException e) {
            if (!BaseDao.isConstraintViolation(e)) {
                throw new ServletException(e);
            }
            WebUtils.flash(req, "error", "The selected technician no longer exists. Pick another one.");
            res.sendRedirect(req.getContextPath() + "/MaintenanceController");
        }
    }

    private static String statusFilter(String value) {
        String status = FormUtils.trim(value);
        return "OPEN".equals(status) || "CLOSED".equals(status) ? status : "";
    }
}
