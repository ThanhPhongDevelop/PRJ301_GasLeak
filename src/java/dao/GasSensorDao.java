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
import model.GasSensor;
import model.PageResult;
import util.DbContext;

/** Gas sensors. Writes go through GasSensor_Save and GasSensor_Delete. */
public class GasSensorDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT gas_sensor_id, sensor_code, device_id, device_code, "
            + "sensor_type_id, sensor_type_name, unit, kitchen_zone_id, zone_name, pin_label, installed_at, status "
            + "FROM v_GasSensorDetail";

    public PageResult<GasSensor> search(String keyword, Integer deviceId, Integer zoneId, String status, int page,
            int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, deviceId, zoneId, status, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_GasSensorDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<GasSensor>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY gas_sensor_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams), page, pageSize, total);
        }
    }

    public List<GasSensor> listForExport(String keyword, Integer deviceId, Integer zoneId, String status)
            throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, deviceId, zoneId, status, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY gas_sensor_id", params);
        }
    }

    public GasSensor findById(int sensorId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<GasSensor> found = query(conn, SELECT_COLUMNS + " WHERE gas_sensor_id = ?",
                    Collections.<Object>singletonList(sensorId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Creates the sensor when its id is 0, otherwise changes it. Returns the id. */
    public int save(int actorId, GasSensor sensor) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call GasSensor_Save(?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            bindSaveId(cs, 2, sensor.getGasSensorId());
            cs.setString(3, sensor.getSensorCode());
            cs.setInt(4, sensor.getDeviceId());
            cs.setInt(5, sensor.getSensorTypeId());
            setNullableInt(cs, 6, sensor.getKitchenZoneId());
            setNullableString(cs, 7, sensor.getPinLabel());
            if (sensor.getInstalledAt() == null) {
                cs.setNull(8, Types.DATE);
            } else {
                cs.setDate(8, sensor.getInstalledAt());
            }
            cs.setString(9, sensor.getStatus());
            cs.execute();
            return cs.getInt(2);
        }
    }

    public boolean delete(int actorId, int sensorId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call GasSensor_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, sensorId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(String keyword, Integer deviceId, Integer zoneId, String status,
            List<Object> params) {
        String k = keyword == null ? "" : keyword;
        StringBuilder where = new StringBuilder(" WHERE (sensor_code LIKE ? OR device_code LIKE ?)");
        params.add("%" + k + "%");
        params.add("%" + k + "%");
        if (deviceId != null) {
            where.append(" AND device_id = ?");
            params.add(deviceId);
        }
        if (zoneId != null) {
            where.append(" AND kitchen_zone_id = ?");
            params.add(zoneId);
        }
        if (status != null && !status.isEmpty()) {
            where.append(" AND status = ?");
            params.add(status);
        }
        return where.toString();
    }

    private static List<GasSensor> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<GasSensor> items = new ArrayList<GasSensor>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GasSensor s = new GasSensor();
                    s.setGasSensorId(rs.getInt("gas_sensor_id"));
                    s.setSensorCode(rs.getString("sensor_code"));
                    s.setDeviceId(rs.getInt("device_id"));
                    s.setDeviceCode(rs.getString("device_code"));
                    s.setSensorTypeId(rs.getInt("sensor_type_id"));
                    s.setSensorTypeName(rs.getString("sensor_type_name"));
                    s.setUnit(rs.getString("unit"));
                    s.setKitchenZoneId(getNullableInt(rs, "kitchen_zone_id"));
                    s.setZoneName(rs.getString("zone_name"));
                    s.setPinLabel(rs.getString("pin_label"));
                    s.setInstalledAt(rs.getDate("installed_at"));
                    s.setStatus(rs.getString("status"));
                    items.add(s);
                }
            }
        }
        return items;
    }
}
