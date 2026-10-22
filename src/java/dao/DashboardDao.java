package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import model.DashboardSummary;
import util.DbContext;

public class DashboardDao {

    public DashboardSummary summary() throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Dashboard_Summary}");
                ResultSet rs = cs.executeQuery()) {
            if (!rs.next()) {
                return new DashboardSummary(0, 0, 0, 0, 0, 0);
            }
            return new DashboardSummary(rs.getInt("zone_count"), rs.getInt("active_devices"),
                    rs.getInt("online_devices"), rs.getInt("open_alerts"), rs.getInt("urgent_alerts"),
                    rs.getInt("sessions_today"));
        }
    }
}
