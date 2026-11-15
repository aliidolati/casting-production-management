-- Preserve unclassified legacy closures. New dispatches record how they were closed.
SET @welding_has_completion = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'piece_welding_dispatches'
      AND COLUMN_NAME = 'process_completed'
);
SET @welding_completion_ddl = IF(@welding_has_completion = 0,
    'ALTER TABLE piece_welding_dispatches ADD COLUMN process_completed BIT(1) NULL',
    'DO 0');
PREPARE welding_completion_stmt FROM @welding_completion_ddl;
EXECUTE welding_completion_stmt;
DEALLOCATE PREPARE welding_completion_stmt;
