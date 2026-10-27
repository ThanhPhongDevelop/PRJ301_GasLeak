package controller;

import dao.BaseDao;
import dao.CalibrationDao;
import dao.LookupDao;
import form.CalibrationForm;
import form.ProcErrors;
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
import model.Calibration;
import model.PageResult;
import util.CsvWriter;
import util.FormUtils;
import util.WebUtils;

/**
 * Calibrations of a sensor type on a device (corrected = raw * scale + offset). Calibration_Save gives a
 * new calibration the next version of its device and sensor type; editing changes offset, scale and the
 * valid-until date only, because a calibration stays with its device and sensor type.
 */
@WebServlet("/CalibrationController")
public class CalibrationController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "CALIBRATION";

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
                    Map<String, String> form = CalibrationForm.empty();
                    Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
                    Integer typeId = FormUtils.parseInt(req.getParameter("sensorTypeId"));
                    form.put("deviceId", deviceId == null ? "" : String.valueOf(deviceId)); // from a filtered list
                    form.put("sensorTypeId", typeId == null ? "" : String.valueOf(typeId));
                    showForm(req, res, form, new HashMap<String, String>(), null);
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                Calibration c = id == null ? null : new CalibrationDao().findById(id);
                if (c == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, CalibrationForm.of(c), new HashMap<String, String>(), c);
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
        CalibrationDao dao = new CalibrationDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = CalibrationForm.read(req);
                Integer id = FormUtils.parseInt(form.get("id"));
                Calibration before = null;
                if (id != null && id > 0) {
                    before = dao.findById(id);
                    if (before == null) {
                        WebUtils.flash(req, "error", "That calibration no longer exists.");
                        res.sendRedirect(req.getContextPath() + "/CalibrationController");
                        return;
                    }
                    // a calibration stays with its device and sensor type, whatever the request says
                    form.put("deviceId", String.valueOf(before.getDeviceId()));
                    form.put("sensorTypeId", String.valueOf(before.getSensorTypeId()));
                }
                Map<String, String> errors = new HashMap<String, String>();
                Calibration c = CalibrationForm.validate(form, errors);
                int savedId = 0;
                if (errors.isEmpty()) {
                    try {
                        savedId = dao.save(actor, c);
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) {
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/CalibrationController");
                            return;
                        }
                        errors.put(field, e.getMessage());
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors, before);
                    return;
                }
                if (before != null) {
                    WebUtils.flash(req, "ok", "Calibration saved.");
                } else {
                    Calibration added = dao.findById(savedId);
                    WebUtils.flash(req, "ok", added == null ? "Calibration added." : "Calibration added as version "
                            + added.getVersion() + " for " + added.getDeviceCode() + ", " + added.getSensorTypeName() + ".");
                }
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error",
                            deleted ? "Calibration deleted." : "That calibration no longer exists.");
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
        res.sendRedirect(req.getContextPath() + "/CalibrationController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
        Integer typeId = FormUtils.parseInt(req.getParameter("sensorTypeId"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        CalibrationDao dao = new CalibrationDao();
        PageResult<Calibration> result = dao.search(deviceId, typeId, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(deviceId, typeId, result.getTotalPages(), PAGE_SIZE);
        }
        LookupDao lookup = new LookupDao();
        req.setAttribute("result", result);
        req.setAttribute("deviceId", deviceId == null ? "" : String.valueOf(deviceId));
        req.setAttribute("sensorTypeId", typeId == null ? "" : String.valueOf(typeId));
        req.setAttribute("devices", lookup.devices());
        req.setAttribute("sensorTypes", lookup.sensorTypes());
        req.getRequestDispatcher("/calibration_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors, Calibration saved) throws SQLException, ServletException, IOException {
        LookupDao lookup = new LookupDao();
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("saved", saved); // null when adding: device and sensor type can be chosen
        req.setAttribute("devices", lookup.devices());
        req.setAttribute("sensorTypes", lookup.sensorTypes());
        req.getRequestDispatcher("/calibration_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<Calibration> items = new CalibrationDao().listForExport(
                FormUtils.parseInt(req.getParameter("deviceId")), FormUtils.parseInt(req.getParameter("sensorTypeId")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"calibrations.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("id", "device", "sensor_type", "unit", "version", "offset", "scale", "valid_until",
                "expired", "created_by"));
        for (Calibration c : items) {
            out.write(CsvWriter.row(c.getCalibId(), c.getDeviceCode(), c.getSensorTypeName(), c.getUnit(),
                    c.getVersion(), c.getOffsetVal(), c.getScaleVal(), c.getValidUntil(), c.isExpired() ? "yes" : "no",
                    c.getCreatedByName()));
        }
        out.flush();
    }
}
