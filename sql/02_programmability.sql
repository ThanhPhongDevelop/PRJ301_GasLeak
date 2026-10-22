-- =====================================================================
-- 02_programmability.sql : trigger, views, stored procedures
-- Every object uses CREATE OR ALTER, so this script can be re-run safely.
-- =====================================================================
USE PRJ301_GAS;
GO

-- sessions are append-only: block every update and delete
CREATE OR ALTER TRIGGER trg_GasEvent_AppendOnly ON GasEvent INSTEAD OF UPDATE, DELETE AS
BEGIN
    RAISERROR(N'Session records are append-only', 16, 1);
END;
GO

-- ---------------------------------------------------------------------
-- Views
-- ---------------------------------------------------------------------
-- feature view consumed by the machine learning step
CREATE OR ALTER VIEW v_GasEvent_Features AS
SELECT s.session_id, s.device_id, s.mq2_raw, s.mq135_raw, s.temp_c, s.rh_pct, s.compensated_score, l.label
FROM GasEvent s JOIN GasEventLabel l ON l.session_id = s.session_id;
GO

-- accounts with their role (one role per user in this project)
CREATE OR ALTER VIEW v_UserAccount AS
SELECT u.user_id, u.username, u.full_name, u.status, u.created_at, r.role_id, r.role_name
FROM Users u
LEFT JOIN UserRole ur ON ur.user_id = u.user_id
LEFT JOIN Roles r ON r.role_id = ur.role_id;
GO

-- kitchen zones with readable names for the list page
CREATE OR ALTER VIEW v_KitchenZoneDetail AS
SELECT z.kitchen_zone_id, z.zone_name, z.home_id, h.home_name, z.threshold_ppm,
       z.valve_device_id, d.device_code AS valve_device_code, z.created_at
FROM KitchenZone z
JOIN Home h ON h.home_id = z.home_id
LEFT JOIN Device d ON d.device_id = z.valve_device_id;
GO

-- alerts with device code and counts for the alert list
CREATE OR ALTER VIEW v_AlertOverview AS
SELECT a.alert_id, a.device_id, d.device_code, a.session_id, a.source, a.label, a.risk_score,
       a.status, a.created_at,
       (SELECT COUNT(*) FROM AlertEvidence e WHERE e.alert_id = a.alert_id) AS evidence_count,
       (SELECT COUNT(*) FROM MaintenanceTicket t WHERE t.alert_id = a.alert_id) AS ticket_count
FROM Alert a
LEFT JOIN Device d ON d.device_id = a.device_id;
GO

-- ---------------------------------------------------------------------
-- Audit log with a hash chain
-- ---------------------------------------------------------------------
-- writes one audit row and links its hash to the previous row in the same transaction
CREATE OR ALTER PROCEDURE Audit_Append
    @actor INT, @action NVARCHAR(50), @objectType NVARCHAR(50), @objectId BIGINT, @diff NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    DECLARE @id BIGINT, @ts DATETIME2(7), @prev CHAR(64), @row NVARCHAR(MAX), @hash CHAR(64);
    INSERT INTO AuditLog (actor_user_id, action, object_type, object_id, diff_json)
    VALUES (@actor, @action, @objectType, @objectId, @diff);
    SET @id = SCOPE_IDENTITY();
    SELECT @ts = ts FROM AuditLog WHERE audit_id = @id;
    -- lock the chain tail so two writers cannot fork the chain
    SELECT TOP 1 @prev = chain_hash FROM HashChain WITH (UPDLOCK, HOLDLOCK) ORDER BY anchor_id DESC;
    SET @prev = ISNULL(@prev, REPLICATE('0', 64));
    -- NULL values become '' inside CONCAT; AuditVerifier reads them back the same way
    SET @row = CONCAT(@id, '|', CONVERT(NVARCHAR(33), @ts, 126), '|', @actor, '|', @action, '|',
                      @objectType, '|', @objectId, '|', ISNULL(@diff, ''), '|', @prev);
    SET @hash = LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', @row), 2));
    UPDATE AuditLog SET row_hash = @hash WHERE audit_id = @id;
    INSERT INTO HashChain (audit_id, prev_hash, chain_hash) VALUES (@id, @prev, @hash);
    COMMIT;
END;
GO

-- ---------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------
-- new accounts get the END_USER (Guest viewer) role; @newUserId = -1 when the name is taken
CREATE OR ALTER PROCEDURE User_Register
    @username NVARCHAR(50), @passwordHash CHAR(60), @fullName NVARCHAR(80), @newUserId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET @newUserId = -1;
    IF EXISTS (SELECT 1 FROM Users WHERE username = @username)
        RETURN;
    BEGIN TRAN;
    INSERT INTO Users (username, password_hash, full_name) VALUES (@username, @passwordHash, @fullName);
    SET @newUserId = SCOPE_IDENTITY();
    INSERT INTO UserRole (user_id, role_id)
    SELECT @newUserId, role_id FROM Roles WHERE role_name = N'END_USER';
    EXEC Audit_Append @newUserId, N'REGISTER', N'USER', @newUserId, NULL;
    COMMIT;
END;
GO

-- replaces the role of a user; refuses to remove the last active administrator
CREATE OR ALTER PROCEDURE User_ChangeRole @actor INT, @userId INT, @roleName NVARCHAR(30)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @roleId INT = (SELECT role_id FROM Roles WHERE role_name = @roleName);
    IF @roleId IS NULL
        THROW 50020, N'Unknown role', 1;
    DECLARE @oldRole NVARCHAR(30) = (SELECT TOP 1 role_name FROM v_UserAccount WHERE user_id = @userId);
    IF @oldRole = N'ADMIN' AND @roleName <> N'ADMIN'
       AND (SELECT COUNT(*) FROM v_UserAccount WHERE role_name = N'ADMIN' AND status = N'ACTIVE') <= 1
        THROW 50021, N'Cannot remove the last active administrator', 1;
    BEGIN TRAN;
    DELETE FROM UserRole WHERE user_id = @userId;
    INSERT INTO UserRole (user_id, role_id) VALUES (@userId, @roleId);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"old":"', ISNULL(@oldRole, N''), N'","new":"', @roleName, N'"}');
    EXEC Audit_Append @actor, N'CHANGE_ROLE', N'USER', @userId, @diff;
    COMMIT;
END;
GO

-- ACTIVE or LOCKED (locking is the "delete" of an account: history stays linked)
CREATE OR ALTER PROCEDURE User_SetStatus @actor INT, @userId INT, @status NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @status NOT IN (N'ACTIVE', N'LOCKED')
        THROW 50030, N'Unknown status', 1;
    IF @actor = @userId AND @status = N'LOCKED'
        THROW 50031, N'You cannot lock your own account', 1;
    BEGIN TRAN;
    UPDATE Users SET status = @status WHERE user_id = @userId;
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"status":"', @status, N'"}');
    EXEC Audit_Append @actor, N'SET_STATUS', N'USER', @userId, @diff;
    COMMIT;
