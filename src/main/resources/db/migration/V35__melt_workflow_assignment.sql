-- Additive manual MySQL upgrade after V29/V32. Flyway execution is not assumed.
-- Keep historical passports unassigned; ADMIN can assign existing drafts explicitly.
-- Existing tasks.task_type is VARCHAR(48), workflow_id is the existing BINARY(16) reference.
SET @melt_has_operator_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='melt_passports' AND COLUMN_NAME='assigned_operator_id');
SET @melt_operator_id_ddl = IF(@melt_has_operator_id=0,
 'ALTER TABLE melt_passports ADD COLUMN assigned_operator_id BINARY(16) NULL', 'DO 0');
PREPARE melt_operator_id_stmt FROM @melt_operator_id_ddl;
EXECUTE melt_operator_id_stmt;
DEALLOCATE PREPARE melt_operator_id_stmt;
SET @melt_has_operator_name = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='melt_passports' AND COLUMN_NAME='assigned_operator_name');
SET @melt_operator_name_ddl = IF(@melt_has_operator_name=0,
 'ALTER TABLE melt_passports ADD COLUMN assigned_operator_name VARCHAR(255) NULL', 'DO 0');
PREPARE melt_operator_name_stmt FROM @melt_operator_name_ddl;
EXECUTE melt_operator_name_stmt;
DEALLOCATE PREPARE melt_operator_name_stmt;
