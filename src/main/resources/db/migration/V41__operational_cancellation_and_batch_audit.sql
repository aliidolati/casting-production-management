-- Category 5 additive upgrade; no legacy dispatch snapshot is invented.
SET @p5_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='heat_treatment_batches' AND COLUMN_NAME='version');
SET @p5_ddl = IF(@p5_has=0,'ALTER TABLE heat_treatment_batches ADD COLUMN version BIGINT NOT NULL DEFAULT 0','DO 0');
PREPARE p5_stmt FROM @p5_ddl;
EXECUTE p5_stmt;
DEALLOCATE PREPARE p5_stmt;
SET @p5_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_welding_dispatches' AND COLUMN_NAME='origin_process_date');
SET @p5_ddl = IF(@p5_has=0,'ALTER TABLE piece_welding_dispatches ADD COLUMN origin_process_date DATE NULL','DO 0');
PREPARE p5_stmt FROM @p5_ddl;
EXECUTE p5_stmt;
DEALLOCATE PREPARE p5_stmt;
SET @p5_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_welding_dispatches' AND COLUMN_NAME='dispatch_piece_version');
SET @p5_ddl = IF(@p5_has=0,'ALTER TABLE piece_welding_dispatches ADD COLUMN dispatch_piece_version BIGINT NULL','DO 0');
PREPARE p5_stmt FROM @p5_ddl;
EXECUTE p5_stmt;
DEALLOCATE PREPARE p5_stmt;
SET @p5_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_welding_dispatches' AND COLUMN_NAME='cancelled_at');
SET @p5_ddl = IF(@p5_has=0,'ALTER TABLE piece_welding_dispatches ADD COLUMN cancelled_at DATETIME(6) NULL','DO 0');
PREPARE p5_stmt FROM @p5_ddl;
EXECUTE p5_stmt;
DEALLOCATE PREPARE p5_stmt;
SET @p5_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_welding_dispatches' AND COLUMN_NAME='cancelled_by');
SET @p5_ddl = IF(@p5_has=0,'ALTER TABLE piece_welding_dispatches ADD COLUMN cancelled_by VARCHAR(255) NULL','DO 0');
PREPARE p5_stmt FROM @p5_ddl;
EXECUTE p5_stmt;
DEALLOCATE PREPARE p5_stmt;
SET @p5_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='piece_welding_dispatches' AND COLUMN_NAME='cancellation_reason');
SET @p5_ddl = IF(@p5_has=0,'ALTER TABLE piece_welding_dispatches ADD COLUMN cancellation_reason VARCHAR(2000) NULL','DO 0');
PREPARE p5_stmt FROM @p5_ddl;
EXECUTE p5_stmt;
DEALLOCATE PREPARE p5_stmt;
ALTER TABLE melt_passports MODIFY COLUMN status VARCHAR(32) NOT NULL;
CREATE TABLE IF NOT EXISTS heat_batch_events (
 id BINARY(16) NOT NULL PRIMARY KEY,
 batch_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NOT NULL,
 actor_name VARCHAR(255) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 before_state LONGTEXT NOT NULL,
 after_state LONGTEXT NOT NULL,
 affected_pieces LONGTEXT NOT NULL,
 INDEX idx_heat_batch_event_time(batch_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
