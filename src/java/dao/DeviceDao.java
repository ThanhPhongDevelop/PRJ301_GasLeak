package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.DeviceCommand;
import model.DeviceKey;
import model.DeviceInfo;
import model.PageResult;
import model.SensorLevels;
import util.DbContext;
import util.SecurityUtils;

public class DeviceDao extends BaseDao {

    private static final String DEVICE_COLUMNS = "SELECT d.device_id, d.device_code, d.location, d.firmware, d.status, "
            + "d.control_owner, u.username AS control_owner_name, d.last_seen, "
            + "CASE WHEN d.last_seen >= DATEADD(MINUTE, -5, SYSUTCDATETIME()) THEN 1 ELSE 0 END AS online, "
            + "STUFF((SELECT DISTINCT N', ' + z.zone_name FROM GasSensor gs "
            + "JOIN KitchenZone z ON z.kitchen_zone_id = gs.kitchen_zone_id WHERE gs.device_id = d.device_id "
            + "FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(400)'), 1, 2, N'') AS zone_names "
            + "FROM Device d LEFT JOIN Users u ON u.user_id = d.control_owner";

    private static final String COMMAND_COLUMNS = "SELECT c.command_id, c.device_id, d.device_code, c.command, "
            + "u.username AS issued_by_name, c.issued_at, c.status, c.delivered_at FROM DeviceCommand c "
            + "JOIN Device d ON d.device_id = c.device_id LEFT JOIN Users u ON u.user_id = c.issued_by";

    /** Device id for a SHA-256 key hash, or -1 when no active device has that key. */
    public int findActiveIdByKeyHash(String keyHash) throws SQLException {
        String sql = "SELECT device_id FROM Device WHERE api_key_hash = ? AND status = 'ACTIVE'";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, keyHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /** Updates last_seen whenever the device talks to the server. */
    public void touch(int deviceId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Device SET last_seen = SYSUTCDATETIME() WHERE device_id = ?")) {
            ps.setInt(1, deviceId);
            ps.executeUpdate();
        }
    }

