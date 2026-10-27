package controller;

import dao.BaseDao;
import dao.HomeDao;
import dao.LookupDao;
import form.HomeForm;
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
import model.Home;
import model.PageResult;
import util.CsvWriter;
import util.FormUtils;
import util.ViewFunctions;
import util.WebUtils;

/**
 * Homes. GET: list (search, paging), new, edit, export. POST: save (add or change), delete.
 * SecurityFilter checks HOME READ for GET and HOME WRITE for POST; Home_Save and Home_Delete check
 * the data again and write the audit log in the same transaction.
 */
@WebServlet("/HomeController")
public class HomeController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final String RESOURCE = "HOME";

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
                    showForm(req, res, HomeForm.empty(), new HashMap<String, String>());
                    return;
                }
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                Home home = id == null ? null : new HomeDao().findById(id);
                if (home == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                showForm(req, res, HomeForm.of(home), new HashMap<String, String>());
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
        HomeDao dao = new HomeDao();
        try {
            if ("save".equals(action)) {
                Map<String, String> form = HomeForm.read(req);
                Map<String, String> errors = new HashMap<String, String>();
                Home home = HomeForm.validate(form, errors);
                if (errors.isEmpty()) {
                    try {
                        dao.save(actor, home); // the procedure checks again and writes the audit row
                    } catch (SQLException e) {
                        if (!BaseDao.isBusinessRule(e)) {
                            throw e;
                        }
                        String field = ProcErrors.fieldOf(e);
                        if (field == null) { // for example the home was deleted meanwhile
                            WebUtils.flash(req, "error", e.getMessage());
                            res.sendRedirect(req.getContextPath() + "/HomeController");
                            return;
                        }
                        errors.put(field, e.getMessage());
                    }
                }
                if (!errors.isEmpty()) {
                    showForm(req, res, form, errors);
                    return;
                }
                WebUtils.flash(req, "ok", home.getHomeId() > 0 ? "Home saved." : "Home added.");
            } else if ("delete".equals(action)) {
                Integer id = FormUtils.parseInt(req.getParameter("id"));
                if (id == null) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                try {
                    boolean deleted = dao.delete(actor, id);
                    WebUtils.flash(req, deleted ? "ok" : "error",
                            deleted ? "Home deleted." : "That home no longer exists.");
                } catch (SQLException e) {
                    if (!BaseDao.isBusinessRule(e)) {
                        throw e;
                    }
                    WebUtils.flash(req, "error", e.getMessage()); // the home still has zones or contacts
                }
            } else {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        res.sendRedirect(req.getContextPath() + "/HomeController");
    }

    private void showList(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, ServletException, IOException {
        String keyword = FormUtils.trim(req.getParameter("q"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        HomeDao dao = new HomeDao();
        PageResult<Home> result = dao.search(keyword, page, PAGE_SIZE);
        if (result.getItems().isEmpty() && page > result.getTotalPages()) {
            result = dao.search(keyword, result.getTotalPages(), PAGE_SIZE); // page past the end
        }
        req.setAttribute("result", result);
        req.setAttribute("q", keyword);
        req.getRequestDispatcher("/home_list.jsp").forward(req, res);
    }

    private void showForm(HttpServletRequest req, HttpServletResponse res, Map<String, String> form,
            Map<String, String> errors) throws SQLException, ServletException, IOException {
        req.setAttribute("form", form);
        req.setAttribute("errors", errors);
        req.setAttribute("owners", new LookupDao().activeUsers());
        req.getRequestDispatcher("/home_form.jsp").forward(req, res);
    }

    private void exportCsv(HttpServletRequest req, HttpServletResponse res) throws SQLException, IOException {
        List<Home> items = new HomeDao().listForExport(FormUtils.trim(req.getParameter("q")));
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"homes.csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿'); // byte order mark so Excel reads UTF-8 correctly
        out.write(CsvWriter.row("id", "home_name", "address", "owner", "kitchen_zones", "emergency_contacts",
                "created_at_utc"));
        for (Home h : items) {
            out.write(CsvWriter.row(h.getHomeId(), h.getHomeName(), h.getAddress(), h.getOwnerName(),
                    h.getZoneCount(), h.getContactCount(), ViewFunctions.time(h.getCreatedAt())));
        }
        out.flush();
    }
}