END;
GO

-- ---------------------------------------------------------------------
-- Gas sessions and labels
-- ---------------------------------------------------------------------
-- inserts one device session together with the rule and model results computed by GasEventApi;
-- @sessionId = -1 when (device_id, device_seq) is already stored
CREATE OR ALTER PROCEDURE GasEvent_Insert
    @deviceId INT, @deviceSeq INT, @measuredAt DATETIME2, @mq2Raw FLOAT, @mq135Raw FLOAT,
    @tempC FLOAT, @rhPct FLOAT, @compensatedScore FLOAT, @mq135Compensated FLOAT, @flameDetected BIT,
    @ruleLabel NVARCHAR(40), @mlLabel NVARCHAR(40), @mlConfidence FLOAT, @sessionId BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET @sessionId = -1;
    IF EXISTS (SELECT 1 FROM GasEvent WHERE device_id = @deviceId AND device_seq = @deviceSeq)
        RETURN;
    BEGIN TRY
        INSERT INTO GasEvent (device_id, device_seq, measured_at, mq2_raw, mq135_raw, temp_c, rh_pct,
                              compensated_score, mq135_compensated, flame_detected, rule_label, ml_label, ml_confidence)
        VALUES (@deviceId, @deviceSeq, @measuredAt, @mq2Raw, @mq135Raw, @tempC, @rhPct,
                @compensatedScore, @mq135Compensated, @flameDetected, @ruleLabel, @mlLabel, @mlConfidence);
        SET @sessionId = SCOPE_IDENTITY();
    END TRY
    BEGIN CATCH
        -- 2601/2627: the same session arrived twice at the same moment, keep the first copy
        IF ERROR_NUMBER() NOT IN (2601, 2627)
            THROW;
    END CATCH;
END;
GO

-- assigns or changes the label of a session (the session row itself never changes)
CREATE OR ALTER PROCEDURE GasEventLabel_Set @actor INT, @sessionId BIGINT, @label NVARCHAR(40)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @label NOT IN (N'SAFE', N'COOKING_FUMES', N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED', N'SENSOR_FAULT')
        THROW 50010, N'Unknown label', 1;
    IF NOT EXISTS (SELECT 1 FROM GasEvent WHERE session_id = @sessionId)
        THROW 50011, N'Session not found', 1;
    DECLARE @old NVARCHAR(40);
    BEGIN TRAN;
    SELECT @old = label FROM GasEventLabel WITH (UPDLOCK, HOLDLOCK) WHERE session_id = @sessionId;
    IF @old IS NULL
        INSERT INTO GasEventLabel (session_id, label, labeled_by) VALUES (@sessionId, @label, @actor);
    ELSE
        UPDATE GasEventLabel SET label = @label, labeled_by = @actor, labeled_at = SYSUTCDATETIME()
        WHERE session_id = @sessionId;
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"old":', CASE WHEN @old IS NULL THEN N'null' ELSE CONCAT(N'"', @old, N'"') END,
                                         N',"new":"', @label, N'"}');
    EXEC Audit_Append @actor, N'LABEL', N'GASEVENT', @sessionId, @diff;
    COMMIT;
END;
GO

-- removes the label of a session (used as the "delete" of an event)
CREATE OR ALTER PROCEDURE GasEventLabel_Remove @actor INT, @sessionId BIGINT, @removed BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @old NVARCHAR(40);
    BEGIN TRAN;
    SELECT @old = label FROM GasEventLabel WITH (UPDLOCK, HOLDLOCK) WHERE session_id = @sessionId;
    DELETE FROM GasEventLabel WHERE session_id = @sessionId;
    SET @removed = CASE WHEN @old IS NULL THEN 0 ELSE 1 END;
    IF @removed = 1
    BEGIN
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"old":"', @old, N'"}');
        EXEC Audit_Append @actor, N'UNLABEL', N'GASEVENT', @sessionId, @diff;
    END;
    COMMIT;
END;
GO

-- ---------------------------------------------------------------------
-- Alerts and maintenance
-- ---------------------------------------------------------------------
-- creates an alert with evidence; @evidenceJson = [{"item":"mq2_raw","value":"1890"}, ...]
CREATE OR ALTER PROCEDURE Alert_Create
    @deviceId INT, @sessionId BIGINT, @source NVARCHAR(10), @label NVARCHAR(40), @riskScore FLOAT,
    @evidenceJson NVARCHAR(MAX), @alertId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    INSERT INTO Alert (device_id, session_id, source, label, risk_score)
    VALUES (@deviceId, @sessionId, @source, @label, @riskScore);
    SET @alertId = SCOPE_IDENTITY();
    IF ISJSON(@evidenceJson) = 1
        INSERT INTO AlertEvidence (alert_id, item, value_text)
        SELECT @alertId, j.item, j.value_text
        FROM OPENJSON(@evidenceJson) WITH (item NVARCHAR(60) '$.item', value_text NVARCHAR(200) '$.value') j;
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"source":"', STRING_ESCAPE(ISNULL(@source, N''), 'json'),
                                         N'","label":"', STRING_ESCAPE(ISNULL(@label, N''), 'json'), N'"}');
    EXEC Audit_Append NULL, N'CREATE', N'ALERT', @alertId, @diff;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE Alert_Acknowledge @actor INT, @alertId INT, @changed BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    UPDATE Alert SET status = N'ACKNOWLEDGED' WHERE alert_id = @alertId AND status = N'OPEN';
    SET @changed = CASE WHEN @@ROWCOUNT = 1 THEN 1 ELSE 0 END;
    IF @changed = 1
        EXEC Audit_Append @actor, N'ACKNOWLEDGE', N'ALERT', @alertId, NULL;
    COMMIT;
END;
GO

-- "confirm the incident was handled": OPEN or ACKNOWLEDGED -> RESOLVED
CREATE OR ALTER PROCEDURE Alert_Resolve @actor INT, @alertId INT, @note NVARCHAR(400), @changed BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    UPDATE Alert SET status = N'RESOLVED' WHERE alert_id = @alertId AND status IN (N'OPEN', N'ACKNOWLEDGED');
    SET @changed = CASE WHEN @@ROWCOUNT = 1 THEN 1 ELSE 0 END;
    IF @changed = 1
    BEGIN
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"note":"', STRING_ESCAPE(ISNULL(@note, N''), 'json'), N'"}');
        EXEC Audit_Append @actor, N'RESOLVE', N'ALERT', @alertId, @diff;
    END;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE MaintenanceTicket_Open
    @actor INT, @alertId INT, @deviceId INT, @assignedTo INT, @note NVARCHAR(400), @ticketId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    INSERT INTO MaintenanceTicket (alert_id, device_id, assigned_to, note)
    VALUES (@alertId, @deviceId, @assignedTo, @note);
    SET @ticketId = SCOPE_IDENTITY();
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"alertId":', ISNULL(CONVERT(NVARCHAR(20), @alertId), N'null'),
                                         N',"assignedTo":', ISNULL(CONVERT(NVARCHAR(20), @assignedTo), N'null'), N'}');
    EXEC Audit_Append @actor, N'OPEN_TICKET', N'MAINTENANCE', @ticketId, @diff;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE MaintenanceTicket_Close @actor INT, @ticketId INT, @note NVARCHAR(400), @changed BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    UPDATE MaintenanceTicket
    SET status = N'CLOSED', closed_at = SYSUTCDATETIME(),
        note = LEFT(CONCAT(note, CASE WHEN note IS NULL THEN N'' ELSE N' | ' END, N'Closed: ', @note), 400)
    WHERE ticket_id = @ticketId AND status = N'OPEN';
    SET @changed = CASE WHEN @@ROWCOUNT = 1 THEN 1 ELSE 0 END;
    IF @changed = 1
    BEGIN
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"note":"', STRING_ESCAPE(ISNULL(@note, N''), 'json'), N'"}');
        EXEC Audit_Append @actor, N'CLOSE_TICKET', N'MAINTENANCE', @ticketId, @diff;
    END;
    COMMIT;
