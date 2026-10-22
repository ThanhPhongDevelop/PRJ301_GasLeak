package controller;

import dao.AlertDao;
import dao.BaseDao;
import dao.GasEventDao;
import dao.LookupDao;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.GasEvent;
import model.Labels;
import model.PageResult;
import model.User;
import util.CsvWriter;
import util.FormUtils;
import util.WebUtils;

/**
 * Sessions ("events"). The four operations: devices add sessions through the API, people view them
 * here, and editing or deleting works on the human label (GasEventLabel), because the measured
 * session itself is append-only.
 */
@WebServlet("/GasEventController")
public class GasEventController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 15;

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
        Long sessionId = parseLong(req.getParameter("id"));
        if (sessionId == null) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        try {
            GasEventDao dao = new GasEventDao();
            if ("label".equals(action)) {
                String label = FormUtils.trim(req.getParameter("label"));
                if (!Labels.isValid(label)) {
                    WebUtils.flash(req, "error", "Choose one of the six labels.");
                } else {
                    dao.setLabel(user.getUserId(), sessionId, label);
                    WebUtils.flash(req, "ok", "Label saved: " + Labels.displayNameOf(label) + ".");
                }
            } else if ("unlabel".equals(action)) {
                boolean removed = dao.removeLabel(user.getUserId(), sessionId);
                WebUtils.flash(req, removed ? "ok" : "error",
                        removed ? "Label removed." : "This session had no label to remove.");
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            if (!BaseDao.isBusinessRule(e)) {
                throw new ServletException(e);
            }
            WebUtils.flash(req, "error", e.getMessage());
        }
        res.sendRedirect(req.getContextPath() + "/GasEventController?action=view&id=" + sessionId);
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
        String ruleLabel = labelFilter(req.getParameter("rule"));
        String humanLabel = humanFilter(req.getParameter("human"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        GasEventDao dao = new GasEventDao();
        PageResult<GasEvent> result = dao.search(deviceId, ruleLabel, humanLabel, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(deviceId, ruleLabel, humanLabel, result.getTotalPages(), PAGE_SIZE);
        }
        req.setAttribute("result", result);
        req.setAttribute("devices", new LookupDao().devices());
        req.setAttribute("labels", Labels.ALL);
        req.setAttribute("deviceId", deviceId == null ? "" : String.valueOf(deviceId));
        req.setAttribute("rule", ruleLabel);
        req.setAttribute("human", humanLabel);
        req.getRequestDispatcher("/gas_event_list.jsp").forward(req, res);
    }

    private void showDetail(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Long id = parseLong(req.getParameter("id"));
        GasEvent session = id == null ? null : new GasEventDao().findById(id);
        if (session == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("event", session);
        req.setAttribute("alerts", new AlertDao().forSession(session.getSessionId()));
        req.setAttribute("labels", Labels.ALL);
        req.getRequestDispatcher("/gas_event_detail.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"gas_sessions.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("session_id", "device", "seq", "measured_at_utc", "mq2_raw", "compensated_score",
                "mq135_raw", "mq135_compensated", "temp_c", "rh_pct", "flame", "rule_label", "ml_label",
                "ml_confidence", "human_label"));
        for (GasEvent s : new GasEventDao().listForExport(FormUtils.parseInt(req.getParameter("deviceId")),
                labelFilter(req.getParameter("rule")), humanFilter(req.getParameter("human")))) {
            out.write(CsvWriter.row(s.getSessionId(), s.getDeviceCode(), s.getDeviceSeq(), s.getMeasuredAt(),
                    s.getMq2Raw(), s.getCompensatedScore(), s.getMq135Raw(), s.getMq135Compensated(), s.getTempC(),
                    s.getRhPct(), s.getFlameDetected(), s.getRuleLabel(), s.getMlLabel(), s.getMlConfidence(),
                    s.getHumanLabel()));
        }
        out.flush();
    }

    private static String labelFilter(String value) {
        String label = FormUtils.trim(value);
        return Labels.isValid(label) ? label : "";
    }

    private static String humanFilter(String value) {
        String label = FormUtils.trim(value);
        return Labels.isValid(label) || GasEventDao.UNLABELLED.equals(label) ? label : "";
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
