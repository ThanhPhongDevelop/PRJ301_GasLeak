package service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import util.DbContext;
import util.SecurityUtils;

/**
 * Recomputes the audit hash chain in Java and returns the first entry that does not match.
 * Fixes two gaps in the workshop sample: NULL columns are read as '' exactly like CONCAT in
 * Audit_Append (the sample turned a NULL actor into "0"), and rows inserted without going
 * through Audit_Append (no HashChain entry) are reported too.
 */
public class AuditVerifier {

    public static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private static final String CHAIN_SQL = "SELECT CONVERT(NVARCHAR(20), a.audit_id), "
            + "CONVERT(NVARCHAR(33), a.ts, 126), ISNULL(CONVERT(NVARCHAR(20), a.actor_user_id), N''), "
            + "ISNULL(a.action, N''), ISNULL(a.object_type, N''), ISNULL(CONVERT(NVARCHAR(20), a.object_id), N''), "
            + "ISNULL(a.diff_json, N''), h.prev_hash, h.chain_hash, a.audit_id "
            + "FROM AuditLog a JOIN HashChain h ON a.audit_id = h.audit_id ORDER BY h.anchor_id";

    private static final String ORPHAN_SQL = "SELECT MIN(a.audit_id) FROM AuditLog a "
            + "WHERE NOT EXISTS (SELECT 1 FROM HashChain h WHERE h.audit_id = a.audit_id)";

    /** First broken audit_id, or -1 when the chain is intact. */
    public long findFirstBroken() throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            return findFirstBroken(conn);
        }
    }

    /** Same check on a given connection, so a test can tamper inside a transaction and roll back. */
    public long findFirstBroken(Connection conn) throws SQLException {
        String prevHash = ZERO_HASH;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(CHAIN_SQL)) {
            while (rs.next()) {
                String row = buildRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), prevHash);
                String hash = hashRow(row);
                if (!prevHash.equals(rs.getString(8)) || !hash.equals(rs.getString(9))) {
                    return rs.getLong(10);
                }
                prevHash = hash;
            }
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(ORPHAN_SQL)) {
            if (rs.next()) {
                long orphan = rs.getLong(1);
                if (!rs.wasNull()) {
                    return orphan;
                }
            }
        }
        return -1;
    }

    /** Same layout as @row in Audit_Append: id|ts|actor|action|objectType|objectId|diff|prevHash */
    public static String buildRow(String id, String ts, String actor, String action, String objectType,
            String objectId, String diff, String prevHash) {
        return nz(id) + "|" + nz(ts) + "|" + nz(actor) + "|" + nz(action) + "|" + nz(objectType) + "|"
                + nz(objectId) + "|" + nz(diff) + "|" + nz(prevHash);
    }

    /** SQL Server hashes NVARCHAR as UTF-16LE, so Java hashes the same bytes. */
    public static String hashRow(String row) {
        return SecurityUtils.toHex(SecurityUtils.sha256(row.getBytes(StandardCharsets.UTF_16LE)));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
