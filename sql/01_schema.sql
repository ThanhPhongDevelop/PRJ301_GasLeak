-- =====================================================================
-- PRJ301 Final Project 1 - Kitchen Gas Leak Detection
-- 01_schema.sql : database and tables
-- Run in order: 01_schema.sql, 02_programmability.sql, 03_seed.sql (00_reset.sql first to start over)
-- Requires SQL Server 2016 SP1 or newer (CREATE OR ALTER, OPENJSON, STRING_ESCAPE)
-- =====================================================================
IF DB_ID(N'PRJ301_GAS') IS NULL
    CREATE DATABASE PRJ301_GAS;
GO
USE PRJ301_GAS;
GO
-- the tables are created once; running this file again would only print "There is already an object named ..."
IF OBJECT_ID(N'dbo.Users', N'U') IS NOT NULL
BEGIN
    RAISERROR(N'The tables already exist. To start over run 00_reset.sql, then 01, 02 and 03.', 16, 1);
    SET NOEXEC ON;
END
GO

-- ---------------------------------------------------------------------
-- 14 shared tables, identical in every project topic (copied from the workshop)
-- ---------------------------------------------------------------------
CREATE TABLE Users (user_id INT IDENTITY PRIMARY KEY, username NVARCHAR(50) UNIQUE NOT NULL,
    password_hash CHAR(60) NOT NULL, full_name NVARCHAR(80), status NVARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME2 DEFAULT SYSUTCDATETIME());
CREATE TABLE Roles (role_id INT IDENTITY PRIMARY KEY, role_name NVARCHAR(30) UNIQUE NOT NULL);
CREATE TABLE UserRole (user_id INT REFERENCES Users, role_id INT REFERENCES Roles, PRIMARY KEY (user_id, role_id));
CREATE TABLE Permissions (perm_id INT IDENTITY PRIMARY KEY, resource NVARCHAR(40), action NVARCHAR(10),
    UNIQUE (resource, action));
CREATE TABLE RolePerm (role_id INT REFERENCES Roles, perm_id INT REFERENCES Permissions, PRIMARY KEY (role_id, perm_id));
CREATE TABLE Device (device_id INT IDENTITY PRIMARY KEY, device_code NVARCHAR(30) UNIQUE, location NVARCHAR(80),
    firmware NVARCHAR(20), status NVARCHAR(20) DEFAULT 'ACTIVE', control_owner INT NULL REFERENCES Users,
    last_seen DATETIME2 NULL, api_key_hash CHAR(64) NOT NULL);
CREATE TABLE SensorType (sensor_type_id INT IDENTITY PRIMARY KEY, name NVARCHAR(40), unit NVARCHAR(20),
    min_valid FLOAT, max_valid FLOAT);
CREATE TABLE Calibration (calib_id INT IDENTITY PRIMARY KEY, device_id INT REFERENCES Device,
    sensor_type_id INT REFERENCES SensorType, offset_val FLOAT, scale_val FLOAT, version INT,
    valid_until DATE, created_by INT REFERENCES Users);
CREATE TABLE OperatingProfile (profile_id INT IDENTITY PRIMARY KEY, device_id INT REFERENCES Device,
    params_json NVARCHAR(MAX), updated_by INT REFERENCES Users, updated_at DATETIME2 DEFAULT SYSUTCDATETIME());
CREATE TABLE Alert (alert_id INT IDENTITY PRIMARY KEY, device_id INT REFERENCES Device, session_id BIGINT,
    source NVARCHAR(10), label NVARCHAR(40), risk_score FLOAT NULL, status NVARCHAR(20) DEFAULT 'OPEN',
    created_at DATETIME2 DEFAULT SYSUTCDATETIME());
CREATE TABLE AlertEvidence (evidence_id INT IDENTITY PRIMARY KEY, alert_id INT REFERENCES Alert,
    item NVARCHAR(60), value_text NVARCHAR(200));
CREATE TABLE MaintenanceTicket (ticket_id INT IDENTITY PRIMARY KEY, alert_id INT NULL REFERENCES Alert,
    device_id INT REFERENCES Device, assigned_to INT REFERENCES Users, note NVARCHAR(400),
    status NVARCHAR(20) DEFAULT 'OPEN', opened_at DATETIME2 DEFAULT SYSUTCDATETIME(), closed_at DATETIME2 NULL);
