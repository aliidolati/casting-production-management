-- Additive manual MySQL upgrade; reconcile V29/V30 first. Do not assume Flyway runs.
-- Nullable columns preserve all existing events; repeatable after Hibernate schema update.
SET @has_melt_old_values = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='melt_passport_events' AND COLUMN_NAME='old_values');
SET @melt_old_values_ddl = IF(@has_melt_old_values=0,
 'ALTER TABLE melt_passport_events ADD COLUMN old_values MEDIUMTEXT NULL', 'DO 0');
PREPARE melt_old_values_stmt FROM @melt_old_values_ddl;
EXECUTE melt_old_values_stmt;
DEALLOCATE PREPARE melt_old_values_stmt;
SET @has_melt_new_values = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='melt_passport_events' AND COLUMN_NAME='new_values');
SET @melt_new_values_ddl = IF(@has_melt_new_values=0,
 'ALTER TABLE melt_passport_events ADD COLUMN new_values MEDIUMTEXT NULL', 'DO 0');
PREPARE melt_new_values_stmt FROM @melt_new_values_ddl;
EXECUTE melt_new_values_stmt;
DEALLOCATE PREPARE melt_new_values_stmt;
