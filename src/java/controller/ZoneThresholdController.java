package controller;

import dao.BaseDao;
import dao.LookupDao;
import dao.ZoneThresholdDao;
import form.ProcErrors;
import form.ZoneThresholdForm;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.PageResult;
import model.ZoneThreshold;
import util.CsvWriter;
import util.FormUtils;
import util.ViewFunctions;
import util.WebUtils;

/**
 * Warning and alarm levels per kitchen zone and sensor type, used by the rule engine.
 * A zone keeps one pair of levels per sensor type: ZoneThreshold_Upsert replaces the pair when it
 * already exists. Editing changes only the two levels; the zone and the sensor type stay.
 */
@WebServlet("/ZoneThresholdController")
public class ZoneThresholdController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "ZONE_THRESHOLD";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String action = FormUtils.trim(req.getParameter("action"));
        try {
            if ("new".equals(action) || "edit".equals(action)) {
                if (!WebUtils.can(req, RESOURCE, "WRITE")) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                if ("new".equals(action)) {
                    Map<String, String> form = ZoneThresholdForm.empty();
                    // "Add levels" from a filtered list starts with that zone and sensor type chosen
                    Integer zoneId = FormUtils.parseInt(req.getParameter("zoneId"));
                    Integer typeId = FormUtils.parseInt(req.getParameter("sensorTypeId"));
                    form.put("kitchenZoneId", zoneId == null ? "" : String.valueOf(zoneId));
                    form.put("sensorTypeId", typeId == null ? "" : String.valueOf(typeId));
                    showForm(req, res, form, new HashMap<String, String>());
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                ZoneThreshold t = id == null ? null : new ZoneThresholdDao().findById(id);
                if (t == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, ZoneThresholdForm.of(t), new HashMap<String, String>());
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
        int actor = WebUtils.currentUser(req).getUserId();
        String action = FormUtils.trim(req.getParameter("action"));
        ZoneThresholdDao dao = new ZoneThresholdDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = ZoneThresholdForm.read(req);
                Integer id = FormUtils.parseInt(form.get("id"));
                if (id != null && id > 0) {
                    ZoneThreshold before = dao.findById(id);
                    if (before == null) {
                        WebUtils.flash(req, "error", "Those levels no longer exist.");
                        res.sendRedirect(req.getContextPath() + "/ZoneThresholdController");
                        return;
                    }
                    // saved levels keep their zone and sensor type, whatever the request says
                    form.put("kitchenZoneId", String.valueOf(before.getKitchenZoneId()));
                    form.put("sensorTypeId", String.valueOf(before.getSensorTypeId()));
                }
                Map<String, String> errors = new HashMap<String, String>();
                ZoneThreshold t = ZoneThresholdForm.validate(form, errors);
                if (errors.isEmpty()) {
                    try {
                        dao.save(actor, t.getKitchenZoneId(), t.getSensorTypeId(), t.getWarnLevel(), t.getAlarmLevel());
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) {
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/ZoneThresholdController");
                            return;
                        }
                        errors.put(field, e.getMessage());
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors);
                    return;
                }
                WebUtils.flash(req, "ok", "Levels saved. The rules use them from the next session.");
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error", deleted
                            ? "Levels deleted. The rules now use their default levels for this sensor in this zone."
                            : "Those levels no longer exist.");
                } catch (SQLException e) {
                    if (!BaseDao.isBusinessRule(e)) {
                        throw e;
                    }
                    WebUtils.flash(req, "error", e.getMessage());
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/ZoneThresholdController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Integer zoneId = FormUtils.parseInt(req.getParameter("zoneId"));
        Integer typeId = FormUtils.parseInt(req.getParameter("sensorTypeId"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        ZoneThresholdDao dao = new ZoneThresholdDao();
        PageResult<ZoneThreshold> result = dao.search(zoneId, typeId, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(zoneId, typeId, result.getTotalPages(), PAGE_SIZE);
        }
        LookupDao lookup = new LookupDao();
        req.setAttribute("result", result);
        req.setAttribute("zoneId", zoneId == null ? "" : String.valueOf(zoneId));
        req.setAttribute("sensorTypeId", typeId == null ? "" : String.valueOf(typeId));
        req.setAttribute("zones", lookup.zones());
        req.setAttribute("sensorTypes", lookup.sensorTypes());
        req.getRequestDispatcher("/zone_threshold_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors) throws SQLException, ServletException, IOException {
        LookupDao lookup = new LookupDao();
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("zones", lookup.zones());
        req.setAttribute("sensorTypes", lookup.sensorTypes());
        req.getRequestDispatcher("/zone_threshold_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<ZoneThreshold> items = new ZoneThresholdDao().listForExport(
                FormUtils.parseInt(req.getParameter("zoneId")), FormUtils.parseInt(req.getParameter("sensorTypeId")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"zone_thresholds.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("id", "kitchen_zone", "home", "sensor_type", "unit", "warn_level", "alarm_level",
                "updated_by", "updated_at_utc"));
        for (ZoneThreshold t : items) {
            out.write(CsvWriter.row(t.getZoneThresholdId(), t.getZoneName(), t.getHomeName(), t.getSensorTypeName(),
                    t.getUnit(), t.getWarnLevel(), t.getAlarmLevel(), t.getUpdatedByName(),
                    ViewFunctions.time(t.getUpdatedAt())));
        }
        out.flush();
    }
}
