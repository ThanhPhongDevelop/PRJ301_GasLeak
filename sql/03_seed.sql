-- =====================================================================
-- 03_seed.sql : roles, permissions, demo accounts and demo data
-- Demo password for every account: Demo@123 (change it before any real use)
-- =====================================================================
USE PRJ301_GAS;
GO

-- stop with one clear message instead of a page of errors when the order is wrong or the data is already there
IF OBJECT_ID(N'dbo.Audit_Append', N'P') IS NULL
BEGIN
    RAISERROR(N'Run 01_schema.sql and 02_programmability.sql before 03_seed.sql.', 16, 1);
    SET NOEXEC ON;
END
ELSE IF EXISTS (SELECT 1 FROM dbo.Roles)
BEGIN
    RAISERROR(N'The demo data is already loaded. To load it again run 00_reset.sql, then 01, 02 and 03.', 16, 1);
    SET NOEXEC ON;
END
GO

-- one transaction: if any insert fails nothing is kept, so the script can simply be run again
SET XACT_ABORT ON;
BEGIN TRAN;

INSERT INTO Roles (role_name) VALUES (N'ADMIN'), (N'OPERATOR'), (N'TECHNICIAN'), (N'AUDITOR'), (N'END_USER');

-- every resource gets a READ and a WRITE permission (11 from the workshop + 5 for this topic)
INSERT INTO Permissions (resource, action)
SELECT r.res, a.act
FROM (VALUES (N'USER'), (N'ROLE'), (N'DEVICE'), (N'SENSOR_TYPE'), (N'CALIBRATION'), (N'PROFILE'),
             (N'GASEVENT'), (N'KITCHENZONE'), (N'ALERT'), (N'MAINTENANCE'), (N'AUDIT'),
             (N'HOME'), (N'GAS_SENSOR'), (N'ZONE_THRESHOLD'), (N'EMERGENCY_CONTACT'), (N'CONTROL')) r(res)
CROSS JOIN (VALUES (N'READ'), (N'WRITE')) a(act);

-- ADMIN (Administrator): everything
INSERT INTO RolePerm (role_id, perm_id)
SELECT (SELECT role_id FROM Roles WHERE role_name = N'ADMIN'), perm_id FROM Permissions;

-- AUDITOR (Auditor): read-only on everything, as in the workshop sample
INSERT INTO RolePerm (role_id, perm_id)
SELECT (SELECT role_id FROM Roles WHERE role_name = N'AUDITOR'), perm_id FROM Permissions WHERE action = N'READ';

-- OPERATOR (Homeowner), TECHNICIAN (Technician), END_USER (Guest viewer)
-- R = read only, RW = read and write. Compare with the table in the course rules file.
INSERT INTO RolePerm (role_id, perm_id)
SELECT r.role_id, p.perm_id
FROM (VALUES
    (N'OPERATOR', N'HOME', N'RW'), (N'OPERATOR', N'KITCHENZONE', N'RW'), (N'OPERATOR', N'ZONE_THRESHOLD', N'RW'),
    (N'OPERATOR', N'EMERGENCY_CONTACT', N'RW'), (N'OPERATOR', N'GASEVENT', N'RW'), (N'OPERATOR', N'ALERT', N'RW'),
    (N'OPERATOR', N'CONTROL', N'RW'), (N'OPERATOR', N'GAS_SENSOR', N'R'), (N'OPERATOR', N'DEVICE', N'R'),
    (N'OPERATOR', N'SENSOR_TYPE', N'R'), (N'OPERATOR', N'PROFILE', N'R'), (N'OPERATOR', N'MAINTENANCE', N'R'),
    (N'TECHNICIAN', N'DEVICE', N'RW'), (N'TECHNICIAN', N'GAS_SENSOR', N'RW'), (N'TECHNICIAN', N'SENSOR_TYPE', N'RW'),
    (N'TECHNICIAN', N'CALIBRATION', N'RW'), (N'TECHNICIAN', N'PROFILE', N'RW'), (N'TECHNICIAN', N'MAINTENANCE', N'RW'),
    (N'TECHNICIAN', N'ALERT', N'RW'), (N'TECHNICIAN', N'GASEVENT', N'RW'), (N'TECHNICIAN', N'CONTROL', N'RW'),
    (N'TECHNICIAN', N'KITCHENZONE', N'R'), (N'TECHNICIAN', N'ZONE_THRESHOLD', N'R'), (N'TECHNICIAN', N'HOME', N'R'),
    (N'TECHNICIAN', N'EMERGENCY_CONTACT', N'R'),
    (N'END_USER', N'HOME', N'R'), (N'END_USER', N'KITCHENZONE', N'R'), (N'END_USER', N'GASEVENT', N'R'),
    (N'END_USER', N'ALERT', N'R'), (N'END_USER', N'GAS_SENSOR', N'R')
) m(role_name, resource, access)
JOIN Roles r ON r.role_name = m.role_name
JOIN Permissions p ON p.resource = m.resource AND (p.action = N'READ' OR m.access = N'RW');

