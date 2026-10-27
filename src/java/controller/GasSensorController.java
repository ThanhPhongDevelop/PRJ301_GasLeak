package controller;

import dao.BaseDao;
import dao.GasSensorDao;
import dao.LookupDao;
import form.GasSensorForm;
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
import model.GasSensor;
import model.PageResult;
import util.CsvWriter;
import util.FormUtils;
import util.WebUtils;

/**
 * Gas sensors: which sensor is wired to which device, where it is installed and on which pin.
 * GET: list (search, device, zone and status filters, paging), new, edit, export. POST: save, delete.
 * GasSensor_Save and GasSensor_Delete check the data again and write the audit log.
 */
@WebServlet("/GasSensorController")
public class GasSensorController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "GAS_SENSOR";

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
                    showForm(req, res, GasSensorForm.empty(), new HashMap<String, String>());
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                GasSensor sensor = id == null ? null : new GasSensorDao().findById(id);
                if (sensor == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, GasSensorForm.of(sensor), new HashMap<String, String>());
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
        GasSensorDao dao = new GasSensorDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = GasSensorForm.read(req);
                Map<String, String> errors = new HashMap<String, String>();
                GasSensor sensor = GasSensorForm.validate(form, errors);
                if (errors.isEmpty()) {
                    try {
                        dao.save(actor, sensor);
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) { // the sensor was deleted meanwhile
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/GasSensorController");
                            return;
                        }
                        errors.put(field, e.getMessage()); // for example the code is already taken
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors);
                    return;
                }
                WebUtils.flash(req, "ok", sensor.getGasSensorId() > 0 ? "Sensor saved." : "Sensor added.");
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error",
                            deleted ? "Sensor deleted." : "That sensor no longer exists.");
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
        res.sendRedirect(req.getContextPath() + "/GasSensorController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
        Integer zoneId = FormUtils.parseInt(req.getParameter("zoneId"));
        String status = statusFilter(req.getParameter("status"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        GasSensorDao dao = new GasSensorDao();
        PageResult<GasSensor> result = dao.search(keyword, deviceId, zoneId, status, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(keyword, deviceId, zoneId, status, result.getTotalPages(), PAGE_SIZE);
        }
        LookupDao lookup = new LookupDao();
        req.setAttribute("result", result);
        req.setAttribute("q", keyword);
        req.setAttribute("deviceId", deviceId == null ? "" : String.valueOf(deviceId));
        req.setAttribute("zoneId", zoneId == null ? "" : String.valueOf(zoneId));
        req.setAttribute("status", status);
        req.setAttribute("devices", lookup.devices());
        req.setAttribute("zones", lookup.zones());
        req.getRequestDispatcher("/gas_sensor_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors) throws SQLException, ServletException, IOException {
        LookupDao lookup = new LookupDao();
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("devices", lookup.devices());
        req.setAttribute("sensorTypes", lookup.sensorTypes());
        req.setAttribute("zones", lookup.zones());
        req.getRequestDispatcher("/gas_sensor_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<GasSensor> items = new GasSensorDao().listForExport(FormUtils.trim(req.getParameter("q")),
                FormUtils.parseInt(req.getParameter("deviceId")), FormUtils.parseInt(req.getParameter("zoneId")),
                statusFilter(req.getParameter("status")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"gas_sensors.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("id", "sensor_code", "device", "sensor_type", "unit", "kitchen_zone", "pin",
                "installed_at", "status"));
        for (GasSensor s : items) {
            out.write(CsvWriter.row(s.getGasSensorId(), s.getSensorCode(), s.getDeviceCode(), s.getSensorTypeName(),
                    s.getUnit(), s.getZoneName(), s.getPinLabel(), s.getInstalledAt(), s.getStatus()));
        }
        out.flush();
    }

    /** ACTIVE or INACTIVE; anything else means no status filter. */
    private static String statusFilter(String value) {
        String status = FormUtils.trim(value);
        return "ACTIVE".equals(status) || "INACTIVE".equals(status) ? status : "";
    }
}