END;
GO

-- ---------------------------------------------------------------------
-- Device control lock and dashboard
-- ---------------------------------------------------------------------
-- only one person may control a device at a time
CREATE OR ALTER PROCEDURE Device_AcquireControl @deviceId INT, @userId INT, @acquired BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    UPDATE Device SET control_owner = @userId
    WHERE device_id = @deviceId AND status = N'ACTIVE' AND (control_owner IS NULL OR control_owner = @userId);
    SET @acquired = CASE WHEN @@ROWCOUNT = 1 THEN 1 ELSE 0 END;
    IF @acquired = 1
        EXEC Audit_Append @userId, N'ACQUIRE_CONTROL', N'DEVICE', @deviceId, NULL;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE Device_ReleaseControl @deviceId INT, @userId INT, @released BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    UPDATE Device SET control_owner = NULL WHERE device_id = @deviceId AND control_owner = @userId;
    SET @released = CASE WHEN @@ROWCOUNT = 1 THEN 1 ELSE 0 END;
    IF @released = 1
        EXEC Audit_Append @userId, N'RELEASE_CONTROL', N'DEVICE', @deviceId, NULL;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE Dashboard_Summary
AS
BEGIN
    SET NOCOUNT ON;
    SELECT
        (SELECT COUNT(*) FROM KitchenZone) AS zone_count,
        (SELECT COUNT(*) FROM Device WHERE status = N'ACTIVE') AS active_devices,
        (SELECT COUNT(*) FROM Device WHERE last_seen >= DATEADD(MINUTE, -5, SYSUTCDATETIME())) AS online_devices,
        (SELECT COUNT(*) FROM Alert WHERE status <> N'RESOLVED') AS open_alerts,
        -- leaks, flames and sensor faults need action; cooking fumes are informational
        (SELECT COUNT(*) FROM Alert WHERE status <> N'RESOLVED' AND label <> N'COOKING_FUMES') AS urgent_alerts,
        (SELECT COUNT(*) FROM GasEvent WHERE ingested_at >= CAST(SYSUTCDATETIME() AS DATE)) AS sessions_today;
END;
GO

-- ---------------------------------------------------------------------
-- Phase 2: feature views, alert pipeline, device commands, profiles
-- ---------------------------------------------------------------------
-- feature view of the public sessions; tool.ModelTrainer trains from it
CREATE OR ALTER VIEW v_PublicSession_Features AS
SELECT public_id, induction_id, window_idx, mq2_raw, mq135_raw, temp_c, rh_pct, compensated_score, label
FROM PublicSession WHERE label IS NOT NULL;
GO

-- sessions with readable names for the session list and detail pages
CREATE OR ALTER VIEW v_GasEventDetail AS
SELECT s.session_id, s.device_id, d.device_code, s.device_seq, s.measured_at, s.ingested_at,
       s.mq2_raw, s.mq135_raw, s.temp_c, s.rh_pct, s.compensated_score, s.mq135_compensated,
       s.flame_detected, s.rule_label, s.ml_label, s.ml_confidence,
       l.label AS human_label, l.labeled_at, u.username AS labeled_by_name,
       (SELECT COUNT(*) FROM Alert a WHERE a.session_id = s.session_id) AS alert_count
FROM GasEvent s
JOIN Device d ON d.device_id = s.device_id
LEFT JOIN GasEventLabel l ON l.session_id = s.session_id
LEFT JOIN Users u ON u.user_id = l.labeled_by;
GO

CREATE OR ALTER VIEW v_MaintenanceTicketDetail AS
SELECT t.ticket_id, t.alert_id, a.label AS alert_label, t.device_id, d.device_code, t.assigned_to,
       u.username AS assignee_name, t.note, t.status, t.opened_at, t.closed_at
FROM MaintenanceTicket t
LEFT JOIN Alert a ON a.alert_id = t.alert_id
LEFT JOIN Device d ON d.device_id = t.device_id
LEFT JOIN Users u ON u.user_id = t.assigned_to;
GO

-- raises an alert unless the same device already has an unresolved alert with this label raised
-- in the last @dedupeMinutes minutes; then @alertId is that alert and @created = 0
CREATE OR ALTER PROCEDURE Alert_Raise
    @deviceId INT, @sessionId BIGINT, @source NVARCHAR(10), @label NVARCHAR(40), @riskScore FLOAT,
    @evidenceJson NVARCHAR(MAX), @dedupeMinutes INT, @alertId INT OUTPUT, @created BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET @alertId = NULL;
    SET @created = 0;
    BEGIN TRAN;
    SELECT TOP 1 @alertId = alert_id FROM Alert WITH (UPDLOCK, HOLDLOCK)
    WHERE device_id = @deviceId AND label = @label AND status <> N'RESOLVED'
      AND created_at >= DATEADD(MINUTE, -@dedupeMinutes, SYSUTCDATETIME())
    ORDER BY alert_id DESC;
    IF @alertId IS NULL
    BEGIN
        EXEC Alert_Create @deviceId, @sessionId, @source, @label, @riskScore, @evidenceJson, @alertId OUTPUT;
        SET @created = 1;
    END;
    COMMIT;
END;
GO

