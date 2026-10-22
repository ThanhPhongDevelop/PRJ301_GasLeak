package security;

import filter.SecurityFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The checks the team lead does when reviewing a new page, run automatically on the source files.
 * Run from the project folder (NetBeans does this for tests).
 */
public class SecurityRulesTest {

    /** Servlets that only need a login (or none): every other one must name a protected resource. */
    private static final Set<String> LOGIN_ONLY = new HashSet<String>(Arrays.asList(
            "/LoginController", "/RegisterController", "/LogoutController", "/DashboardController",
            "/AccountController"));

    private Path root;

    @BeforeClass
    public void findProject() {
        root = Paths.get(System.getProperty("project.dir", "."));
        if (!Files.isDirectory(root.resolve("src/java/controller")) || !Files.isDirectory(root.resolve("web"))) {
            throw new SkipException("Run from the project folder to check the source files");
        }
    }

    @Test
    public void everyServletIsProtectedByAResource() throws IOException {
        // @WebServlet("/X"), @javax.servlet.annotation.WebServlet(...), value = "/X" or urlPatterns = {"/X"}
        Pattern servlet = Pattern.compile(
                "@(?:[\\w.]*\\.)?WebServlet\\(\\s*(?:(?:value|urlPatterns)\\s*=\\s*)?\\{?\\s*\"([^\"]+)\"");
        List<String> unprotected = new ArrayList<String>();
        int checked = 0;
        for (String folder : new String[]{"src/java/controller", "src/java/api"}) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root.resolve(folder), "*.java")) {
                for (Path file : files) {
                    Matcher m = servlet.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                    while (m.find()) {
                        String path = m.group(1);
                        checked++;
                        if (!LOGIN_ONLY.contains(path) && !SecurityFilter.isOpenPath(path)
                                && SecurityFilter.resourceFor(path) == null) {
                            unprotected.add(path + " (" + file.getFileName() + ")");
                        }
                    }
                }
            }
        }
        Assert.assertTrue(checked >= 12, "found only " + checked + " servlets");
        Assert.assertTrue(unprotected.isEmpty(), "add these to RESOURCE_BY_PATH in SecurityFilter: " + unprotected);
    }

    @Test
    public void everyPostFormCarriesTheCsrfToken() throws IOException {
        Pattern form = Pattern.compile("<form[^>]*method=\"post\"[^>]*>(.*?)</form>", Pattern.DOTALL);
        Set<String> exempt = new HashSet<String>(Arrays.asList("login.jsp", "register.jsp")); // no session yet
        List<String> missing = new ArrayList<String>();
        for (Path page : jsps()) {
            if (exempt.contains(page.getFileName().toString())) {
                continue;
            }
            Matcher m = form.matcher(new String(Files.readAllBytes(page), StandardCharsets.UTF_8));
            while (m.find()) {
                if (!m.group(1).contains("name=\"csrfToken\"")) {
                    missing.add(page.getFileName().toString());
                }
            }
        }
        Assert.assertTrue(missing.isEmpty(), "POST forms without csrfToken in: " + missing);
    }

    @Test
    public void requestParametersAreNeverPrintedRaw() throws IOException {
        // ${param.x} is allowed only inside test="...", where it is compared, not printed
        Pattern raw = Pattern.compile("\\$\\{[^}]*\\bparam\\.");
        Pattern inTest = Pattern.compile("test=\"\\$\\{[^\"]*\\bparam\\.");
        List<String> found = new ArrayList<String>();
        for (Path page : jsps()) {
            String[] lines = new String(Files.readAllBytes(page), StandardCharsets.UTF_8).split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (raw.matcher(lines[i]).find() && !inTest.matcher(lines[i]).find()) {
                    found.add(page.getFileName() + ":" + (i + 1));
                }
            }
        }
        Assert.assertTrue(found.isEmpty(), "print request parameters with <c:out>: " + found);
    }

    private List<Path> jsps() throws IOException {
        List<Path> pages = new ArrayList<Path>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root.resolve("web"), "*.jsp")) {
            for (Path p : files) {
                pages.add(p);
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root.resolve("web/WEB-INF/jspf"), "*.jspf")) {
            for (Path p : files) {
                pages.add(p);
            }
        }
        return pages;
    }
}
