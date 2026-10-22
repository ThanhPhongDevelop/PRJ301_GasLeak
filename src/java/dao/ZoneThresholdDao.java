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
import model.ZoneThreshold;
import util.DbContext;

/**
 * Zone levels the rule engine compares sensor values with. One row per zone and sensor type:
 * saving a zone and type that already have levels changes them (ZoneThreshold_Upsert).
 */
public class ZoneThresholdDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT zone_threshold_id, kitchen_zone_id, zone_name, home_name, "
            + "sensor_type_id, sensor_type_name, unit, warn_level, alarm_level, updated_by_name, updated_at "
            + "FROM v_ZoneThresholdDetail";

    public PageResult<ZoneThreshold> search(Integer zoneId, Integer sensorTypeId, int page, int pageSize)
            throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(zoneId, sensorTypeId, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_ZoneThresholdDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<ZoneThreshold>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY zone_name, sensor_type_name, zone_threshold_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    pageParams), page, pageSize, total);
        }
    }

    public List<ZoneThreshold> listForExport(Integer zoneId, Integer sensorTypeId) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(zoneId, sensorTypeId, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY zone_name, sensor_type_name", params);
        }
    }

    public ZoneThreshold findById(int thresholdId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<ZoneThreshold> found = query(conn, SELECT_COLUMNS + " WHERE zone_threshold_id = ?",
                    Collections.<Object>singletonList(thresholdId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Creates or changes the levels of a sensor type in a zone. Error 50060 when warn is not below alarm. */
    public void save(Integer actorId, int zoneId, int sensorTypeId, double warnLevel, double alarmLevel)
            throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call ZoneThreshold_Upsert(?, ?, ?, ?, ?)}")) {
            setNullableInt(cs, 1, actorId);
            cs.setInt(2, zoneId);
            cs.setInt(3, sensorTypeId);
            cs.setDouble(4, warnLevel);
            cs.setDouble(5, alarmLevel);
            cs.execute();
        }
    }

    /** Without levels the rule engine falls back to its default levels for that sensor. */
    public boolean delete(int actorId, int thresholdId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call ZoneThreshold_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, thresholdId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(Integer zoneId, Integer sensorTypeId, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (zoneId != null) {
            where.append(" AND kitchen_zone_id = ?");
            params.add(zoneId);
        }
        if (sensorTypeId != null) {
            where.append(" AND sensor_type_id = ?");
            params.add(sensorTypeId);
        }
        return where.toString();
    }

    private static List<ZoneThreshold> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<ZoneThreshold> items = new ArrayList<ZoneThreshold>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ZoneThreshold t = new ZoneThreshold();
                    t.setZoneThresholdId(rs.getInt("zone_threshold_id"));
                    t.setKitchenZoneId(rs.getInt("kitchen_zone_id"));
                    t.setZoneName(rs.getString("zone_name"));
                    t.setHomeName(rs.getString("home_name"));
                    t.setSensorTypeId(rs.getInt("sensor_type_id"));
                    t.setSensorTypeName(rs.getString("sensor_type_name"));
                    t.setUnit(rs.getString("unit"));
                    t.setWarnLevel(rs.getDouble("warn_level"));
                    t.setAlarmLevel(rs.getDouble("alarm_level"));
                    t.setUpdatedByName(rs.getString("updated_by_name"));
                    t.setUpdatedAt(rs.getTimestamp("updated_at"));
                    items.add(t);
                }
            }
        }
        return items;
    }
}