-- queues an actuator command. Takes the control lock when nobody holds it and refuses when
-- someone else does. Opening the gas valve is refused while a leak or flame alert is unresolved.
CREATE OR ALTER PROCEDURE DeviceCommand_Issue
    @actor INT, @deviceId INT, @command NVARCHAR(30), @commandId BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @owner INT, @status NVARCHAR(20);
    BEGIN TRAN;
    SELECT @owner = control_owner, @status = status FROM Device WITH (UPDLOCK, HOLDLOCK) WHERE device_id = @deviceId;
    IF @status IS NULL
        THROW 50040, N'Device not found.', 1;
    IF @status <> N'ACTIVE'
        THROW 50041, N'This device is not active.', 1;
    IF @owner IS NOT NULL AND @owner <> @actor
        THROW 50042, N'Another user is controlling this device. Ask them to release it first.', 1;
    IF @command = N'OPEN_VALVE' AND EXISTS (
        SELECT 1 FROM Alert a
        WHERE a.status <> N'RESOLVED' AND a.label IN (N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED')
          AND (a.device_id = @deviceId OR a.device_id IN (
                SELECT gs.device_id FROM GasSensor gs
                JOIN KitchenZone z ON z.kitchen_zone_id = gs.kitchen_zone_id
                WHERE z.valve_device_id = @deviceId)))
        THROW 50043, N'Resolve the open leak and flame alerts of this zone before opening the gas valve.', 1;
    IF @owner IS NULL
    BEGIN
        UPDATE Device SET control_owner = @actor WHERE device_id = @deviceId;
        EXEC Audit_Append @actor, N'ACQUIRE_CONTROL', N'DEVICE', @deviceId, NULL;
    END;
    INSERT INTO DeviceCommand (device_id, command, issued_by) VALUES (@deviceId, @command, @actor);
    SET @commandId = SCOPE_IDENTITY();
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"command":"', @command, N'","commandId":', @commandId, N'}');
    EXEC Audit_Append @actor, N'COMMAND', N'DEVICE', @deviceId, @diff;
    COMMIT;
END;
GO

-- returns the pending commands of a device, oldest first, and marks them as sent
CREATE OR ALTER PROCEDURE DeviceCommand_TakePending @deviceId INT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @taken TABLE (command_id BIGINT, command NVARCHAR(30), issued_at DATETIME2);
    UPDATE DeviceCommand SET status = N'SENT', delivered_at = SYSUTCDATETIME()
    OUTPUT inserted.command_id, inserted.command, inserted.issued_at INTO @taken
    WHERE device_id = @deviceId AND status = N'PENDING';
    SELECT command_id, command, issued_at FROM @taken ORDER BY command_id;
END;
GO

-- releases every device controlled by a user (called at logout)
CREATE OR ALTER PROCEDURE Device_ReleaseAllForUser @userId INT, @released INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @ids TABLE (device_id INT);
    BEGIN TRAN;
    UPDATE Device SET control_owner = NULL OUTPUT inserted.device_id INTO @ids WHERE control_owner = @userId;
    SET @released = @@ROWCOUNT;
    IF @released > 0
    BEGIN
        DECLARE @diff NVARCHAR(MAX) = (SELECT device_id AS deviceId FROM @ids FOR JSON PATH);
        EXEC Audit_Append @userId, N'RELEASE_CONTROL', N'DEVICE', NULL, @diff;
    END;
    COMMIT;
END;
GO

-- lets an administrator free a device that someone forgot to release
CREATE OR ALTER PROCEDURE Device_ForceRelease @actor INT, @deviceId INT, @released BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @owner INT;
    BEGIN TRAN;
    SELECT @owner = control_owner FROM Device WITH (UPDLOCK, HOLDLOCK) WHERE device_id = @deviceId;
    UPDATE Device SET control_owner = NULL WHERE device_id = @deviceId AND control_owner IS NOT NULL;
    SET @released = CASE WHEN @@ROWCOUNT = 1 THEN 1 ELSE 0 END;
    IF @released = 1
    BEGIN
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"previousOwner":', @owner, N'}');
        EXEC Audit_Append @actor, N'FORCE_RELEASE', N'DEVICE', @deviceId, @diff;
    END;
    COMMIT;
END;
GO

-- stores a new compensation profile for a device (older rows stay as history)
CREATE OR ALTER PROCEDURE OperatingProfile_Save
    @actor INT, @deviceId INT, @paramsJson NVARCHAR(MAX), @profileId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF ISNULL(ISJSON(@paramsJson), 0) = 0
        THROW 50050, N'Profile parameters must be valid JSON.', 1;
    IF NOT EXISTS (SELECT 1 FROM Device WHERE device_id = @deviceId)
        THROW 50051, N'The selected device does not exist.', 1;
    BEGIN TRAN;
    INSERT INTO OperatingProfile (device_id, params_json, updated_by) VALUES (@deviceId, @paramsJson, @actor);
    SET @profileId = SCOPE_IDENTITY();
    EXEC Audit_Append @actor, N'SAVE_PROFILE', N'DEVICE', @deviceId, @paramsJson;
    COMMIT;
END;
GO

-- creates or changes the warning and alarm levels of one sensor type in one zone
CREATE OR ALTER PROCEDURE ZoneThreshold_Upsert
    @actor INT, @zoneId INT, @sensorTypeId INT, @warnLevel FLOAT, @alarmLevel FLOAT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @warnLevel IS NULL OR @alarmLevel IS NULL OR @warnLevel >= @alarmLevel
        THROW 50060, N'The warning level must be below the alarm level.', 1;
    IF NOT EXISTS (SELECT 1 FROM KitchenZone WHERE kitchen_zone_id = @zoneId)
        THROW 50061, N'The selected kitchen zone does not exist.', 1;
    IF NOT EXISTS (SELECT 1 FROM SensorType WHERE sensor_type_id = @sensorTypeId)
        THROW 50062, N'The selected sensor type does not exist.', 1;
    DECLARE @old NVARCHAR(200), @thresholdId INT;
    BEGIN TRAN;
    SELECT @thresholdId = zone_threshold_id,
           @old = CONCAT(N'{"warn":', warn_level, N',"alarm":', alarm_level, N'}')
    FROM ZoneThreshold WITH (UPDLOCK, HOLDLOCK) WHERE kitchen_zone_id = @zoneId AND sensor_type_id = @sensorTypeId;
    IF @thresholdId IS NULL
    BEGIN
        INSERT INTO ZoneThreshold (kitchen_zone_id, sensor_type_id, warn_level, alarm_level, updated_by)
        VALUES (@zoneId, @sensorTypeId, @warnLevel, @alarmLevel, @actor);
        SET @thresholdId = SCOPE_IDENTITY();
    END
    ELSE
        UPDATE ZoneThreshold
        SET warn_level = @warnLevel, alarm_level = @alarmLevel, updated_by = @actor, updated_at = SYSUTCDATETIME()
        WHERE zone_threshold_id = @thresholdId;
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"zoneId":', @zoneId, N',"sensorTypeId":', @sensorTypeId,
        N',"before":', ISNULL(@old, N'null'), N',"after":{"warn":', @warnLevel, N',"alarm":', @alarmLevel, N'}}');
    EXEC Audit_Append @actor, N'SET_THRESHOLD', N'ZONE_THRESHOLD', @thresholdId, @diff;
    COMMIT;
END;
GO

