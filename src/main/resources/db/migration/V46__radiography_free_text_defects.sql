-- Upgrades the existing radiography defect table, including rows created by older releases.
SET @rt_mode_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'radiography_defects' AND COLUMN_NAME = 'entry_mode'),
    'DO 0',
    'ALTER TABLE radiography_defects ADD COLUMN entry_mode VARCHAR(16) NOT NULL DEFAULT ''STRUCTURED'''
);
PREPARE rt_mode_stmt FROM @rt_mode_ddl;
EXECUTE rt_mode_stmt;
DEALLOCATE PREPARE rt_mode_stmt;

SET @rt_text_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'radiography_defects' AND COLUMN_NAME = 'free_text'),
    'DO 0',
    'ALTER TABLE radiography_defects ADD COLUMN free_text TEXT NULL'
);
PREPARE rt_text_stmt FROM @rt_text_ddl;
EXECUTE rt_text_stmt;
DEALLOCATE PREPARE rt_text_stmt;

ALTER TABLE radiography_defects
    MODIFY COLUMN defect_type VARCHAR(255) NULL,
    MODIFY COLUMN status VARCHAR(32) NULL;
