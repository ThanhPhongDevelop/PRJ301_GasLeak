package controller;

import dao.AuditDao;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.User;
import service.AuditVerifier;
import util.FormUtils;
import util.JsonText;
import util.WebUtils;

/** Audit log list and the hash chain check (read-only, so Auditors can run it). */
@WebServlet("/AuditController")
public class AuditController extends HttpServlet {

    private static final int PAGE_SIZE = 20;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String filter = FormUtils.trim(req.getParameter("filter"));
        int page = FormUtils.parsePage(req.getParameter("page"));
        try {
            AuditDao dao = new AuditDao();
            if ("verify".equals(req.getParameter("action"))) {
                long brokenId = new AuditVerifier().findFirstBroken();
                req.setAttribute("verified", Boolean.TRUE);
                req.setAttribute("brokenId", brokenId);
                User me = WebUtils.currentUser(req);
                dao.append(me.getUserId(), "VERIFY_CHAIN", "AUDIT", 0,
                        JsonText.object("brokenId", brokenId < 0 ? null : brokenId));
            }
            req.setAttribute("result", dao.search(filter, page, PAGE_SIZE));
            req.setAttribute("actions", dao.listActions());
            req.setAttribute("filter", filter);
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        req.getRequestDispatcher("/audit_logs.jsp").forward(req, res);
    }
}