-- ---------------------------------------------------------------------
-- Phase 3: remaining entities and account management.
-- Every write goes through a procedure that checks the input, writes the change and its audit
-- entry in one transaction. Errors 5007x-5008x carry a message the page can show as it is.
-- ---------------------------------------------------------------------
CREATE OR ALTER VIEW v_HomeDetail AS
SELECT h.home_id, h.home_name, h.address, h.owner_user_id, u.username AS owner_name, h.created_at,
       (SELECT COUNT(*) FROM KitchenZone z WHERE z.home_id = h.home_id) AS zone_count,
       (SELECT COUNT(*) FROM EmergencyContact c WHERE c.home_id = h.home_id) AS contact_count
FROM Home h
LEFT JOIN Users u ON u.user_id = h.owner_user_id;
GO

CREATE OR ALTER VIEW v_GasSensorDetail AS
SELECT s.gas_sensor_id, s.sensor_code, s.device_id, d.device_code, s.sensor_type_id, t.name AS sensor_type_name,
       t.unit, s.kitchen_zone_id, z.zone_name, s.pin_label, s.installed_at, s.status
FROM GasSensor s
JOIN Device d ON d.device_id = s.device_id
JOIN SensorType t ON t.sensor_type_id = s.sensor_type_id
LEFT JOIN KitchenZone z ON z.kitchen_zone_id = s.kitchen_zone_id;
GO

CREATE OR ALTER VIEW v_ZoneThresholdDetail AS
SELECT zt.zone_threshold_id, zt.kitchen_zone_id, z.zone_name, h.home_name, zt.sensor_type_id,
       t.name AS sensor_type_name, t.unit, zt.warn_level, zt.alarm_level, u.username AS updated_by_name, zt.updated_at
FROM ZoneThreshold zt
JOIN KitchenZone z ON z.kitchen_zone_id = zt.kitchen_zone_id
JOIN Home h ON h.home_id = z.home_id
JOIN SensorType t ON t.sensor_type_id = zt.sensor_type_id
LEFT JOIN Users u ON u.user_id = zt.updated_by;
GO

CREATE OR ALTER VIEW v_EmergencyContactDetail AS
SELECT c.contact_id, c.home_id, h.home_name, c.full_name, c.phone, c.email, c.relation, c.priority,
       c.notify_min_label, c.created_at
FROM EmergencyContact c
JOIN Home h ON h.home_id = c.home_id;
GO

