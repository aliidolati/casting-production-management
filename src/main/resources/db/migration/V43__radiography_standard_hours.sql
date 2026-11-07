-- Optional per-piece standard hours. Existing rows retain NULL.
-- The existence checks also allow execution after Hibernate schema update.
SET @has_rt_shot_hours = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_definitions' AND COLUMN_NAME='radiography_shot_hours');
SET @rt_shot_hours_ddl = IF(@has_rt_shot_hours=0,
 'ALTER TABLE piece_definitions ADD COLUMN radiography_shot_hours DECIMAL(10,2) NULL', 'DO 0');
PREPARE rt_shot_hours_stmt FROM @rt_shot_hours_ddl;
EXECUTE rt_shot_hours_stmt;
DEALLOCATE PREPARE rt_shot_hours_stmt;

SET @has_rt_interpretation_hours = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_definitions' AND COLUMN_NAME='radiography_interpretation_hours');
SET @rt_interpretation_hours_ddl = IF(@has_rt_interpretation_hours=0,
 'ALTER TABLE piece_definitions ADD COLUMN radiography_interpretation_hours DECIMAL(10,2) NULL', 'DO 0');
PREPARE rt_interpretation_hours_stmt FROM @rt_interpretation_hours_ddl;
EXECUTE rt_interpretation_hours_stmt;
DEALLOCATE PREPARE rt_interpretation_hours_stmt;
