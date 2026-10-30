-- ایندکس برای گزارش‌های تست کشش که بر اساس بازه‌ی زمان ثبت تست فیلتر و مرتب می‌شوند
-- (گزارش روند تست کشش و Quality Index). فقط در صورت نبودن ساخته می‌شود.

SET @has_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'tensile_tests'
      AND index_name = 'idx_tensile_created_at'
);
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE tensile_tests ADD INDEX idx_tensile_created_at (created_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