-- creates a home when @homeId is NULL, otherwise changes it; @homeId returns the id
CREATE OR ALTER PROCEDURE Home_Save
    @actor INT, @homeId INT OUTPUT, @homeName NVARCHAR(80), @address NVARCHAR(200), @ownerUserId INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @ownerUserId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM Users WHERE user_id = @ownerUserId)
        THROW 50070, N'The selected owner does not exist.', 1;
    DECLARE @before NVARCHAR(MAX), @after NVARCHAR(MAX), @action NVARCHAR(50) = N'CREATE';
    BEGIN TRAN;
    IF @homeId IS NULL
    BEGIN
        INSERT INTO Home (home_name, address, owner_user_id) VALUES (@homeName, @address, @ownerUserId);
        SET @homeId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        SET @before = (SELECT home_name, address, owner_user_id FROM Home WITH (UPDLOCK, HOLDLOCK)
                       WHERE home_id = @homeId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
        IF @before IS NULL
            THROW 50071, N'This home no longer exists.', 1;
        UPDATE Home SET home_name = @homeName, address = @address, owner_user_id = @ownerUserId
        WHERE home_id = @homeId;
        SET @action = N'UPDATE';
    END;
    SET @after = (SELECT home_name, address, owner_user_id FROM Home WHERE home_id = @homeId
                  FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', ISNULL(@before, N'null'), N',"after":', @after, N'}');
    EXEC Audit_Append @actor, @action, N'HOME', @homeId, @diff;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE Home_Delete @actor INT, @homeId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT home_name, address, owner_user_id FROM Home WITH (UPDLOCK, HOLDLOCK)
                   WHERE home_id = @homeId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM KitchenZone WHERE home_id = @homeId)
           OR EXISTS (SELECT 1 FROM EmergencyContact WHERE home_id = @homeId)
            THROW 50072, N'Delete or move the kitchen zones and emergency contacts of this home first.', 1;
        DELETE FROM Home WHERE home_id = @homeId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'HOME', @homeId, @diff;
    END;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE GasSensor_Save
    @actor INT, @sensorId INT OUTPUT, @sensorCode NVARCHAR(30), @deviceId INT, @sensorTypeId INT, @zoneId INT,
    @pinLabel NVARCHAR(20), @installedAt DATE, @status NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @status IS NULL OR @status NOT IN (N'ACTIVE', N'INACTIVE')
        THROW 50073, N'Status must be ACTIVE or INACTIVE.', 1;
    IF NOT EXISTS (SELECT 1 FROM Device WHERE device_id = @deviceId)
        THROW 50074, N'The selected device does not exist.', 1;
    IF NOT EXISTS (SELECT 1 FROM SensorType WHERE sensor_type_id = @sensorTypeId)
        THROW 50075, N'The selected sensor type does not exist.', 1;
    IF @zoneId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM KitchenZone WHERE kitchen_zone_id = @zoneId)
        THROW 50076, N'The selected kitchen zone does not exist.', 1;
    IF EXISTS (SELECT 1 FROM GasSensor WHERE sensor_code = @sensorCode AND gas_sensor_id <> ISNULL(@sensorId, -1))
        THROW 50077, N'Another sensor already uses this code.', 1;
    DECLARE @before NVARCHAR(MAX), @after NVARCHAR(MAX), @action NVARCHAR(50) = N'CREATE';
    BEGIN TRAN;
    IF @sensorId IS NULL
    BEGIN
        INSERT INTO GasSensor (sensor_code, device_id, sensor_type_id, kitchen_zone_id, pin_label, installed_at, status)
        VALUES (@sensorCode, @deviceId, @sensorTypeId, @zoneId, @pinLabel, @installedAt, @status);
        SET @sensorId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        SET @before = (SELECT sensor_code, device_id, sensor_type_id, kitchen_zone_id, pin_label, installed_at, status
                       FROM GasSensor WITH (UPDLOCK, HOLDLOCK) WHERE gas_sensor_id = @sensorId
                       FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
        IF @before IS NULL
            THROW 50078, N'This sensor no longer exists.', 1;
        UPDATE GasSensor
        SET sensor_code = @sensorCode, device_id = @deviceId, sensor_type_id = @sensorTypeId,
            kitchen_zone_id = @zoneId, pin_label = @pinLabel, installed_at = @installedAt, status = @status
        WHERE gas_sensor_id = @sensorId;
        SET @action = N'UPDATE';
    END;
    SET @after = (SELECT sensor_code, device_id, sensor_type_id, kitchen_zone_id, pin_label, installed_at, status
                  FROM GasSensor WHERE gas_sensor_id = @sensorId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', ISNULL(@before, N'null'), N',"after":', @after, N'}');
    EXEC Audit_Append @actor, @action, N'GAS_SENSOR', @sensorId, @diff;
    COMMIT;
END;
GO

-- a sensor has no history of its own (sessions belong to the device), so it can be deleted
CREATE OR ALTER PROCEDURE GasSensor_Delete @actor INT, @sensorId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT sensor_code, device_id, sensor_type_id, kitchen_zone_id, pin_label, installed_at, status
                   FROM GasSensor WITH (UPDLOCK, HOLDLOCK) WHERE gas_sensor_id = @sensorId
                   FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        DELETE FROM GasSensor WHERE gas_sensor_id = @sensorId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'GAS_SENSOR', @sensorId, @diff;
    END;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE ZoneThreshold_Delete @actor INT, @thresholdId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT kitchen_zone_id, sensor_type_id, warn_level, alarm_level
                   FROM ZoneThreshold WITH (UPDLOCK, HOLDLOCK) WHERE zone_threshold_id = @thresholdId
                   FOR JSON PATH, WITHOUT_ARRAY_WRAPPER);
    IF @before IS NOT NULL
    BEGIN
        DELETE FROM ZoneThreshold WHERE zone_threshold_id = @thresholdId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'ZONE_THRESHOLD', @thresholdId, @diff;
    END;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE EmergencyContact_Save
    @actor INT, @contactId INT OUTPUT, @homeId INT, @fullName NVARCHAR(80), @phone NVARCHAR(20),
    @email NVARCHAR(100), @relation NVARCHAR(40), @priority INT, @notifyMinLabel NVARCHAR(40)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF NOT EXISTS (SELECT 1 FROM Home WHERE home_id = @homeId)
        THROW 50079, N'The selected home does not exist.', 1;
    IF @priority IS NULL OR @priority < 1
        THROW 50082, N'Priority must be 1 or more (1 is called first).', 1;
    IF @notifyMinLabel IS NULL
       OR @notifyMinLabel NOT IN (N'MINOR_LEAK', N'MAJOR_LEAK', N'FLAME_DETECTED', N'SENSOR_FAULT')
        THROW 50083, N'Choose the least serious alert this contact should hear about.', 1;
    DECLARE @before NVARCHAR(MAX), @after NVARCHAR(MAX), @action NVARCHAR(50) = N'CREATE';
    BEGIN TRAN;
    IF @contactId IS NULL
    BEGIN
        INSERT INTO EmergencyContact (home_id, full_name, phone, email, relation, priority, notify_min_label)
        VALUES (@homeId, @fullName, @phone, @email, @relation, @priority, @notifyMinLabel);
        SET @contactId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        SET @before = (SELECT home_id, full_name, phone, email, relation, priority, notify_min_label
                       FROM EmergencyContact WITH (UPDLOCK, HOLDLOCK) WHERE contact_id = @contactId
                       FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
        IF @before IS NULL
            THROW 50084, N'This contact no longer exists.', 1;
        UPDATE EmergencyContact
        SET home_id = @homeId, full_name = @fullName, phone = @phone, email = @email, relation = @relation,
            priority = @priority, notify_min_label = @notifyMinLabel
        WHERE contact_id = @contactId;
        SET @action = N'UPDATE';
    END;
    SET @after = (SELECT home_id, full_name, phone, email, relation, priority, notify_min_label
                  FROM EmergencyContact WHERE contact_id = @contactId
                  FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', ISNULL(@before, N'null'), N',"after":', @after, N'}');
    EXEC Audit_Append @actor, @action, N'EMERGENCY_CONTACT', @contactId, @diff;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE EmergencyContact_Delete @actor INT, @contactId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT home_id, full_name, phone, email, relation, priority, notify_min_label
                   FROM EmergencyContact WITH (UPDLOCK, HOLDLOCK) WHERE contact_id = @contactId
                   FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        DELETE FROM EmergencyContact WHERE contact_id = @contactId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'EMERGENCY_CONTACT', @contactId, @diff;
    END;
    COMMIT;
END;
GO

-- registers a device; only the SHA-256 of its API key is stored, and never written to the audit log
CREATE OR ALTER PROCEDURE Device_Register
    @actor INT, @deviceCode NVARCHAR(30), @location NVARCHAR(80), @firmware NVARCHAR(20), @keyHash CHAR(64),
    @deviceId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @keyHash IS NULL OR LEN(@keyHash) <> 64
        THROW 50085, N'The key hash must be 64 hexadecimal characters.', 1;
    IF EXISTS (SELECT 1 FROM Device WHERE device_code = @deviceCode)
        THROW 50086, N'Another device already uses this code.', 1;
    BEGIN TRAN;
    INSERT INTO Device (device_code, location, firmware, api_key_hash) VALUES (@deviceCode, @location, @firmware, @keyHash);
    SET @deviceId = SCOPE_IDENTITY();
    DECLARE @after NVARCHAR(MAX) = (SELECT device_code, location, firmware, status FROM Device
                                    WHERE device_id = @deviceId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"after":', @after, N'}');
    EXEC Audit_Append @actor, N'REGISTER', N'DEVICE', @deviceId, @diff;
    COMMIT;
END;
GO

-- changes location, firmware and status; an inactive device loses its control lock and its key stops working
CREATE OR ALTER PROCEDURE Device_Update
    @actor INT, @deviceId INT, @location NVARCHAR(80), @firmware NVARCHAR(20), @status NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @status IS NULL OR @status NOT IN (N'ACTIVE', N'INACTIVE')
        THROW 50087, N'Status must be ACTIVE or INACTIVE.', 1;
    DECLARE @before NVARCHAR(MAX), @after NVARCHAR(MAX);
    BEGIN TRAN;
    SET @before = (SELECT location, firmware, status FROM Device WITH (UPDLOCK, HOLDLOCK) WHERE device_id = @deviceId
                   FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NULL
        THROW 50088, N'This device no longer exists.', 1;
    UPDATE Device
    SET location = @location, firmware = @firmware, status = @status,
        control_owner = CASE WHEN @status = N'ACTIVE' THEN control_owner END
    WHERE device_id = @deviceId;
    SET @after = (SELECT location, firmware, status FROM Device WHERE device_id = @deviceId
                  FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N',"after":', @after, N'}');
    EXEC Audit_Append @actor, N'UPDATE', N'DEVICE', @deviceId, @diff;
    COMMIT;
END;
GO

-- replaces the API key; the old key stops working at once
CREATE OR ALTER PROCEDURE Device_RotateKey @actor INT, @deviceId INT, @keyHash CHAR(64)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @keyHash IS NULL OR LEN(@keyHash) <> 64
        THROW 50085, N'The key hash must be 64 hexadecimal characters.', 1;
    BEGIN TRAN;
    UPDATE Device SET api_key_hash = @keyHash WHERE device_id = @deviceId;
    IF @@ROWCOUNT = 0
        THROW 50088, N'This device no longer exists.', 1;
    EXEC Audit_Append @actor, N'ROTATE_KEY', N'DEVICE', @deviceId, NULL;
    COMMIT;
END;
GO

-- deletes a device that has no history; a device with sessions, alerts or parts is set INACTIVE instead
CREATE OR ALTER PROCEDURE Device_Delete @actor INT, @deviceId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT device_code, location, firmware, status FROM Device WITH (UPDLOCK, HOLDLOCK)
                   WHERE device_id = @deviceId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM GasEvent WHERE device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM Alert WHERE device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM GasSensor WHERE device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM KitchenZone WHERE valve_device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM MaintenanceTicket WHERE device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM DeviceCommand WHERE device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM Calibration WHERE device_id = @deviceId)
           OR EXISTS (SELECT 1 FROM OperatingProfile WHERE device_id = @deviceId)
            THROW 50089, N'This device has sessions, sensors or other history. Set it to INACTIVE instead.', 1;
        DELETE FROM Device WHERE device_id = @deviceId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'DEVICE', @deviceId, @diff;
    END;
    COMMIT;
END;
GO

-- an administrator creates an account with its role; @newUserId = -1 when the username is taken
CREATE OR ALTER PROCEDURE User_Create
    @actor INT, @username NVARCHAR(50), @passwordHash CHAR(60), @fullName NVARCHAR(80), @roleName NVARCHAR(30),
    @newUserId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET @newUserId = -1;
    DECLARE @roleId INT = (SELECT role_id FROM Roles WHERE role_name = @roleName);
    IF @roleId IS NULL
        THROW 50020, N'Unknown role', 1;
    IF EXISTS (SELECT 1 FROM Users WHERE username = @username)
        RETURN;
    BEGIN TRAN;
    INSERT INTO Users (username, password_hash, full_name) VALUES (@username, @passwordHash, @fullName);
    SET @newUserId = SCOPE_IDENTITY();
    INSERT INTO UserRole (user_id, role_id) VALUES (@newUserId, @roleId);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"username":"', STRING_ESCAPE(@username, 'json'),
                                         N'","role":"', @roleName, N'"}');
    EXEC Audit_Append @actor, N'CREATE', N'USER', @newUserId, @diff;
    COMMIT;
