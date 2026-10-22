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
import model.Calibration;
import model.PageResult;
import util.DbContext;

/**
 * Calibrations (shared table). Writes go through Calibration_Save and Calibration_Delete, which also
 * write the audit log. A new calibration gets the next version of its device and sensor type.
 */
public class CalibrationDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT calib_id, device_id, device_code, sensor_type_id, "
            + "sensor_type_name, unit, offset_val, scale_val, version, valid_until, created_by, created_by_name, "
            + "expired FROM v_CalibrationDetail";

    public PageResult<Calibration> search(Integer deviceId, Integer sensorTypeId, int page, int pageSize)
            throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(deviceId, sensorTypeId, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_CalibrationDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<Calibration>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY device_code, sensor_type_name, version DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    pageParams), page, pageSize, total);
        }
    }

    public List<Calibration> listForExport(Integer deviceId, Integer sensorTypeId) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(deviceId, sensorTypeId, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY device_code, sensor_type_name, version DESC",
                    params);
        }
    }

    public Calibration findById(int calibId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<Calibration> found = query(conn, SELECT_COLUMNS + " WHERE calib_id = ?",
                    Collections.<Object>singletonList(calibId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /**
     * Creates the calibration when its id is 0, otherwise changes offset, scale and valid-until date
     * (device and sensor type stay: error 50102). Returns the id. Errors 50098-50102.
     */
    public int save(int actorId, Calibration c) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Calibration_Save(?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            bindSaveId(cs, 2, c.getCalibId());
            cs.setInt(3, c.getDeviceId());
            cs.setInt(4, c.getSensorTypeId());
            cs.setDouble(5, c.getOffsetVal());
            cs.setDouble(6, c.getScaleVal());
            if (c.getValidUntil() == null) {
                cs.setNull(7, Types.DATE);
            } else {
                cs.setDate(7, c.getValidUntil());
            }
            cs.execute();
            return cs.getInt(2);
        }
    }

    /** False when the calibration was already gone. */
    public boolean delete(int actorId, int calibId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Calibration_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, calibId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(Integer deviceId, Integer sensorTypeId, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (deviceId != null) {
            where.append(" AND device_id = ?");
            params.add(deviceId);
        }
        if (sensorTypeId != null) {
            where.append(" AND sensor_type_id = ?");
            params.add(sensorTypeId);
        }
        return where.toString();
    }

    private static List<Calibration> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<Calibration> items = new ArrayList<Calibration>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Calibration c = new Calibration();
                    c.setCalibId(rs.getInt("calib_id"));
                    c.setDeviceId(rs.getInt("device_id"));
                    c.setDeviceCode(rs.getString("device_code"));
                    c.setSensorTypeId(rs.getInt("sensor_type_id"));
                    c.setSensorTypeName(rs.getString("sensor_type_name"));
                    c.setUnit(rs.getString("unit"));
                    c.setOffsetVal(rs.getDouble("offset_val"));
                    c.setScaleVal(rs.getDouble("scale_val"));
                    c.setVersion(rs.getInt("version"));
                    c.setValidUntil(rs.getDate("valid_until"));
                    c.setCreatedBy(getNullableInt(rs, "created_by"));
                    c.setCreatedByName(rs.getString("created_by_name"));
                    c.setExpired(rs.getBoolean("expired"));
                    items.add(c);
                }
            }
        }
        return items;
    }
}
