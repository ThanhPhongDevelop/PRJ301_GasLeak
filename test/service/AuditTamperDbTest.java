package service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import util.DbContext;

/**
 * Runs against the real database inside one transaction that is rolled back at the end,
 * so the demo data is never changed. Skipped when SQL Server is not reachable.
 */
public class AuditTamperDbTest {

    private Connection conn;

    @BeforeClass
    public void connect() {
        try {
            conn = DbContext.getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new SkipException("SQL Server is not reachable: " + e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true)
    public void rollbackEverything() throws SQLException {
        if (conn != null) {
            conn.rollback();
            conn.close();
        }
    }

    @Test
    public void entriesWrittenByTheProcedureKeepTheChainIntact() throws SQLException {
        append("TEST_APPEND", "{\"step\":1}");
        Assert.assertEquals(new AuditVerifier().findFirstBroken(conn), -1L);
    }

    @Test(dependsOnMethods = "entriesWrittenByTheProcedureKeepTheChainIntact")
    public void editedEntryIsDetected() throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            long id = append("TEST_TAMPER", "{\"thresholdPpm\":300}");
            execute("UPDATE AuditLog SET diff_json = N'{\"thresholdPpm\":900}' WHERE audit_id = ?", id);
            Assert.assertEquals(new AuditVerifier().findFirstBroken(conn), id);
        } finally {
            conn.rollback(savepoint);
        }
    }

    @Test(dependsOnMethods = "entriesWrittenByTheProcedureKeepTheChainIntact")
    public void rowInsertedOutsideTheProcedureIsDetected() throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            execute("INSERT INTO AuditLog (actor_user_id, action, object_type) VALUES (?, 'FORGED', 'USER')", 1);
            long forged = lastId("FORGED");
            Assert.assertEquals(new AuditVerifier().findFirstBroken(conn), forged);
        } finally {
            conn.rollback(savepoint);
        }
    }

    private long append(String action, String diff) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("{call Audit_Append(?, ?, ?, ?, ?)}")) {
            cs.setNull(1, java.sql.Types.INTEGER);
            cs.setString(2, action);
            cs.setString(3, "TEST");
            cs.setNull(4, java.sql.Types.BIGINT);
            cs.setString(5, diff);
            cs.execute();
        }
        return lastId(action);
    }

    private long lastId(String action) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TOP 1 audit_id FROM AuditLog WHERE action = ? ORDER BY audit_id DESC")) {
            ps.setString(1, action);
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    /**
     * Run as gasguard_app (sql/05_app_login.sql), the application login must not be able to change
     * history at all. Skipped for other logins such as sa, which the tamper tests above need.
     */
    @Test
    public void applicationLoginCannotRewriteHistory() throws SQLException {
        String login;
        try (PreparedStatement ps = conn.prepareStatement("SELECT SUSER_SNAME()");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            login = rs.getString(1);
        }
        if (!"gasguard_app".equalsIgnoreCase(login)) {
            throw new SkipException("Connected as " + login + "; run with gasguard_app to check its rights");
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            execute("UPDATE AuditLog SET action = action WHERE audit_id = ?", 1);
            Assert.fail("gasguard_app could update AuditLog");
        } catch (SQLException e) {
            Assert.assertEquals(e.getErrorCode(), 229, "expected permission denied, got " + e.getMessage());
        } finally {
            conn.rollback(savepoint);
        }
    }

    /** The tamper tests simulate an attacker with full database rights; the app login has none. */
    private void execute(String sql, long value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 229 && !sql.startsWith("UPDATE AuditLog SET action = action")) {
                throw new SkipException("This login may not change AuditLog (good: gasguard_app). "
                        + "Run the tamper tests with sa.");
            }
            throw e;
        }
    }
}
