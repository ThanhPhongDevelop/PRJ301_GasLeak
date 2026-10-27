package controller;

import dao.BaseDao;
import dao.SensorTypeDao;
import form.ProcErrors;
import form.SensorTypeForm;
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
import model.SensorType;
import util.CsvWriter;
import util.FormUtils;
import util.WebUtils;

/**
 * Sensor types and their valid range (readings outside it raise SENSOR_FAULT). The four built-in
 * types are found by name by the rules: their unit and range can change, their name cannot, and they
 * cannot be deleted. A type in use by sensors, levels or calibrations cannot be deleted either.
 */
@WebServlet("/SensorTypeController")
public class SensorTypeController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "SENSOR_TYPE";

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
                    showForm(req, res, SensorTypeForm.empty(), new HashMap<String, String>());
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                SensorType type = id == null ? null : new SensorTypeDao().findById(id);
                if (type == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, SensorTypeForm.of(type), new HashMap<String, String>());
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
        SensorTypeDao dao = new SensorTypeDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = SensorTypeForm.read(req);
                Map<String, String> errors = new HashMap<String, String>();
                SensorType type = SensorTypeForm.validate(form, errors);
                if (errors.isEmpty()) {
                    try {
                        dao.save(actor, type);
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) { // the type was deleted meanwhile
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/SensorTypeController");
                            return;
                        }
                        errors.put(field, e.getMessage()); // name taken, or a built-in name changed
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors);
                    return;
                }
                WebUtils.flash(req, "ok", type.getSensorTypeId() > 0 ? "Sensor type saved." : "Sensor type added.");
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error",
                            deleted ? "Sensor type deleted." : "That sensor type no longer exists.");
                } catch (SQLException e) {
                    if (!BaseDao.isBusinessRule(e)) {
                        throw e;
                    }
                    WebUtils.flash(req, "error", e.getMessage()); // built in, or still in use
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/SensorTypeController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        SensorTypeDao dao = new SensorTypeDao();
        PageResult<SensorType> result = dao.search(keyword, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(keyword, result.getTotalPages(), PAGE_SIZE);
        }
        req.setAttribute("result", result);
        req.setAttribute("q", keyword);
        req.getRequestDispatcher("/sensor_type_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors) throws SQLException, ServletException, IOException {
        Integer id = FormUtils.parseInt(form.get("id"));
        SensorType saved = id == null ? null : new SensorTypeDao().findById(id);
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("builtIn", saved != null && saved.isBuiltIn()); // the name field becomes read-only
        req.getRequestDispatcher("/sensor_type_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<SensorType> items = new SensorTypeDao().listForExport(FormUtils.trim(req.getParameter("q")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"sensor_types.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("id", "name", "unit", "min_valid", "max_valid", "built_in", "sensors", "zone_levels",
                "calibrations"));
        for (SensorType t : items) {
            out.write(CsvWriter.row(t.getSensorTypeId(), t.getName(), t.getUnit(), t.getMinValid(), t.getMaxValid(),
                    t.isBuiltIn() ? "yes" : "no", t.getSensorCount(), t.getThresholdCount(), t.getCalibrationCount()));
        }
        out.flush();
    }
}