END;
GO

-- sets a new password hash: CHANGE_PASSWORD when users change their own, RESET_PASSWORD when an
-- administrator does it; the hash never goes into the audit log
CREATE OR ALTER PROCEDURE User_SetPassword @actor INT, @userId INT, @passwordHash CHAR(60)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @action NVARCHAR(50) = CASE WHEN @actor = @userId THEN N'CHANGE_PASSWORD' ELSE N'RESET_PASSWORD' END;
    BEGIN TRAN;
    UPDATE Users SET password_hash = @passwordHash WHERE user_id = @userId;
    IF @@ROWCOUNT = 0
        THROW 50032, N'User not found.', 1;
    EXEC Audit_Append @actor, @action, N'USER', @userId, NULL;
    COMMIT;
END;
GO

-- ---------------------------------------------------------------------
-- Phase 3: shared entities (the same tables in every project): sensor types, calibrations and
-- compensation profiles. Same pattern as above; errors 50051-50052 and 50090-50102.
-- ---------------------------------------------------------------------

-- built_in: the rules find these four types by name (service.DeviceSetup), so they keep their names
CREATE OR ALTER VIEW v_SensorTypeDetail AS
SELECT t.sensor_type_id, t.name, t.unit, t.min_valid, t.max_valid,
       CAST(CASE WHEN t.name IN (N'MQ-2', N'MQ-135', N'DHT22 temperature', N'DHT22 humidity') THEN 1 ELSE 0 END AS BIT)
           AS built_in,
       (SELECT COUNT(*) FROM GasSensor s WHERE s.sensor_type_id = t.sensor_type_id) AS sensor_count,
       (SELECT COUNT(*) FROM ZoneThreshold z WHERE z.sensor_type_id = t.sensor_type_id) AS threshold_count,
       (SELECT COUNT(*) FROM Calibration c WHERE c.sensor_type_id = t.sensor_type_id) AS calibration_count
FROM SensorType t;
GO

CREATE OR ALTER VIEW v_CalibrationDetail AS
SELECT c.calib_id, c.device_id, d.device_code, c.sensor_type_id, t.name AS sensor_type_name, t.unit,
       c.offset_val, c.scale_val, c.version, c.valid_until, c.created_by, u.username AS created_by_name,
       CAST(CASE WHEN c.valid_until < CAST(SYSUTCDATETIME() AS DATE) THEN 1 ELSE 0 END AS BIT) AS expired
FROM Calibration c
LEFT JOIN Device d ON d.device_id = c.device_id
LEFT JOIN SensorType t ON t.sensor_type_id = c.sensor_type_id
LEFT JOIN Users u ON u.user_id = c.created_by;
GO

-- the newest profile of a device is the one the compensation uses (DeviceDao.latestProfileJson)
CREATE OR ALTER VIEW v_OperatingProfileDetail AS
SELECT p.profile_id, p.device_id, d.device_code, p.params_json, p.updated_by, u.username AS updated_by_name,
       p.updated_at,
       CAST(CASE WHEN p.profile_id = (SELECT MAX(n.profile_id) FROM OperatingProfile n WHERE n.device_id = p.device_id)
            THEN 1 ELSE 0 END AS BIT) AS is_current
FROM OperatingProfile p
LEFT JOIN Device d ON d.device_id = p.device_id
LEFT JOIN Users u ON u.user_id = p.updated_by;
GO

