-- =====================================================================
-- 06_label_device_sessions.sql : label the sessions of the real ESP32 in one time range at once
-- Every label goes through GasEventLabel_Set, so each one is in the audit log.
-- Use it after a recorded test (see firmware/README.md), then run tool.TransferCheck.
-- measured_at is UTC: Vietnam time minus 7 hours (14:00 in Ha Noi = 07:00 UTC).
-- =====================================================================
USE PRJ301_GAS;
GO
DECLARE @actor INT = (SELECT user_id FROM Users WHERE username = N'admin');
DECLARE @device INT = (SELECT device_id FROM Device WHERE device_code = N'ESP32-REAL-01');
DECLARE @fromUtc DATETIME2 = '2026-10-11 07:00:00';  -- change: start of the test, UTC
DECLARE @toUtc DATETIME2 = '2026-10-11 07:30:00';    -- change: end of the test, UTC (not included)
DECLARE @label NVARCHAR(40) = N'SAFE';               -- SAFE, COOKING_FUMES, MINOR_LEAK, MAJOR_LEAK,
                                                     -- FLAME_DETECTED or SENSOR_FAULT
DECLARE @session BIGINT, @count INT = 0;

DECLARE device_sessions CURSOR LOCAL FAST_FORWARD FOR
    SELECT session_id FROM GasEvent
    WHERE device_id = @device AND measured_at >= @fromUtc AND measured_at < @toUtc
    ORDER BY session_id;
OPEN device_sessions;
FETCH NEXT FROM device_sessions INTO @session;
WHILE @@FETCH_STATUS = 0
BEGIN
    EXEC GasEventLabel_Set @actor, @session, @label;
    SET @count += 1;
    FETCH NEXT FROM device_sessions INTO @session;
END;
CLOSE device_sessions;
DEALLOCATE device_sessions;
PRINT CONCAT(@count, N' sessions labelled ', @label);

-- labelled sessions of the device so far (TransferCheck needs about 100)
SELECT l.label, COUNT(*) AS sessions
FROM GasEventLabel l
JOIN GasEvent e ON e.session_id = l.session_id
WHERE e.device_id = @device
GROUP BY l.label
ORDER BY l.label;
GO
