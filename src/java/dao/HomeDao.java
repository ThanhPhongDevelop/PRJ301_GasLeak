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
import model.Home;
import model.PageResult;
import util.DbContext;

/** Homes. Writes go through Home_Save and Home_Delete, which also write the audit log. */
public class HomeDao extends BaseDao {

    private static final String SELECT_COLUMNS = "SELECT home_id, home_name, address, owner_user_id, owner_name, "
            + "created_at, zone_count, contact_count FROM v_HomeDetail";

    public PageResult<Home> search(String keyword, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, params);
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM v_HomeDetail" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            return new PageResult<Home>(query(conn, SELECT_COLUMNS + where
                    + " ORDER BY home_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams), page, pageSize, total);
        }
    }

    public List<Home> listForExport(String keyword) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String where = buildWhere(keyword, params);
        try (Connection conn = DbContext.getConnection()) {
            return query(conn, SELECT_COLUMNS + where + " ORDER BY home_id", params);
        }
    }

    public Home findById(int homeId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<Home> found = query(conn, SELECT_COLUMNS + " WHERE home_id = ?",
                    Collections.<Object>singletonList(homeId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Creates the home when its id is 0, otherwise changes it. Returns the id. Errors 5007x are readable. */
    public int save(int actorId, Home home) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Home_Save(?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            bindSaveId(cs, 2, home.getHomeId());
            cs.setString(3, home.getHomeName());
            setNullableString(cs, 4, home.getAddress());
            setNullableInt(cs, 5, home.getOwnerUserId());
            cs.execute();
            return cs.getInt(2);
        }
    }

    /** False when the home was already gone; error 50072 while zones or contacts remain. */
    public boolean delete(int actorId, int homeId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Home_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, homeId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static String buildWhere(String keyword, List<Object> params) {
        String k = keyword == null ? "" : keyword;
        params.add("%" + k + "%");
        params.add("%" + k + "%");
        return " WHERE (home_name LIKE ? OR address LIKE ?)";
    }

    private static List<Home> query(Connection conn, String sql, List<Object> params) throws SQLException {
        List<Home> items = new ArrayList<Home>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Home h = new Home();
                    h.setHomeId(rs.getInt("home_id"));
                    h.setHomeName(rs.getString("home_name"));
                    h.setAddress(rs.getString("address"));
                    h.setOwnerUserId(getNullableInt(rs, "owner_user_id"));
                    h.setOwnerName(rs.getString("owner_name"));
                    h.setCreatedAt(rs.getTimestamp("created_at"));
                    h.setZoneCount(rs.getInt("zone_count"));
                    h.setContactCount(rs.getInt("contact_count"));
                    items.add(h);
                }
            }
        }
        return items;
    }
}
