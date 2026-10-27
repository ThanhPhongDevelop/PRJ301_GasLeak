package controller;

import dao.BaseDao;
import dao.EmergencyContactDao;
import dao.LookupDao;
import form.EmergencyContactForm;
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
import model.EmergencyContact;
import model.PageResult;
import util.CsvWriter;
import util.FormUtils;
import util.ViewFunctions;
import util.WebUtils;

/**
 * Emergency contacts of a home, called in priority order (1 first) from the least serious alert they
 * should hear about. GET: list (search, home filter, paging), new, edit, export. POST: save, delete.
 */
@WebServlet("/EmergencyContactController")
public class EmergencyContactController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "EMERGENCY_CONTACT";

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
                    Map<String, String> form = EmergencyContactForm.empty();
                    Integer homeId = FormUtils.parseInt(req.getParameter("homeId"));
                    form.put("homeId", homeId == null ? "" : String.valueOf(homeId)); // from a filtered list
                    showForm(req, res, form, new HashMap<String, String>());
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                EmergencyContact contact = id == null ? null : new EmergencyContactDao().findById(id);
                if (contact == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, EmergencyContactForm.of(contact), new HashMap<String, String>());
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
        EmergencyContactDao dao = new EmergencyContactDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = EmergencyContactForm.read(req);
                Map<String, String> errors = new HashMap<String, String>();
                EmergencyContact contact = EmergencyContactForm.validate(form, errors);
                if (errors.isEmpty()) {
                    try {
                        dao.save(actor, contact);
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) { // the contact was deleted meanwhile
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/EmergencyContactController");
                            return;
                        }
                        errors.put(field, e.getMessage());
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors);
                    return;
                }
                WebUtils.flash(req, "ok", contact.getContactId() > 0 ? "Contact saved." : "Contact added.");
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error",
                            deleted ? "Contact deleted." : "That contact no longer exists.");
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
        res.sendRedirect(req.getContextPath() + "/EmergencyContactController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        Integer homeId = FormUtils.parseInt(req.getParameter("homeId"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        EmergencyContactDao dao = new EmergencyContactDao();
        PageResult<EmergencyContact> result = dao.search(keyword, homeId, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(keyword, homeId, result.getTotalPages(), PAGE_SIZE);
        }
        req.setAttribute("result", result);
        req.setAttribute("q", keyword);
        req.setAttribute("homeId", homeId == null ? "" : String.valueOf(homeId));
        req.setAttribute("homes", new LookupDao().homes());
        req.getRequestDispatcher("/emergency_contact_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors) throws SQLException, ServletException, IOException {
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("homes", new LookupDao().homes());
        req.setAttribute("notifyLabels", EmergencyContactForm.NOTIFY_LABELS);
        req.getRequestDispatcher("/emergency_contact_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<EmergencyContact> items = new EmergencyContactDao().listForExport(
                FormUtils.trim(req.getParameter("q")), FormUtils.parseInt(req.getParameter("homeId")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"emergency_contacts.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');
        out.write(CsvWriter.row("id", "home", "priority", "full_name", "phone", "email", "relation", "notify_from",
                "created_at_utc"));
        for (EmergencyContact c : items) {
            out.write(CsvWriter.row(c.getContactId(), c.getHomeName(), c.getPriority(), c.getFullName(), c.getPhone(),
                    c.getEmail(), c.getRelation(), c.getNotifyMinLabel(), ViewFunctions.time(c.getCreatedAt())));
        }
        out.flush();
    }
}