-- one demo account per role; BCrypt hash ($2a$, cost 10) of Demo@123, compatible with jbcrypt-0.4
DECLARE @demoHash CHAR(60) = '$2a$10$ifggxOSMrIYAn1ipqEYfuuDKOpPPE.Q/DAbK4dt1Nv/nbgQYIm/HK';
INSERT INTO Users (username, password_hash, full_name) VALUES
    (N'admin', @demoHash, N'System Administrator'),
    (N'owner', @demoHash, N'Demo Homeowner'),
    (N'tech', @demoHash, N'Demo Technician'),
    (N'auditor', @demoHash, N'Demo Auditor'),
    (N'guest', @demoHash, N'Demo Guest');
INSERT INTO UserRole (user_id, role_id)
SELECT u.user_id, r.role_id
FROM (VALUES (N'admin', N'ADMIN'), (N'owner', N'OPERATOR'), (N'tech', N'TECHNICIAN'),
             (N'auditor', N'AUDITOR'), (N'guest', N'END_USER')) m(username, role_name)
JOIN Users u ON u.username = m.username
JOIN Roles r ON r.role_name = m.role_name;

-- API keys are stored as SHA-256 hex.
-- device 1 "device-1-api-key": the replay device used by tool.DataReplayer (public data)
-- device 2 "device-2-api-key": the real ESP32 of the extension; put this key in the firmware so its
--   sessions stay apart from the replayed ones (tool.TransferCheck reads device 2)
INSERT INTO Device (device_code, location, firmware, api_key_hash) VALUES
    (N'ESP32-KITCHEN-01', N'Main kitchen', N'1.0.0', '73fb4e98f6db0f8a286c82e03e54764c4d8cd556fcfd858c0f6edf61cdaec849'),
    (N'ESP32-REAL-01', N'Oven corner', N'1.0.0', 'ba743c0f791497d2448fe7ee55e73342a389d72158dd264e77a98140c6cdab77');

INSERT INTO SensorType (name, unit, min_valid, max_valid) VALUES
    (N'MQ-2', N'adc', 0, 4095), (N'MQ-135', N'adc', 0, 4095),
    (N'DHT22 temperature', N'C', -40, 80), (N'DHT22 humidity', N'%', 0, 100), (N'Flame', N'bool', 0, 1);

INSERT INTO Home (home_name, address, owner_user_id)
SELECT N'Demo home', N'123 Demo Street', user_id FROM Users WHERE username = N'owner';

INSERT INTO KitchenZone (zone_name, home_id, threshold_ppm, valve_device_id)
SELECT N'Stove area', h.home_id, 300, d.device_id
FROM Home h CROSS JOIN Device d WHERE h.home_name = N'Demo home' AND d.device_code = N'ESP32-KITCHEN-01';
INSERT INTO KitchenZone (zone_name, home_id, threshold_ppm, valve_device_id)
SELECT N'Oven corner', h.home_id, 250, d.device_id
FROM Home h CROSS JOIN Device d WHERE h.home_name = N'Demo home' AND d.device_code = N'ESP32-REAL-01';

