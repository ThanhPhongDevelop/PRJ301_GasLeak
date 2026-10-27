package controller;

import dao.BaseDao;
import dao.DeviceDao;
import form.DeviceForm;
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
import model.DeviceInfo;
import model.DeviceKey;
import model.PageResult;
import util.CsvWriter;
import util.FormUtils;
import util.ViewFunctions;
import util.WebUtils;

/**
 * Devices (ESP32 boards). GET: list (search, status filter, paging), new, edit, export.
 * POST: save (register a new device or change one), rotateKey, delete.
 * Registering and rotating issue an API key that is shown once in the next page's notice; the
 * database keeps only its SHA-256, so nobody can read it back later. The device code is set once.
 */
@WebServlet("/DeviceController")
public class DeviceController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "DEVICE";

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
                    showForm(req, res, DeviceForm.empty(), new HashMap<String, String>(), null);
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                DeviceInfo device = id == null ? null : new DeviceDao().findDevice(id);
                if (device == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, DeviceForm.of(device), new HashMap<String, String>(), device);
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
        DeviceDao dao = new DeviceDao();
        try {
            if ("save".equals(action)) {
                if (save(req, res, actor, dao)) {
                    return; // the form was shown again with errors, or we already redirected
                }
            } else if ("rotateKey".equals(action) || "delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                if ("rotateKey".equals(action)) {
                    rotateKey(req, actor, dao, id);
                } else {
                    delete(req, actor, dao, id);
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/DeviceController");
    }

    /** True when it already answered (form shown again, or redirect after a vanished device). */
    private boolean save(HttpServletRequest req, HttpServletResponse res, int actor, DeviceDao dao)
            throws SQLException, ServletException, IOException {
        Map<String, String> form = DeviceForm.read(req);
        Integer id = FormUtils.parseInt(form.get("id"));
        boolean creating = id == null || id <= 0;
        DeviceInfo before = null;
        if (creating) {
            form.put("status", "ACTIVE"); // a new device starts active; the status is chosen when editing
        } else {
            before = dao.findDevice(id);
            if (before == null) {
                WebUtils.flash(req, "error", "That device no longer exists.");
                res.sendRedirect(req.getContextPath() + "/DeviceController");
                return true;
            }
            form.put("deviceCode", before.getDeviceCode()); // the code never changes
        }
        Map<String, String> errors = new HashMap<String, String>();
        DeviceInfo d = DeviceForm.validate(form, errors, creating);
        if (errors.isEmpty()) {
            try {
                if (creating) {
                    DeviceKey key = dao.register(actor, d.getDeviceCode(), d.getLocation(), d.getFirmware());
                    WebUtils.flash(req, "ok", "Device " + d.getDeviceCode() + " registered. API key: "
                            + key.getApiKey() + " (shown only now). Copy it into API_KEY in esp32_gasguard.ino "
                            + "before leaving this page: the server keeps only its SHA-256 and cannot show it again.");
                } else {
                    dao.update(actor, before.getDeviceId(), d.getLocation(), d.getFirmware(), d.getStatus());
                    WebUtils.flash(req, "ok", "INACTIVE".equals(d.getStatus())
                            ? "Device saved. It is inactive: its API key is refused until you set it Active again."
                            : "Device saved.");
                }
            } catch (SQLException e) {
                if (!BaseDao.isBusinessRule(e)) {
                    throw e;
                }
                String field = ProcErrors.fieldOf(e);
                if (field == null) {
                    WebUtils.flash(req, "error", e.getMessage());
                    res.sendRedirect(req.getContextPath() + "/DeviceController");
                    return true;
                }
                errors.put(field, e.getMessage()); // for example the code is already taken
            }
        }
        if (!errors.isEmpty()) {
            showForm(req, res, form, errors, before);
            return true;
        }
        return false;
    }

    private void rotateKey(HttpServletRequest req, int actor, DeviceDao dao, int id) throws SQLException {
        DeviceInfo device = dao.findDevice(id);
        if (device == null) {
            WebUtils.flash(req, "error", "That device no longer exists.");
            return;
        }
        try {
            DeviceKey key = dao.rotateKey(actor, id);
            WebUtils.flash(req, "ok", "New API key for " + device.getDeviceCode() + ": " + key.getApiKey()
                    + " (shown only now). The old key stopped working at once: put the new one in API_KEY in "
                    + "esp32_gasguard.ino and upload the firmware to the board again.");
        } catch (SQLException e) {
            if (!BaseDao.isBusinessRule(e)) {
                throw e;
            }
            WebUtils.flash(req, "error", e.getMessage());
        }
    }

    private void delete(HttpServletRequest req, int actor, DeviceDao dao, int id) throws SQLException {
        try {
            boolean deleted = dao.delete(actor, id);
            WebUtils.flash(req, deleted ? "ok" : "error",
                    deleted ? "Device deleted." : "That device no longer exists.");
        } catch (SQLException e) {
            if (!BaseDao.isBusinessRule(e)) {
                throw e;
            }
            WebUtils.flash(req, "error", e.getMessage()); // it has history: set it INACTIVE instead
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        String status = statusFilter(req.getParameter("status"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        DeviceDao dao = new DeviceDao();
        PageResult<DeviceInfo> result = dao.search(keyword, status, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(keyword, status, result.getTotalPages(), PAGE_SIZE);
        }
        req.setAttribute("result", result);
        req.setAttribute("q", keyword);
        req.setAttribute("status", status);
        req.getRequestDispatcher("/device_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors, DeviceInfo device) throws ServletException, IOException {
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("device", device); // null when registering a new device
        req.getRequestDispatcher("/device_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<DeviceInfo> items = new DeviceDao().listForExport(FormUtils.trim(req.getParameter("q")),
                statusFilter(req.getParameter("status")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"devices.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("id", "device_code", "location", "firmware", "status", "online", "last_seen_utc",
                "kitchen_zones", "controlled_by"));
        for (DeviceInfo d : items) {
            out.write(CsvWriter.row(d.getDeviceId(), d.getDeviceCode(), d.getLocation(), d.getFirmware(),
                    d.getStatus(), d.isOnline() ? "yes" : "no", ViewFunctions.time(d.getLastSeen()), d.getZoneNames(),
                    d.getControlOwnerName()));
        }
        out.flush();
    }

    /** ACTIVE or INACTIVE; anything else means no status filter. */
    private static String statusFilter(String value) {
        String status = FormUtils.trim(value);
        return "ACTIVE".equals(status) || "INACTIVE".equals(status) ? status : "";
    }
}
