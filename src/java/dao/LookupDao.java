package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.Option;
import util.DbContext;

/** Options for select boxes in forms and filters. */
public class LookupDao {

    public List<Option> homes() throws SQLException {
        return options("SELECT home_id, home_name FROM Home ORDER BY home_name");
    }

    public List<Option> devices() throws SQLException {
        return options("SELECT device_id, device_code FROM Device ORDER BY device_code");
    }

    /** Active technicians and administrators, who can be assigned maintenance tickets. */
    public List<Option> technicians() throws SQLException {
        return options("SELECT user_id, CONCAT(full_name, N' (', username, N')') FROM v_UserAccount "
                + "WHERE status = 'ACTIVE' AND role_name IN ('TECHNICIAN', 'ADMIN') ORDER BY full_name");
    }

    public List<Option> sensorTypes() throws SQLException {
        return options("SELECT sensor_type_id, CONCAT(name, N' (', unit, N')') FROM SensorType ORDER BY name");
    }

    /** Kitchen zones labelled with their home, for zone pickers. */
    public List<Option> zones() throws SQLException {
        return options("SELECT z.kitchen_zone_id, CONCAT(z.zone_name, N', ', h.home_name) FROM KitchenZone z "
                + "JOIN Home h ON h.home_id = z.home_id ORDER BY h.home_name, z.zone_name");
    }

    /** Active accounts, for the home owner picker. */
    public List<Option> activeUsers() throws SQLException {
        return options("SELECT user_id, CONCAT(full_name, N' (', username, N')') FROM Users "
                + "WHERE status = 'ACTIVE' ORDER BY full_name");
    }

    private static List<Option> options(String sql) throws SQLException {
        List<Option> list = new ArrayList<Option>();
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Option(rs.getInt(1), rs.getString(2)));
            }
        }
        return list;
    }
}
