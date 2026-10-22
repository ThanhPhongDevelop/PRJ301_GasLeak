package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.KitchenZone;
import model.PageResult;
import util.DbContext;

/** Reference CRUD: copy this pattern for the other entities. */
public class KitchenZoneDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT kitchen_zone_id, zone_name, home_id, home_name, "
            + "threshold_ppm, valve_device_id, valve_device_code, created_at FROM v_KitchenZoneDetail";

    /** Keyword search and home filter with server-side paging. */
    public PageResult<KitchenZone> search(String keyword, Integer homeId, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, homeId, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_KitchenZoneDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            List<KitchenZone> items = query(conn, SELECT_COLUMNS + where
                    + " ORDER BY kitchen_zone_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams);
            return new PageResult<KitchenZone>(items, page, pageSize, total);
        }
    }

    /** Same filter without paging, for the CSV export. */
    public List<KitchenZone> listForExport(String keyword, Integer homeId) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, homeId, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY kitchen_zone_id", params);
        }
    }

    public KitchenZone findById(int id) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<KitchenZone> found = query(conn, SELECT_COLUMNS + " WHERE kitchen_zone_id = ?",
                    Collections.<Object>singletonList(id));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    public int insert(KitchenZone item) throws SQLException {
        String sql = "INSERT INTO KitchenZone (zone_name, home_id, threshold_ppm, valve_device_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getZoneName());
            ps.setInt(2, item.getHomeId());
            ps.setDouble(3, item.getThresholdPpm());
            setNullableInt(ps, 4, item.getValveDeviceId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    public boolean update(KitchenZone item) throws SQLException {
        String sql = "UPDATE KitchenZone SET zone_name = ?, home_id = ?, threshold_ppm = ?, valve_device_id = ? "
                + "WHERE kitchen_zone_id = ?";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getZoneName());
            ps.setInt(2, item.getHomeId());
            ps.setDouble(3, item.getThresholdPpm());
            setNullableInt(ps, 4, item.getValveDeviceId());
            ps.setInt(5, item.getId());
            return ps.executeUpdate() == 1;
        }
    }

    /** Throws SQLException 547 while sensors or thresholds still point at the zone. */
    public boolean delete(int id) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM KitchenZone WHERE kitchen_zone_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static String buildWhere(String keyword, Integer homeId, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE zone_name LIKE ?");
        params.add("%" + (keyword == null ? "" : keyword) + "%");
        if (homeId != null) {
            where.append(" AND home_id = ?");
            params.add(homeId);
        }
        return where.toString();
    }

    private static List<KitchenZone> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<KitchenZone> items = new ArrayList<KitchenZone>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    KitchenZone item = new KitchenZone();
                    item.setId(rs.getInt("kitchen_zone_id"));
                    item.setZoneName(rs.getString("zone_name"));
                    item.setHomeId(rs.getInt("home_id"));
                    item.setHomeName(rs.getString("home_name"));
                    item.setThresholdPpm(rs.getDouble("threshold_ppm"));
                    item.setValveDeviceId(getNullableInt(rs, "valve_device_id"));
                    item.setValveDeviceCode(rs.getString("valve_device_code"));
                    item.setCreatedAt(rs.getTimestamp("created_at"));
                    items.add(item);
                }
            }
        }
        return items;
    }
}