    public List<DeviceInfo> listDevices() throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            return queryDevices(conn, DEVICE_COLUMNS + " ORDER BY d.device_code", Collections.emptyList());
        }
    }

    public DeviceInfo findDevice(int deviceId) throws SQLException {
        try (Connection conn = DbContext.getConnection()) {
            List<DeviceInfo> found = queryDevices(conn, DEVICE_COLUMNS + " WHERE d.device_id = ?",
                    Collections.<Object>singletonList(deviceId));
            return found.isEmpty() ? null : found.get(0);
        }
    }

    public Integer firstActiveDeviceId() throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT TOP 1 device_id FROM Device WHERE status = 'ACTIVE' ORDER BY device_id");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    /** Valve device of the zone where this device's sensors are installed, or null when there is none. */
    public Integer valveDeviceFor(int sensorDeviceId) throws SQLException {
        String sql = "SELECT TOP 1 z.valve_device_id FROM GasSensor gs "
                + "JOIN KitchenZone z ON z.kitchen_zone_id = gs.kitchen_zone_id "
                + "WHERE gs.device_id = ? AND z.valve_device_id IS NOT NULL ORDER BY gs.gas_sensor_id";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sensorDeviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    // ---------------------------------------------------------------- administration (phase 3)

    /** Device list page: code or location search, optional status filter, paging. */
    public PageResult<DeviceInfo> search(String keyword, String status, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<Object>();
        String k = keyword == null ? "" : keyword;
        StringBuilder where = new StringBuilder(" WHERE (d.device_code LIKE ? OR d.location LIKE ?)");
        params.add("%" + k + "%");
        params.add("%" + k + "%");
        if (status != null && !status.isEmpty()) {
            where.append(" AND d.status = ?");
            params.add(status);
        }
        try (Connection conn = DbContext.getConnection()) {
            int total = count(conn, "SELECT COUNT(*) FROM Device d" + where, params);
            List<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add((page - 1) * pageSize);
            pageParams.add(pageSize);
            List<DeviceInfo> items = queryDevices(conn, DEVICE_COLUMNS + where
                    + " ORDER BY d.device_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", pageParams);
            return new PageResult<DeviceInfo>(items, page, pageSize, total);
        }
    }

    /**
     * Registers a device and issues its API key. The key is returned here once and is never stored:
     * the database keeps its SHA-256 only. Show it to the user once and tell them to copy it.
     */
    public DeviceKey register(int actorId, String deviceCode, String location, String firmware) throws SQLException {
        String apiKey = SecurityUtils.newApiKey();
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Device_Register(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setString(2, deviceCode);
            setNullableString(cs, 3, location);
            setNullableString(cs, 4, firmware);
            cs.setString(5, SecurityUtils.sha256Hex(apiKey));
            cs.registerOutParameter(6, Types.INTEGER);
            cs.execute();
            return new DeviceKey(cs.getInt(6), apiKey);
        }
    }

    /** INACTIVE stops the API key working and frees the control lock. */
    public void update(int actorId, int deviceId, String location, String firmware, String status) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Device_Update(?, ?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, deviceId);
            setNullableString(cs, 3, location);
            setNullableString(cs, 4, firmware);
            cs.setString(5, status);
            cs.execute();
        }
    }

    /** Issues a new API key (returned once); the old key stops working immediately. */
    public DeviceKey rotateKey(int actorId, int deviceId) throws SQLException {
        String apiKey = SecurityUtils.newApiKey();
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Device_RotateKey(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, deviceId);
            cs.setString(3, SecurityUtils.sha256Hex(apiKey));
            cs.execute();
            return new DeviceKey(deviceId, apiKey);
        }
    }

    /** Deletes a device without history; error 50089 tells the user to set it INACTIVE instead. */
    public boolean delete(int actorId, int deviceId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Device_Delete(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, deviceId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    // ---------------------------------------------------------------- profiles and levels

    /** Newest compensation profile (OperatingProfile.params_json) of a device, or null. */
    public String latestProfileJson(int deviceId) throws SQLException {
        String sql = "SELECT TOP 1 params_json FROM OperatingProfile WHERE device_id = ? ORDER BY profile_id DESC";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public int saveProfile(Integer actorId, int deviceId, String paramsJson) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call OperatingProfile_Save(?, ?, ?, ?)}")) {
            setNullableInt(cs, 1, actorId);
            cs.setInt(2, deviceId);
            cs.setString(3, paramsJson);
            cs.registerOutParameter(4, Types.INTEGER);
            cs.execute();
            return cs.getInt(4);
        }
    }

    /** Zone levels of every active sensor of a device, first installed sensor first. */
    public List<SensorLevels> zoneLevels(int deviceId) throws SQLException {
        String sql = "SELECT st.name AS sensor_name, z.kitchen_zone_id, z.zone_name, zt.warn_level, zt.alarm_level "
                + "FROM GasSensor gs "
                + "JOIN SensorType st ON st.sensor_type_id = gs.sensor_type_id "
                + "JOIN KitchenZone z ON z.kitchen_zone_id = gs.kitchen_zone_id "
                + "JOIN ZoneThreshold zt ON zt.kitchen_zone_id = gs.kitchen_zone_id "
                + "AND zt.sensor_type_id = gs.sensor_type_id "
                + "WHERE gs.device_id = ? AND gs.status = 'ACTIVE' ORDER BY gs.gas_sensor_id";
        List<SensorLevels> levels = new ArrayList<SensorLevels>();
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    levels.add(new SensorLevels(rs.getString("sensor_name"), rs.getInt("kitchen_zone_id"),
                            rs.getString("zone_name"), rs.getDouble("warn_level"), rs.getDouble("alarm_level")));
                }
            }
        }
        return levels;
    }

    /** Valid range per sensor type name: {min_valid, max_valid}. */
    public Map<String, double[]> sensorLimits() throws SQLException {
        Map<String, double[]> limits = new HashMap<String, double[]>();
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT name, min_valid, max_valid FROM SensorType "
                        + "WHERE min_valid IS NOT NULL AND max_valid IS NOT NULL");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                limits.put(rs.getString(1), new double[]{rs.getDouble(2), rs.getDouble(3)});
            }
        }
        return limits;
    }

    public Integer sensorTypeId(String name) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT sensor_type_id FROM SensorType WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** Zone of the active sensor of this type on a device, or null. */
    public Integer zoneOfSensor(int deviceId, String sensorName) throws SQLException {
        String sql = "SELECT TOP 1 gs.kitchen_zone_id FROM GasSensor gs "
                + "JOIN SensorType st ON st.sensor_type_id = gs.sensor_type_id "
                + "WHERE gs.device_id = ? AND st.name = ? AND gs.status = 'ACTIVE' AND gs.kitchen_zone_id IS NOT NULL "
                + "ORDER BY gs.gas_sensor_id";
        try (Connection conn = DbContext.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            ps.setString(2, sensorName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    // ---------------------------------------------------------------- control lock and commands

    /** Only one person may control a device at a time; false when someone else holds it. */
    public boolean acquireControl(int deviceId, int userId) throws SQLException {
        return callControl("{call Device_AcquireControl(?, ?, ?)}", deviceId, userId);
    }

    public boolean releaseControl(int deviceId, int userId) throws SQLException {
        return callControl("{call Device_ReleaseControl(?, ?, ?)}", deviceId, userId);
    }

    public boolean forceRelease(int actorId, int deviceId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Device_ForceRelease(?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, deviceId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    /** Releases every device the user controls (logout); returns how many. */
    public int releaseAllFor(int userId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call Device_ReleaseAllForUser(?, ?)}")) {
            cs.setInt(1, userId);
            cs.registerOutParameter(2, Types.INTEGER);
            cs.execute();
            return cs.getInt(2);
        }
    }

    /** Queues a command; the stored procedure takes the lock or refuses (error 5004x with a readable message). */
    public long issueCommand(int actorId, int deviceId, String command) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call DeviceCommand_Issue(?, ?, ?, ?)}")) {
            cs.setInt(1, actorId);
            cs.setInt(2, deviceId);
            cs.setString(3, command);
            cs.registerOutParameter(4, Types.BIGINT);
            cs.execute();
            return cs.getLong(4);
        }
    }

    /** Pending commands for the device, oldest first; they are marked as sent. */
    public List<DeviceCommand> takePendingCommands(int deviceId) throws SQLException {
        List<DeviceCommand> commands = new ArrayList<DeviceCommand>();
        try (Connection conn = DbContext.getConnection();
                CallableStatement cs = conn.prepareCall("{call DeviceCommand_TakePending(?)}")) {
            cs.setInt(1, deviceId);
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    DeviceCommand c = new DeviceCommand();
                    c.setCommandId(rs.getLong("command_id"));
                    c.setDeviceId(deviceId);
                    c.setCommand(rs.getString("command"));
                    c.setIssuedAt(rs.getTimestamp("issued_at"));
                    c.setStatus("SENT");
                    commands.add(c);
                }
            }
        }
        return commands;
    }

    public int countPendingCommands(int deviceId) throws SQLException {
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM DeviceCommand WHERE device_id = ? AND status = 'PENDING'")) {
            ps.setInt(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<DeviceCommand> recentCommands(int limit) throws SQLException {
        List<DeviceCommand> commands = new ArrayList<DeviceCommand>();
        try (Connection conn = DbContext.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        COMMAND_COLUMNS.replaceFirst("SELECT ", "SELECT TOP (?) ") + " ORDER BY c.command_id DESC")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DeviceCommand c = new DeviceCommand();
                    c.setCommandId(rs.getLong("command_id"));
                    c.setDeviceId(rs.getInt("device_id"));
                    c.setDeviceCode(rs.getString("device_code"));
                    c.setCommand(rs.getString("command"));
                    c.setIssuedByName(rs.getString("issued_by_name"));
                    c.setIssuedAt(rs.getTimestamp("issued_at"));
                    c.setStatus(rs.getString("status"));
                    c.setDeliveredAt(rs.getTimestamp("delivered_at"));
                    commands.add(c);
                }
            }
        }
        return commands;
    }

    private static boolean callControl(String call, int deviceId, int userId) throws SQLException {
        try (Connection conn = DbContext.getConnection(); CallableStatement cs = conn.prepareCall(call)) {
            cs.setInt(1, deviceId);
            cs.setInt(2, userId);
            cs.registerOutParameter(3, Types.BIT);
            cs.execute();
            return cs.getBoolean(3);
        }
    }

    private static List<DeviceInfo> queryDevices(Connection conn, String sql, List<?> params) throws SQLException {
        List<DeviceInfo> devices = new ArrayList<DeviceInfo>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, new ArrayList<Object>(params));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DeviceInfo d = new DeviceInfo();
                    d.setDeviceId(rs.getInt("device_id"));
                    d.setDeviceCode(rs.getString("device_code"));
                    d.setLocation(rs.getString("location"));
                    d.setFirmware(rs.getString("firmware"));
                    d.setStatus(rs.getString("status"));
                    d.setControlOwner(getNullableInt(rs, "control_owner"));
                    d.setControlOwnerName(rs.getString("control_owner_name"));
                    d.setLastSeen(rs.getTimestamp("last_seen"));
                    d.setOnline(rs.getInt("online") == 1);
                    d.setZoneNames(rs.getString("zone_names"));
                    devices.add(d);
                }
            }
        }
        return devices;
    }
}
