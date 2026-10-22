package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.OperatingProfile;
import model.PageResult;
import service.CompensationModel;
import util.DbContext;

/**
 * Compensation profiles (shared table OperatingProfile), kept as versions. Saving always adds a new
 * version, which becomes the one in use; only older versions can be deleted (error 50052).
 */
public class OperatingProfileDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT profile_id, device_id, device_code, params_json, "
            + "updated_by, updated_by_name, updated_at, is_current FROM v_OperatingProfileDetail";

    public PageResult<OperatingProfile> search(Integer deviceId, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(deviceId, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_OperatingProfileDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<OperatingProfile>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY device_code, profile_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams),
                    page, pageSize, total);
        }
    }

    public List<OperatingProfile> listForExport(Integer deviceId) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(deviceId, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY device_code, profile_id DESC", params);
        }
    }

    public OperatingProfile findById(int profileId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<OperatingProfile> found = query(conn, SELECT_COLUMNS + " WHERE profile_id = ?",
                    Collections.<Object>singletonList(profileId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Saves the coefficients as the newest version of the device's profile. Returns its id. */
    public int save(int actorId, int deviceId, CompensationModel model) throws SQLException {
        return new DeviceDao().saveProfile(actorId, deviceId, model.toJson());
    }

    /** False when the version was already gone; error 50052 for the version in use. */
    public boolean delete(int actorId, int profileId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call OperatingProfile_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, profileId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(Integer deviceId, List<Object> params) {
        if (deviceId == null) {
            return "";
        }
        params.add(deviceId);
        return " WHERE device_id = ?";
    }

    private static List<OperatingProfile> query(Connection conn, String sql, List<Object> params)
            throws SQLException {
        List<OperatingProfile> items = new ArrayList<OperatingProfile>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OperatingProfile p = new OperatingProfile();
                    p.setProfileId(rs.getInt("profile_id"));
                    p.setDeviceId(rs.getInt("device_id"));
                    p.setDeviceCode(rs.getString("device_code"));
                    p.setParamsJson(rs.getString("params_json"));
                    p.setUpdatedBy(getNullableInt(rs, "updated_by"));
                    p.setUpdatedByName(rs.getString("updated_by_name"));
                    p.setUpdatedAt(rs.getTimestamp("updated_at"));
                    p.setCurrent(rs.getBoolean("is_current"));
                    items.add(p);
                }
            }
        }
        return items;
    }
}
