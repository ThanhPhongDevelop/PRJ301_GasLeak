-- =====================================================================
-- 04_public_data.sql : loads the public UCI dataset and builds PublicSession
-- Run after 01, 02 and 03. Before running it:
--   1. Download "Gas sensors for home activity monitoring" (UCI, id 362) and unzip it.
--   2. In NetBeans, run tool.GasToCsv (Run File) with HT_Sensor_dataset.dat and
--      HT_Sensor_metadata.dat in the project folder. It writes gas_dataset.csv.
--   3. Copy gas_dataset.csv to a folder the SQL Server service can read, for example C:\data\
--      (a folder under your user profile usually is not readable by the service).
-- After this script: run tool.CompensationFitter, then tool.ModelTrainer, then tool.DataReplayer.
-- =====================================================================
USE PRJ301_GAS;
GO

TRUNCATE TABLE RawGas;
BULK INSERT RawGas FROM 'C:\data\gas_dataset.csv'
WITH (FIRSTROW = 2, FIELDTERMINATOR = ',', ROWTERMINATOR = '0x0a', TABLOCK);
GO

-- expect about 919,000 rows from 100 recordings
SELECT COUNT(*) AS raw_rows, COUNT(DISTINCT induction_id) AS recordings FROM RawGas;
SELECT class_name, phase, COUNT(*) AS n FROM RawGas GROUP BY class_name, phase ORDER BY class_name, phase;
GO

-- ---------------------------------------------------------------------
-- One session = one 10-minute window of a recording.
-- * The reference code (scripts/Features_split.py) also splits every recording into 10-minute windows.
--   Grouping by recording only, as in the workshop query, gives exactly 100 sessions, fewer than the
--   several hundred the training step needs. PublicSession keeps induction_id so that
--   tool.ModelTrainer can keep all windows of a recording in the same cross-validation fold.
-- * MOX resistance (kOhm) falls when gas is present, while the device reports ADC counts read across
--   a load resistor, which rise. To replay the public data like the device does, every resistance is
--   converted with the voltage divider equation: adc = 4095 * RL / (R + RL), RL = 10 kOhm.
-- * Labels: background recordings are SAFE. For banana (COOKING_FUMES) and wine (MINOR_LEAK) only
--   windows during the stimulus get the class; windows before the stimulus are SAFE; windows after
--   it are dropped because the gas is still clearing.
-- * compensated_score starts equal to mq2_raw (no correction). tool.CompensationFitter fits the
--   temperature and humidity correction and rewrites this column.
-- ---------------------------------------------------------------------
DECLARE @adcFull FLOAT = 4095, @loadKohm FLOAT = 10, @windowsPerHour FLOAT = 6, @minRows INT = 30;

TRUNCATE TABLE PublicSession;
INSERT INTO PublicSession (induction_id, window_idx, mq2_raw, mq135_raw, temp_c, rh_pct, compensated_score, label)
SELECT induction_id, window_idx,
       AVG(@adcFull * @loadKohm / (r1 + @loadKohm)),
       AVG(@adcFull * @loadKohm / (r2 + @loadKohm)),
       AVG(temp_c),
       AVG(humidity),
       AVG(@adcFull * @loadKohm / (r1 + @loadKohm)),
       CASE WHEN MIN(class_name) = N'background' OR MIN(phase) = N'before' THEN N'SAFE'
            WHEN MIN(class_name) = N'banana' THEN N'COOKING_FUMES'
            ELSE N'MINOR_LEAK' END
FROM (SELECT induction_id, CAST(FLOOR(t * @windowsPerHour) AS INT) AS window_idx,
             r1, r2, temp_c, humidity, class_name, phase
      FROM RawGas
      WHERE r1 > 0 AND r2 > 0 AND temp_c IS NOT NULL AND humidity IS NOT NULL
        AND class_name IN (N'background', N'banana', N'wine')
        AND (class_name = N'background' OR phase IN (N'before', N'during'))) w
GROUP BY induction_id, window_idx
HAVING COUNT(*) >= @minRows
   AND (MIN(class_name) = N'background' OR COUNT(DISTINCT phase) = 1);
GO

-- quick checks: several hundred sessions in total and a few dozen per label at least
SELECT COUNT(*) AS total_sessions, COUNT(DISTINCT induction_id) AS recordings FROM PublicSession;
SELECT label, COUNT(*) AS n, AVG(mq2_raw) AS avg_mq2, AVG(mq135_raw) AS avg_mq135,
       AVG(temp_c) AS avg_temp_c, AVG(rh_pct) AS avg_rh_pct
FROM PublicSession GROUP BY label ORDER BY n DESC;
GO

-- to replay everything again with tool.DataReplayer later:
-- UPDATE PublicSession SET sent = 0;