CREATE TABLE AuditLog (audit_id BIGINT IDENTITY PRIMARY KEY, actor_user_id INT, action NVARCHAR(50),
    object_type NVARCHAR(50), object_id BIGINT, diff_json NVARCHAR(MAX),
    ts DATETIME2(7) DEFAULT SYSUTCDATETIME(), row_hash CHAR(64) NULL);
CREATE TABLE HashChain (anchor_id BIGINT IDENTITY PRIMARY KEY, audit_id BIGINT REFERENCES AuditLog,
    prev_hash CHAR(64), chain_hash CHAR(64));
GO

-- ---------------------------------------------------------------------
-- Topic tables
-- ---------------------------------------------------------------------
-- a home owns kitchen zones and emergency contacts (KitchenZone.home_id points here)
CREATE TABLE Home (
    home_id INT IDENTITY PRIMARY KEY,
    home_name NVARCHAR(80) NOT NULL,
    address NVARCHAR(200),
    owner_user_id INT NULL REFERENCES Users,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME());

-- same columns as the workshop sample, plus foreign keys
CREATE TABLE KitchenZone (
    kitchen_zone_id INT IDENTITY PRIMARY KEY,
    zone_name NVARCHAR(30) NOT NULL,
    home_id INT NOT NULL REFERENCES Home,
    threshold_ppm FLOAT,
    valve_device_id INT NULL REFERENCES Device,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME());

-- one physical sensor on a device, placed in a zone
CREATE TABLE GasSensor (
    gas_sensor_id INT IDENTITY PRIMARY KEY,
    sensor_code NVARCHAR(30) NOT NULL UNIQUE,
    device_id INT NOT NULL REFERENCES Device,
    sensor_type_id INT NOT NULL REFERENCES SensorType,
    kitchen_zone_id INT NULL REFERENCES KitchenZone,
    pin_label NVARCHAR(20),
    installed_at DATE,
    status NVARCHAR(20) DEFAULT 'ACTIVE');

-- warning and alarm levels per zone and sensor type (a higher value is more dangerous)
CREATE TABLE ZoneThreshold (
    zone_threshold_id INT IDENTITY PRIMARY KEY,
    kitchen_zone_id INT NOT NULL REFERENCES KitchenZone,
    sensor_type_id INT NOT NULL REFERENCES SensorType,
    warn_level FLOAT NOT NULL,
    alarm_level FLOAT NOT NULL,
    updated_by INT NULL REFERENCES Users,
    updated_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_ZoneThreshold UNIQUE (kitchen_zone_id, sensor_type_id),
    CONSTRAINT CK_ZoneThreshold_Levels CHECK (warn_level < alarm_level));

