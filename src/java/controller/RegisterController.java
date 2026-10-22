package controller;

import dao.UserDao;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.FormUtils;
import util.SecurityUtils;

@WebServlet("/RegisterController")
public class RegisterController extends HttpServlet {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,50}$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.getRequestDispatcher("/register.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String username = FormUtils.trim(req.getParameter("username"));
        String fullName = FormUtils.trim(req.getParameter("fullName"));
        String password = req.getParameter("password");
        String confirm = req.getParameter("confirm");

        Map<String, String> errors = new LinkedHashMap<String, String>();
        if (!USERNAME.matcher(username).matches()) {
            errors.put("username", "Use 3 to 50 letters, digits, dots or underscores.");
        }
        if (fullName.isEmpty() || fullName.length() > 80) {
            errors.put("fullName", "Enter your full name (up to 80 characters).");
        }
        if (!SecurityUtils.isStrongPassword(password)) {
            errors.put("password", "Use at least 8 characters with a letter and a digit.");
        } else if (!password.equals(confirm)) {
            errors.put("confirm", "The two passwords do not match.");
        }
        try {
            if (errors.isEmpty() && new UserDao().register(username, password, fullName) <= 0) {
                errors.put("username", "This username is already taken.");
            }
        } catch (SQLException e) {
            throw new ServletException(e);
        }
        if (!errors.isEmpty()) {
            req.setAttribute("errors", errors);
            req.setAttribute("username", username);
            req.setAttribute("fullName", fullName);
            req.getRequestDispatcher("/register.jsp").forward(req, res);
            return;
        }
        res.sendRedirect(req.getContextPath() + "/LoginController?registered=1");
    }
}
