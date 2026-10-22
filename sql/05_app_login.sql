-- =====================================================================
-- 05_app_login.sql : a login with only the rights the web application needs
-- Run once as sa (or another administrator) after 01, 02 and 03.
-- Then, in src/java/db.properties, replace sa with gasguard_app and its password.
-- Change the password below before running the script.
-- =====================================================================
USE master;
GO
IF SUSER_ID(N'gasguard_app') IS NULL
    CREATE LOGIN gasguard_app WITH PASSWORD = N'Change-Me-2026!', CHECK_POLICY = ON, DEFAULT_DATABASE = PRJ301_GAS;
GO

USE PRJ301_GAS;
GO
IF USER_ID(N'gasguard_app') IS NULL
    CREATE USER gasguard_app FOR LOGIN gasguard_app;
GO

-- read and change rows and run the stored procedures; no right to create, change or drop
-- tables, views or procedures, and no access to other databases
ALTER ROLE db_datareader ADD MEMBER gasguard_app;
ALTER ROLE db_datawriter ADD MEMBER gasguard_app;
GRANT EXECUTE TO gasguard_app;

-- The audit trail only grows through Audit_Append. With these denies, a stolen application password
-- or an SQL injection cannot add, edit or delete history. The procedures still write these tables
-- because the procedures and the tables have the same owner (ownership chaining skips the check).
DENY INSERT, UPDATE, DELETE ON dbo.AuditLog TO gasguard_app;
DENY INSERT, UPDATE, DELETE ON dbo.HashChain TO gasguard_app;
-- sessions are append-only (the trigger blocks this too)
DENY UPDATE, DELETE ON dbo.GasEvent TO gasguard_app;
GO

-- check: the application login cannot rewrite history (expect "OK" with error 229)
EXECUTE AS USER = N'gasguard_app';
BEGIN TRY
    UPDATE AuditLog SET action = action WHERE 1 = 0;
    PRINT N'PROBLEM: gasguard_app can still update AuditLog';
END TRY
BEGIN CATCH
    PRINT CONCAT(N'OK, error ', ERROR_NUMBER(), N': ', ERROR_MESSAGE());
END CATCH;
REVERT;
GO

-- check: the procedures still work for it (expect one new row in the audit log)
EXECUTE AS USER = N'gasguard_app';
EXEC Audit_Append NULL, N'CHECK_APP_LOGIN', N'SYSTEM', NULL, NULL;
REVERT;
SELECT TOP 1 audit_id, action, row_hash FROM AuditLog ORDER BY audit_id DESC;
GO
