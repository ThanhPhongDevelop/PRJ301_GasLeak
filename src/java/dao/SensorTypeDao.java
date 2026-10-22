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
import model.PageResult;
import model.SensorType;
import util.DbContext;

/**
 * Sensor types (shared table). Writes go through SensorType_Save and SensorType_Delete, which also
 * write the audit log. The four types the rules use keep their names (built_in in the view).
 */
public class SensorTypeDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT sensor_type_id, name, unit, min_valid, max_valid, built_in, "
            + "sensor_count, threshold_count, calibration_count FROM v_SensorTypeDetail";

    public PageResult<SensorType> search(String keyword, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_SensorTypeDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<SensorType>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY sensor_type_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams),
                    page, pageSize, total);
        }
    }

    public List<SensorType> listForExport(String keyword) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY sensor_type_id", params);
        }
    }

    public SensorType findById(int sensorTypeId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<SensorType> found = query(conn, SELECT_COLUMNS + " WHERE sensor_type_id = ?",
                    Collections.<Object>singletonList(sensorTypeId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Creates the type when its id is 0, otherwise changes it. Returns the id. Errors 50090-50095. */
    public int save(int actorId, SensorType type) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call SensorType_Save(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            bindSaveId(cs, 2, type.getSensorTypeId());
            cs.setString(3, type.getName());
            setNullableString(cs, 4, type.getUnit());
            setNullableDouble(cs, 5, type.getMinValid());
            setNullableDouble(cs, 6, type.getMaxValid());
            cs.execute();
            return cs.getInt(2);
        }
    }

    /** False when the type was already gone; 50096 for a built-in type, 50097 while records use it. */
    public boolean delete(int actorId, int sensorTypeId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call SensorType_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, sensorTypeId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(String keyword, List<Object> params) {
        String k = keyword == null ? "" : keyword;
        params.add("%" + k + "%");
        params.add("%" + k + "%");
        return " WHERE (name LIKE ? OR unit LIKE ?)";
    }

    private static List<SensorType> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<SensorType> items = new ArrayList<SensorType>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SensorType t = new SensorType();
                    t.setSensorTypeId(rs.getInt("sensor_type_id"));
                    t.setName(rs.getString("name"));
                    t.setUnit(rs.getString("unit"));
                    t.setMinValid(getNullableDouble(rs, "min_valid"));
                    t.setMaxValid(getNullableDouble(rs, "max_valid"));
                    t.setBuiltIn(rs.getBoolean("built_in"));
                    t.setSensorCount(rs.getInt("sensor_count"));
                    t.setThresholdCount(rs.getInt("threshold_count"));
                    t.setCalibrationCount(rs.getInt("calibration_count"));
                    items.add(t);
                }
            }
        }
        return items;
    }
}
