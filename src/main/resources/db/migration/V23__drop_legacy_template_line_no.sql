-- =====================================================================
-- حذف ستون قدیمی line_no از radiography_report_template_lines
--
-- Entity فعلی فقط course_code را می‌شناسد. روی بعضی دیتابیس‌ها ستون قدیمی
-- line_no (NOT NULL و بدون مقدار پیش‌فرض) هنوز مانده و هر INSERT جدید با
-- خطای "Field 'line_no' doesn't have a default value" رد می‌شود.
-- این اسکریپت idempotent است و اگر line_no وجود نداشته باشد کاری نمی‌کند.
-- =====================================================================

SET @has_line_no = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'line_no'
);
SET @has_course_code = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'course_code'
);

-- فقط line_no هست: همان را به course_code تغییر نام می‌دهیم
SET @sql = IF(@has_line_no = 1 AND @has_course_code = 0,
    'ALTER TABLE radiography_report_template_lines CHANGE COLUMN line_no course_code VARCHAR(50) NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- هر دو هستند: مقدار line_no را برای سطرهای خالی course_code کپی می‌کنیم
SET @sql = IF(@has_line_no = 1 AND @has_course_code = 1,
    'UPDATE radiography_report_template_lines SET course_code = CAST(line_no AS CHAR) WHERE course_code IS NULL OR course_code = ''''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- وضعیت را دوباره می‌خوانیم (ممکن است ستون تغییر نام داده باشد)
SET @has_line_no = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'line_no'
);

-- کلید خارجی template_id به یک ایندکس نیاز دارد؛ قبل از حذف ایندکس یکتای قدیمی یکی موقت می‌سازیم
SET @has_helper_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'idx_rg_template_line_template'
);
SET @sql = IF(@has_line_no = 1 AND @has_helper_index = 0,
    'ALTER TABLE radiography_report_template_lines ADD INDEX idx_rg_template_line_template (template_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ایندکس یکتای قدیمی (که روی line_no است) حذف می‌شود
SET @old_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'uk_rg_template_line_no'
      AND column_name = 'line_no'
);
SET @sql = IF(@old_unique > 0,
    'ALTER TABLE radiography_report_template_lines DROP INDEX uk_rg_template_line_no',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- خود ستون حذف می‌شود
SET @sql = IF(@has_line_no = 1,
    'ALTER TABLE radiography_report_template_lines DROP COLUMN line_no',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- یکتایی (template_id, course_code) برقرار شود
SET @new_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'uk_rg_template_line_no'
      AND column_name = 'course_code'
);
SET @sql = IF(@new_unique = 0,
    'ALTER TABLE radiography_report_template_lines ADD UNIQUE KEY uk_rg_template_line_no (template_id, course_code)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Entity برای image_count رشته (VARCHAR(50)) می‌خواهد؛ اگر هنوز عددی است اصلاح شود
SET @image_count_type = (
    SELECT DATA_TYPE FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'image_count'
);
SET @sql = IF(@image_count_type IS NOT NULL AND @image_count_type <> 'varchar',
    'ALTER TABLE radiography_report_template_lines MODIFY COLUMN image_count VARCHAR(50) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ایندکس موقت دیگر لازم نیست
SET @has_helper_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'idx_rg_template_line_template'
);
SET @sql = IF(@has_helper_index > 0,
    'ALTER TABLE radiography_report_template_lines DROP INDEX idx_rg_template_line_template',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
