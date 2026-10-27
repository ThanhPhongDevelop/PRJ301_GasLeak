package controller;

import dao.BaseDao;
import dao.LookupDao;
import dao.OperatingProfileDao;
import form.OperatingProfileForm;
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
import model.OperatingProfile;
import model.PageResult;
import util.CsvWriter;
import util.FormUtils;
import util.ViewFunctions;
import util.WebUtils;

/**
 * Temperature and humidity compensation profiles, kept as versions per device. Saving always adds a
 * new version, which becomes the one in use; "edit" opens a version with its coefficients filled in and
 * saves the result as the newest. Only older versions can be deleted.
 */
@WebServlet("/OperatingProfileController")
public class OperatingProfileController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "PROFILE";

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
                    Map<String, String> form = OperatingProfileForm.empty();
                    Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
                    form.put("deviceId", deviceId == null ? "" : String.valueOf(deviceId)); // from a filtered list
                    showForm(req, res, form, new HashMap<String, String>(), null);
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                OperatingProfile p = id == null ? null : new OperatingProfileDao().findById(id);
                if (p == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, OperatingProfileForm.of(p), new HashMap<String, String>(), p);
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
        OperatingProfileDao dao = new OperatingProfileDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = OperatingProfileForm.read(req);
                Integer id = FormUtils.parseInt(form.get("id"));
                OperatingProfile basedOn = null;
                if (id != null && id > 0) {
                    basedOn = dao.findById(id); // its device is kept, whatever the form says
                    if (basedOn == null) {
                        WebUtils.flash(req, "error", "That profile version no longer exists.");
                        res.sendRedirect(req.getContextPath() + "/OperatingProfileController");
                        return;
                    }
                }
                Map<String, String> errors = new HashMap<String, String>();
                OperatingProfileForm.Draft draft = OperatingProfileForm.validate(form, errors, basedOn);
                int newId = 0;
                if (errors.isEmpty()) {
                    try {
                        newId = dao.save(actor, draft.getDeviceId(), draft.getModel());
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) {
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/OperatingProfileController");
                            return;
                        }
                        errors.put(field, e.getMessage()); // the device was deleted meanwhile
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors, basedOn);
                    return;
                }
                WebUtils.flash(req, "ok", "Saved as profile version #" + newId
                        + ". It is now the one in use for this device: new sessions and the dashboard use it at once.");
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error",
                            deleted ? "Profile version deleted." : "That profile version no longer exists.");
                } catch (SQLException e) {
                    if (!BaseDao.isBusinessRule(e)) {
                        throw e;
                    }
                    WebUtils.flash(req, "error", e.getMessage()); // the version in use cannot be deleted
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/OperatingProfileController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Integer deviceId = FormUtils.parseInt(req.getParameter("deviceId"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        OperatingProfileDao dao = new OperatingProfileDao();
        PageResult<OperatingProfile> result = dao.search(deviceId, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(deviceId, result.getTotalPages(), PAGE_SIZE);
        }
        req.setAttribute("result", result);
        req.setAttribute("deviceId", deviceId == null ? "" : String.valueOf(deviceId));
        req.setAttribute("devices", new LookupDao().devices());
        req.getRequestDispatcher("/operating_profile_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors, OperatingProfile basedOn) throws SQLException, ServletException, IOException {
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("basedOn", basedOn); // null for a device's first profile
        req.setAttribute("devices", new LookupDao().devices());
        req.getRequestDispatcher("/operating_profile_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<OperatingProfile> items = new OperatingProfileDao().listForExport(
                FormUtils.parseInt(req.getParameter("deviceId")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"compensation_profiles.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("profile_id", "device", "in_use", "source", "summary", "updated_by", "updated_at_utc",
                "params_json"));
        for (OperatingProfile p : items) {
            out.write(CsvWriter.row(p.getProfileId(), p.getDeviceCode(), p.isCurrent() ? "yes" : "no", p.getSource(),
                    p.getSummary(), p.getUpdatedByName(), ViewFunctions.time(p.getUpdatedAt()), p.getParamsJson()));
        }
        out.flush();
    }
}