CREATE TABLE EmergencyContact (
    contact_id INT IDENTITY PRIMARY KEY,
    home_id INT NOT NULL REFERENCES Home,
    full_name NVARCHAR(80) NOT NULL,
    phone NVARCHAR(20) NOT NULL,
    email NVARCHAR(100),
    relation NVARCHAR(40),
    priority INT NOT NULL DEFAULT 1, -- 1 = notify first
    notify_min_label NVARCHAR(40) NOT NULL DEFAULT 'MINOR_LEAK',
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_EmergencyContact_Label CHECK (notify_min_label IN
        (N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED', N'SENSOR_FAULT')));

-- session tables from the workshop
CREATE TABLE GasEvent (
    session_id BIGINT IDENTITY PRIMARY KEY,
    device_id INT NOT NULL REFERENCES Device,
    device_seq INT NOT NULL, -- sequence number on the device, prevents duplicates
    measured_at DATETIME2 NOT NULL, -- time measured on the device
    ingested_at DATETIME2 DEFAULT SYSUTCDATETIME(), -- time received by the server
    mq2_raw FLOAT,
    mq135_raw FLOAT,
    temp_c FLOAT,
    rh_pct FLOAT,
    compensated_score FLOAT, -- MQ-2 value after temperature and humidity compensation
    -- added for the alert pipeline; the session row is written once, so the result is stored with it
    mq135_compensated FLOAT NULL, -- MQ-135 value after compensation (used by the cooking fumes rule)
    flame_detected BIT NULL, -- NULL when the sender has no flame sensor (public data replay)
    rule_label NVARCHAR(40) NULL, -- rule engine result when the session arrived
    ml_label NVARCHAR(40) NULL, -- Weka prediction, NULL when no model is loaded
    ml_confidence FLOAT NULL,
    UNIQUE (device_id, device_seq),
    CONSTRAINT CK_GasEvent_RuleLabel CHECK (rule_label IN
        (N'SAFE', N'COOKING_FUMES', N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED', N'SENSOR_FAULT')),
    CONSTRAINT CK_GasEvent_MlLabel CHECK (ml_label IN
        (N'SAFE', N'COOKING_FUMES', N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED', N'SENSOR_FAULT')));

CREATE TABLE GasEventLabel (
    session_id BIGINT PRIMARY KEY REFERENCES GasEvent,
    label NVARCHAR(40) NOT NULL,
    labeled_by INT REFERENCES Users,
    labeled_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_GasEventLabel_Label CHECK (label IN
        (N'SAFE', N'COOKING_FUMES', N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED', N'SENSOR_FAULT')));

CREATE TABLE GasEventReading (
    reading_id BIGINT IDENTITY PRIMARY KEY,
    session_id BIGINT REFERENCES GasEvent,
    sensor_type_id INT REFERENCES SensorType,
    measured_at DATETIME2, value FLOAT);

-- commands for the actuators (valve, fan, buzzer); the device receives them in its next API reply
CREATE TABLE DeviceCommand (
    command_id BIGINT IDENTITY PRIMARY KEY,
    device_id INT NOT NULL REFERENCES Device,
    command NVARCHAR(30) NOT NULL,
    issued_by INT NOT NULL REFERENCES Users,
    issued_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivered_at DATETIME2 NULL,
    CONSTRAINT CK_DeviceCommand_Command CHECK (command IN
        (N'CLOSE_VALVE', N'OPEN_VALVE', N'FAN_ON', N'FAN_OFF', N'SILENCE_BUZZER')),
    CONSTRAINT CK_DeviceCommand_Status CHECK (status IN (N'PENDING', N'SENT')));

-- staging tables for the public UCI dataset (workshop 2, step 1); see sql/04_public_data.sql.
-- phase is written by tool.GasToCsv: before, during or after the stimulus of the recording.
CREATE TABLE RawGas (induction_id INT, t FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 FLOAT,
    r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, temp_c FLOAT, humidity FLOAT, class_name NVARCHAR(20),
    phase NVARCHAR(10));

-- one row per 10-minute window of a recording (see 04_public_data.sql for why)
CREATE TABLE PublicSession (
    public_id INT IDENTITY PRIMARY KEY,
    induction_id INT, -- recording the window comes from; used for grouped cross-validation
    window_idx INT, -- window number inside the recording, negative before the stimulus
    mq2_raw FLOAT,
    mq135_raw FLOAT,
    temp_c FLOAT,
    rh_pct FLOAT,
    compensated_score FLOAT,
    label NVARCHAR(40),
    sent BIT DEFAULT 0);
GO

-- Alert is a shared table, so its definition above has no link to a topic's session table.
-- For the gas topic an alert always points to the GasEvent session that raised it.
ALTER TABLE Alert ADD CONSTRAINT FK_Alert_GasEvent FOREIGN KEY (session_id) REFERENCES GasEvent (session_id);
-- the rules find sensor types by name (service.DeviceSetup), so a name may appear only once
CREATE UNIQUE INDEX UX_SensorType_Name ON SensorType (name);
GO

CREATE INDEX IX_GasEvent_Device_Time ON GasEvent (device_id, measured_at DESC);
CREATE INDEX IX_Alert_Status_Time ON Alert (status, created_at DESC);
CREATE INDEX IX_Alert_Device_Label ON Alert (device_id, label, status);
CREATE INDEX IX_Alert_Session ON Alert (session_id);
CREATE INDEX IX_DeviceCommand_Device_Status ON DeviceCommand (device_id, status);
CREATE INDEX IX_MaintenanceTicket_Alert ON MaintenanceTicket (alert_id);
CREATE INDEX IX_OperatingProfile_Device ON OperatingProfile (device_id, profile_id DESC);
-- login throttling counts recent LOGIN_FAILED entries
CREATE INDEX IX_AuditLog_Action_Ts ON AuditLog (action, ts);
CREATE INDEX IX_GasEventReading_Session ON GasEventReading (session_id);
CREATE INDEX IX_KitchenZone_Home ON KitchenZone (home_id);
GO
SET NOEXEC OFF;
GO