CREATE OR ALTER PROCEDURE SensorType_Save
    @actor INT, @sensorTypeId INT OUTPUT, @name NVARCHAR(40), @unit NVARCHAR(20), @minValid FLOAT, @maxValid FLOAT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET @name = LTRIM(RTRIM(@name));
    IF @name IS NULL OR @name = N''
        THROW 50090, N'Enter the name of the sensor type.', 1;
    IF (@minValid IS NULL AND @maxValid IS NOT NULL) OR (@minValid IS NOT NULL AND @maxValid IS NULL)
        THROW 50091, N'Enter both ends of the valid range, or leave both empty.', 1;
    IF @minValid >= @maxValid
        THROW 50092, N'The lowest valid value must be below the highest.', 1;
    IF EXISTS (SELECT 1 FROM SensorType WHERE name = @name AND sensor_type_id <> ISNULL(@sensorTypeId, -1))
        THROW 50093, N'Another sensor type already has this name.', 1;
    DECLARE @before NVARCHAR(MAX), @after NVARCHAR(MAX), @action NVARCHAR(50) = N'CREATE', @oldName NVARCHAR(40);
    BEGIN TRAN;
    IF @sensorTypeId IS NULL
    BEGIN
        INSERT INTO SensorType (name, unit, min_valid, max_valid) VALUES (@name, @unit, @minValid, @maxValid);
        SET @sensorTypeId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        SELECT @oldName = name FROM SensorType WITH (UPDLOCK, HOLDLOCK) WHERE sensor_type_id = @sensorTypeId;
        SET @before = (SELECT name, unit, min_valid, max_valid FROM SensorType WHERE sensor_type_id = @sensorTypeId
                       FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
        IF @before IS NULL
            THROW 50094, N'This sensor type no longer exists.', 1;
        -- binary comparison: the Java code compares names exactly, so even a change of case breaks it
        IF @oldName COLLATE Latin1_General_BIN2 <> @name COLLATE Latin1_General_BIN2
           AND EXISTS (SELECT 1 FROM v_SensorTypeDetail WHERE sensor_type_id = @sensorTypeId AND built_in = 1)
            THROW 50095, N'The rules find this sensor type by its name, so the name cannot change.', 1;
        UPDATE SensorType SET name = @name, unit = @unit, min_valid = @minValid, max_valid = @maxValid
        WHERE sensor_type_id = @sensorTypeId;
        SET @action = N'UPDATE';
    END;
    SET @after = (SELECT name, unit, min_valid, max_valid FROM SensorType WHERE sensor_type_id = @sensorTypeId
                  FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', ISNULL(@before, N'null'), N',"after":', @after, N'}');
    EXEC Audit_Append @actor, @action, N'SENSOR_TYPE', @sensorTypeId, @diff;
    COMMIT;
END;
GO

CREATE OR ALTER PROCEDURE SensorType_Delete @actor INT, @sensorTypeId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT name, unit, min_valid, max_valid FROM SensorType WITH (UPDLOCK, HOLDLOCK)
                   WHERE sensor_type_id = @sensorTypeId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM v_SensorTypeDetail WHERE sensor_type_id = @sensorTypeId AND built_in = 1)
            THROW 50096, N'The rules find this sensor type by its name, so it cannot be deleted.', 1;
        IF EXISTS (SELECT 1 FROM GasSensor WHERE sensor_type_id = @sensorTypeId)
           OR EXISTS (SELECT 1 FROM ZoneThreshold WHERE sensor_type_id = @sensorTypeId)
           OR EXISTS (SELECT 1 FROM Calibration WHERE sensor_type_id = @sensorTypeId)
           OR EXISTS (SELECT 1 FROM GasEventReading WHERE sensor_type_id = @sensorTypeId)
            THROW 50097, N'Sensors, zone levels, calibrations or readings use this sensor type. Remove them first.', 1;
        DELETE FROM SensorType WHERE sensor_type_id = @sensorTypeId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'SENSOR_TYPE', @sensorTypeId, @diff;
    END;
    COMMIT;
END;
GO

-- a new calibration gets the next version number of its device and sensor type; a change keeps it
CREATE OR ALTER PROCEDURE Calibration_Save
    @actor INT, @calibId INT OUTPUT, @deviceId INT, @sensorTypeId INT, @offsetVal FLOAT, @scaleVal FLOAT,
    @validUntil DATE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF NOT EXISTS (SELECT 1 FROM Device WHERE device_id = @deviceId)
        THROW 50098, N'The selected device does not exist.', 1;
    IF NOT EXISTS (SELECT 1 FROM SensorType WHERE sensor_type_id = @sensorTypeId)
        THROW 50099, N'The selected sensor type does not exist.', 1;
    IF @scaleVal IS NULL OR @scaleVal <= 0
        THROW 50100, N'The scale must be greater than 0.', 1;
    DECLARE @before NVARCHAR(MAX), @after NVARCHAR(MAX), @action NVARCHAR(50) = N'CREATE',
            @oldDevice INT, @oldType INT, @version INT;
    BEGIN TRAN;
    IF @calibId IS NULL
    BEGIN
        SELECT @version = ISNULL(MAX(version), 0) + 1 FROM Calibration WITH (UPDLOCK, HOLDLOCK)
        WHERE device_id = @deviceId AND sensor_type_id = @sensorTypeId;
        INSERT INTO Calibration (device_id, sensor_type_id, offset_val, scale_val, version, valid_until, created_by)
        VALUES (@deviceId, @sensorTypeId, ISNULL(@offsetVal, 0), @scaleVal, @version, @validUntil, @actor);
        SET @calibId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        SELECT @oldDevice = device_id, @oldType = sensor_type_id FROM Calibration WITH (UPDLOCK, HOLDLOCK)
        WHERE calib_id = @calibId;
        SET @before = (SELECT device_id, sensor_type_id, offset_val, scale_val, version, valid_until FROM Calibration
                       WHERE calib_id = @calibId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
        IF @before IS NULL
            THROW 50101, N'This calibration no longer exists.', 1;
        IF @oldDevice <> @deviceId OR @oldType <> @sensorTypeId
            THROW 50102, N'A calibration stays with its device and sensor type. Add a new calibration instead.', 1;
        UPDATE Calibration SET offset_val = ISNULL(@offsetVal, 0), scale_val = @scaleVal, valid_until = @validUntil
        WHERE calib_id = @calibId;
        SET @action = N'UPDATE';
    END;
    SET @after = (SELECT device_id, sensor_type_id, offset_val, scale_val, version, valid_until FROM Calibration
                  WHERE calib_id = @calibId FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', ISNULL(@before, N'null'), N',"after":', @after, N'}');
    EXEC Audit_Append @actor, @action, N'CALIBRATION', @calibId, @diff;
    COMMIT;
END;
GO

-- nothing points to a calibration, so any version can be deleted
CREATE OR ALTER PROCEDURE Calibration_Delete @actor INT, @calibId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX);
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT device_id, sensor_type_id, offset_val, scale_val, version, valid_until
                   FROM Calibration WITH (UPDLOCK, HOLDLOCK) WHERE calib_id = @calibId
                   FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        DELETE FROM Calibration WHERE calib_id = @calibId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE', N'CALIBRATION', @calibId, @diff;
    END;
    COMMIT;
END;
GO

-- profiles are versions: a change is saved as a new version (OperatingProfile_Save) and the newest one
-- is in use, so only older versions can be deleted
CREATE OR ALTER PROCEDURE OperatingProfile_Delete @actor INT, @profileId INT, @deleted BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @before NVARCHAR(MAX), @deviceId INT;
    SET @deleted = 0;
    BEGIN TRAN;
    SET @before = (SELECT profile_id, device_id, params_json, updated_by, updated_at
                   FROM OperatingProfile WITH (UPDLOCK, HOLDLOCK) WHERE profile_id = @profileId
                   FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);
    IF @before IS NOT NULL
    BEGIN
        SELECT @deviceId = device_id FROM OperatingProfile WHERE profile_id = @profileId;
        IF NOT EXISTS (SELECT 1 FROM OperatingProfile WITH (UPDLOCK, HOLDLOCK)
                       WHERE device_id = @deviceId AND profile_id > @profileId)
            THROW 50052, N'This is the profile in use (the newest of its device). Save a corrected version instead.', 1;
        DELETE FROM OperatingProfile WHERE profile_id = @profileId;
        SET @deleted = 1;
        DECLARE @diff NVARCHAR(MAX) = CONCAT(N'{"before":', @before, N'}');
        EXEC Audit_Append @actor, N'DELETE_PROFILE', N'DEVICE', @deviceId, @diff;
    END;
    COMMIT;
END;
GO
