package controller;

import dao.AuditDao;
import dao.BaseDao;
import dao.KitchenZoneDao;
import dao.LookupDao;
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
import model.KitchenZone;
import model.PageResult;
import model.User;
import util.CsvWriter;
import util.FormUtils;
import util.JsonText;
import util.WebUtils;

/**
 * Reference CRUD for the team. GET: list (search, filter, paging), new, edit, export.
 * POST: create, update, delete. SecurityFilter already checked READ for GET and WRITE for POST.
 */
@WebServlet("/KitchenZoneController")
public class KitchenZoneController extends HttpServlet {

    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "KITCHENZONE";
    private static final String[] FIELDS = {"id", "zoneName", "homeId", "thresholdPpm", "valveDeviceId"};

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
                    showForm(req, res, emptyForm(), new HashMap<String, String>());
                } else {
                    showEdit(req, res);
                }
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
        KitchenZoneDao dao = new KitchenZoneDao();
        AuditDao audit = new AuditDao();
        try {
            if ("delete".equals(action)) {
                delete(req, user, dao, audit);
                res.sendRedirect(req.getContextPath() + "/KitchenZoneController");
                return;
            }
            if (!"create".equals(action) && !"update".equals(action)) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
            Map<String, String> form = readForm(req);
            Map<String, String> errors = new HashMap<String, String>();
            KitchenZone item = validate(form, errors);
            if (!errors.isEmpty()) {
                showForm(req, res, form, errors);
                return;
            }
            try {
                if ("create".equals(action)) {
                    int newId = dao.insert(item);
                    audit.append(user.getUserId(), "CREATE", RESOURCE, newId,
                            JsonText.object("after", JsonText.raw(item.toJson())));
                    WebUtils.flash(req, "ok", "Kitchen zone created.");
                } else {
                    Integer id = FormUtils.parseInt(form.get("id"));
                    KitchenZone before = id == null ? null : dao.findById(id);
                    if (before == null) {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    item.setId(before.getId());
                    dao.update(item);
                    audit.append(user.getUserId(), "UPDATE", RESOURCE, before.getId(), JsonText.object(
                            "before", JsonText.raw(before.toJson()), "after", JsonText.raw(item.toJson())));
                    WebUtils.flash(req, "ok", "Kitchen zone saved.");
                }
            } catch (SQLException e) {
                if (!BaseDao.isConstraintViolation(e)) {
                    throw e;
                }
                errors.put("homeId", "The selected home or valve device no longer exists. Pick another one.");
                showForm(req, res, form, errors);
                return;
            }
            res.sendRedirect(req.getContextPath() + "/KitchenZoneController");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        Integer homeId = FormUtils.parseInt(req.getParameter("homeId"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        KitchenZoneDao dao = new KitchenZoneDao();
        PageResult<KitchenZone> result = dao.search(keyword, homeId, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(keyword, homeId, result.getTotalPages(), PAGE_SIZE); // page past the end
        }
        req.setAttribute("result", result);
        req.setAttribute("q", keyword);
        req.setAttribute("homeId", homeId == null ? "" : String.valueOf(homeId));
        req.setAttribute("homes", new LookupDao().homes());
        req.getRequestDispatcher("/kitchen_zone_list.jsp").forward(req, res);
    }

    private void showEdit(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        Integer id = FormUtils.parseInt(req.getParameter("id"));
        KitchenZone item = id == null ? null : new KitchenZoneDao().findById(id);
        if (item == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Map<String, String> form = new HashMap<String, String>();
        form.put("id", String.valueOf(item.getId()));
        form.put("zoneName", item.getZoneName());
        form.put("homeId", String.valueOf(item.getHomeId()));
        form.put("thresholdPpm", FormUtils.formatNumber(item.getThresholdPpm()));
        form.put("valveDeviceId", item.getValveDeviceId() == null ? "" : String.valueOf(item.getValveDeviceId()));
        showForm(req, res, form, new HashMap<String, String>());
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors) throws SQLException, ServletException, IOException {
        LookupDao lookup = new LookupDao();
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("homes", lookup.homes());
        req.setAttribute("devices", lookup.devices());
        req.getRequestDispatcher("/kitchen_zone_form.jsp").forward(req, res);
    }

    private void delete(HttpServletRequest req, User user, KitchenZoneDao dao, AuditDao audit) throws SQLException {
        Integer id = FormUtils.parseInt(req.getParameter("id"));
        KitchenZone before = id == null ? null : dao.findById(id);
        if (before == null) {
            WebUtils.flash(req, "error", "That kitchen zone no longer exists.");
            return;
        }
        try {
            dao.delete(before.getId());
        } catch (SQLException e) {
            if (!BaseDao.isConstraintViolation(e)) {
                throw e;
            }
            WebUtils.flash(req, "error", "Remove the sensors and thresholds of this zone before deleting it.");
            return;
        }
        audit.append(user.getUserId(), "DELETE", RESOURCE, before.getId(),
                JsonText.object("before", JsonText.raw(before.toJson())));
        WebUtils.flash(req, "ok", "Kitchen zone deleted.");
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<KitchenZone> items = new KitchenZoneDao().listForExport(
                FormUtils.trim(req.getParameter("q")), FormUtils.parseInt(req.getParameter("homeId")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"kitchen_zones.csv\"");
        PrintWriter out = res.getWriter();
        out.write('\uFEFF'); // byte order mark so Excel reads UTF-8 correctly
        out.write(CsvWriter.row("id", "zone_name", "home", "threshold_ppm", "valve_device"));
        for (KitchenZone zone : items) {
            out.write(CsvWriter.row(zone.getId(), zone.getZoneName(), zone.getHomeName(),
                    zone.getThresholdPpm(), zone.getValveDeviceCode()));
        }
        out.flush();
    }

    private static Map<String, String> emptyForm() {
        Map<String, String> form = new HashMap<String, String>();
        for (String field : FIELDS) {
            form.put(field, "");
        }
        return form;
    }

    private static Map<String, String> readForm(HttpServletRequest req) {
        Map<String, String> form = new HashMap<String, String>();
        for (String field : FIELDS) {
            form.put(field, FormUtils.trim(req.getParameter(field)));
        }
        return form;
    }

    /** Server-side validation; fills errors and returns the parsed item. */
    private static KitchenZone validate(Map<String, String> form, Map<String, String> errors) {
        KitchenZone item = new KitchenZone();
        String name = form.get("zoneName");
        if (name.isEmpty()) {
            errors.put("zoneName", "Enter a zone name.");
        } else if (name.length() > 30) {
            errors.put("zoneName", "Use 30 characters or fewer.");
        }
        item.setZoneName(name);

        Integer homeId = FormUtils.parseInt(form.get("homeId"));
        if (homeId == null) {
            errors.put("homeId", "Choose the home this zone belongs to.");
        } else {
            item.setHomeId(homeId);
        }

        Double threshold = FormUtils.parseDouble(form.get("thresholdPpm"));
        if (threshold == null || threshold <= 0 || threshold > 100000) {
            errors.put("thresholdPpm", "Enter a number above 0 and up to 100000.");
        } else {
            item.setThresholdPpm(threshold);
        }

        String valve = form.get("valveDeviceId");
        if (!valve.isEmpty()) {
            Integer valveId = FormUtils.parseInt(valve);
            if (valveId == null) {
                errors.put("valveDeviceId", "Choose a device from the list.");
            } else {
                item.setValveDeviceId(valveId);
            }
        }
        return item;
    }
}
