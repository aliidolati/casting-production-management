-- Additive manual upgrade for MySQL; Flyway execution is not established.
-- Preserve time_at610 DECIMAL unchanged: its historical numbers have no known clock conversion.
-- Safe after Hibernate ddl-auto=update and on repeated execution. Reconcile V29 first.
SET @has_melt_610_clock = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='melt_passports' AND COLUMN_NAME='time_at610_clock');
SET @melt_610_clock_ddl = IF(@has_melt_610_clock=0,
 'ALTER TABLE melt_passports ADD COLUMN time_at610_clock TIME(6) NULL', 'DO 0');
PREPARE melt_clock_stmt FROM @melt_610_clock_ddl;
EXECUTE melt_clock_stmt;
DEALLOCATE PREPARE melt_clock_stmt;
