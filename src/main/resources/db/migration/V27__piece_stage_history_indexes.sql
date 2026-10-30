-- ایندکس برای کوئری‌های داشبورد (آخرین/اولین تغییر مرحله هر قطعه، ورودهای یک مرحله در بازه، آخرین حرکت‌ها).
-- هر ایندکس فقط در صورت نبودن ساخته می‌شود تا اجرای مجدد/دیتابیس‌های متفاوت خطا ندهد.

SET @has_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'piece_stage_history'
      AND index_name = 'idx_psh_piece_changed'
);
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE piece_stage_history ADD INDEX idx_psh_piece_changed (piece_id, changed_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'piece_stage_history'
      AND index_name = 'idx_psh_stage_changed'
);
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE piece_stage_history ADD INDEX idx_psh_stage_changed (stage, changed_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'piece_stage_history'
      AND index_name = 'idx_psh_changed_at'
);
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE piece_stage_history ADD INDEX idx_psh_changed_at (changed_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