INSERT INTO GasSensor (sensor_code, device_id, sensor_type_id, kitchen_zone_id, pin_label, installed_at)
SELECT m.code, d.device_id, t.sensor_type_id, z.kitchen_zone_id, m.pin, CAST(SYSUTCDATETIME() AS DATE)
FROM (VALUES (N'MQ2-01', N'MQ-2', N'GPIO34'), (N'MQ135-01', N'MQ-135', N'GPIO35'),
             (N'DHT22-T-01', N'DHT22 temperature', N'GPIO4'), (N'DHT22-H-01', N'DHT22 humidity', N'GPIO4'),
             (N'FLAME-01', N'Flame', N'GPIO27')) m(code, type_name, pin)
JOIN SensorType t ON t.name = m.type_name
CROSS JOIN (SELECT device_id FROM Device WHERE device_code = N'ESP32-KITCHEN-01') d
CROSS JOIN (SELECT kitchen_zone_id FROM KitchenZone WHERE zone_name = N'Stove area') z;

INSERT INTO GasSensor (sensor_code, device_id, sensor_type_id, kitchen_zone_id, pin_label, installed_at)
SELECT m.code, d.device_id, t.sensor_type_id, z.kitchen_zone_id, m.pin, CAST(SYSUTCDATETIME() AS DATE)
FROM (VALUES (N'MQ2-02', N'MQ-2', N'GPIO34'), (N'MQ135-02', N'MQ-135', N'GPIO35'),
             (N'DHT22-T-02', N'DHT22 temperature', N'GPIO4'), (N'DHT22-H-02', N'DHT22 humidity', N'GPIO4'),
             (N'FLAME-02', N'Flame', N'GPIO27')) m(code, type_name, pin)
JOIN SensorType t ON t.name = m.type_name
CROSS JOIN (SELECT device_id FROM Device WHERE device_code = N'ESP32-REAL-01') d
CROSS JOIN (SELECT kitchen_zone_id FROM KitchenZone WHERE zone_name = N'Oven corner') z;

INSERT INTO ZoneThreshold (kitchen_zone_id, sensor_type_id, warn_level, alarm_level)
SELECT z.kitchen_zone_id, t.sensor_type_id, m.warn_level, m.alarm_level
FROM (VALUES (N'Stove area', N'MQ-2', 1500, 2500), (N'Stove area', N'MQ-135', 1200, 2200),
             (N'Oven corner', N'MQ-2', 1500, 2500), (N'Oven corner', N'MQ-135', 1200, 2200)
    ) m(zone_name, type_name, warn_level, alarm_level)
JOIN KitchenZone z ON z.zone_name = m.zone_name
JOIN SensorType t ON t.name = m.type_name;

INSERT INTO EmergencyContact (home_id, full_name, phone, email, relation, priority, notify_min_label)
SELECT home_id, N'Demo Contact', N'0900000001', N'contact@example.com', N'Family', 1, N'MINOR_LEAK'
FROM Home WHERE home_name = N'Demo home';
INSERT INTO EmergencyContact (home_id, full_name, phone, relation, priority, notify_min_label)
SELECT home_id, N'Fire and rescue', N'114', N'Emergency service', 2, N'MAJOR_LEAK'
FROM Home WHERE home_name = N'Demo home';

-- first entry of the audit chain (actor and object are NULL on purpose: tests the NULL handling)
EXEC Audit_Append NULL, N'SEED', N'SYSTEM', NULL, N'{"note":"initial data loaded"}';

COMMIT;
PRINT N'Demo data loaded: log in as admin, owner, tech, auditor or guest with the password Demo@123.';
GO
SET NOEXEC OFF;
GO
