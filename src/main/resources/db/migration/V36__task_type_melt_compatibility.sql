-- Conditional MySQL compatibility upgrade, after the existing tasks baseline and V35.
-- Old native ENUMs / Hibernate enum CHECKs can reject MELT_PASSPORT_* with error 1265/3819.
-- Only pure task_type IN (...) enum checks missing the new codes are removed.
-- Mixed business checks, foreign keys, task rows and all stored codes remain unchanged.
SET @old_task_type_checks = (
 SELECT GROUP_CONCAT(CONCAT('DROP CHECK `', REPLACE(tc.CONSTRAINT_NAME,'`','``'), '`') SEPARATOR ', ')
 FROM information_schema.TABLE_CONSTRAINTS tc
 JOIN information_schema.CHECK_CONSTRAINTS cc
   ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
 WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='tasks' AND tc.CONSTRAINT_TYPE='CHECK'
 AND REGEXP_LIKE(REPLACE(REPLACE(cc.CHECK_CLAUSE,'`',''), CONCAT(CHAR(92),CHAR(39)), CHAR(39)),
 '^[[:space:](]*task_type[[:space:]]+in[[:space:]]*[(]([[:space:]]*(_[[:alnum:]]+)?''[A-Z0-9_]+''[[:space:]]*,?)+[)][[:space:])]*$', 'i')
 AND (cc.CHECK_CLAUSE NOT LIKE '%MELT_PASSPORT_ENTRY%' OR cc.CHECK_CLAUSE NOT LIKE '%MELT_PASSPORT_APPROVAL%')
);
SET @task_type_check_ddl = IF(@old_task_type_checks IS NULL, 'DO 0', CONCAT('ALTER TABLE tasks ', @old_task_type_checks));
PREPARE task_type_check_stmt FROM @task_type_check_ddl;
EXECUTE task_type_check_stmt;
DEALLOCATE PREPARE task_type_check_stmt;
SET @legacy_task_type_enum = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND COLUMN_NAME='task_type' AND DATA_TYPE='enum');
SET @task_type_column_ddl = IF(@legacy_task_type_enum=0, 'DO 0', 'ALTER TABLE tasks MODIFY COLUMN task_type VARCHAR(48) NULL');
PREPARE task_type_column_stmt FROM @task_type_column_ddl;
EXECUTE task_type_column_stmt;
DEALLOCATE PREPARE task_type_column_stmt;
