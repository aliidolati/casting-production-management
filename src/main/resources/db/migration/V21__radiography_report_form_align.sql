-- =====================================================================
-- هم‌راستاسازی گزارش رادیوگرافی با فرم نمونه
--
-- این اسکریپت هم نصب تازه (فقط ستون‌های قدیمی) و هم حالتی را که
-- Hibernate ستون‌های جدید را کنار ستون‌های قدیمی ساخته پوشش می‌دهد.
-- =====================================================================

-- course_code
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
SET @sql = IF(@has_line_no = 1 AND @has_course_code = 0,
    'ALTER TABLE radiography_report_template_lines CHANGE COLUMN line_no course_code VARCHAR(50) NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
SET @sql = IF(@has_line_no = 1 AND @has_course_code = 1,
    'UPDATE radiography_report_template_lines SET course_code = line_no WHERE line_no IS NOT NULL AND line_no <> '''' AND (course_code IS NULL OR course_code = '''')',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- کلید خارجی template_id از همین ایندکس یکتا استفاده می‌کند؛ اول ایندکس جدا می‌سازیم.
SET @has_fk_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'idx_rg_template_line_template'
);
SET @has_old_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'uk_rg_template_line_no'
      AND column_name = 'line_no'
);
SET @sql = IF(@has_old_index > 0 AND @has_fk_index = 0,
    'ALTER TABLE radiography_report_template_lines ADD INDEX idx_rg_template_line_template (template_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_old_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'uk_rg_template_line_no'
      AND column_name = 'line_no'
);
SET @sql = IF(@has_old_index > 0,
    'ALTER TABLE radiography_report_template_lines DROP INDEX uk_rg_template_line_no',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_line_no = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'line_no'
);
SET @sql = IF(@has_line_no = 1,
    'ALTER TABLE radiography_report_template_lines DROP COLUMN line_no',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- image_count
SET @has_number_image = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'number_image'
);
SET @has_image_count = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'image_count'
);
SET @sql = IF(@has_number_image = 1 AND @has_image_count = 0,
    'ALTER TABLE radiography_report_template_lines CHANGE COLUMN number_image image_count VARCHAR(50) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_number_image = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'number_image'
);
SET @has_image_count = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'image_count'
);
SET @sql = IF(@has_number_image = 1 AND @has_image_count = 1,
    'UPDATE radiography_report_template_lines SET image_count = CAST(number_image AS CHAR) WHERE number_image IS NOT NULL AND (image_count IS NULL OR image_count = '''')',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_number_image = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'number_image'
);
SET @sql = IF(@has_number_image = 1,
    'ALTER TABLE radiography_report_template_lines DROP COLUMN number_image',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- th
SET @has_thickness = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'thickness_mm'
);
SET @has_th = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'th'
);
SET @sql = IF(@has_thickness = 1 AND @has_th = 0,
    'ALTER TABLE radiography_report_template_lines ADD COLUMN th VARCHAR(50) NULL AFTER acc_criteria',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_thickness = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'thickness_mm'
);
SET @has_th = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'th'
);
SET @sql = IF(@has_thickness = 1 AND @has_th = 1,
    'UPDATE radiography_report_template_lines SET th = TRIM(TRAILING ''.'' FROM TRIM(TRAILING ''0'' FROM CAST(thickness_mm AS CHAR))) WHERE thickness_mm IS NOT NULL AND (th IS NULL OR th = '''')',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_thickness = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND column_name = 'thickness_mm'
);
SET @sql = IF(@has_thickness = 1,
    'ALTER TABLE radiography_report_template_lines DROP COLUMN thickness_mm',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_new_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'uk_rg_template_line_no'
);
SET @sql = IF(@has_new_index = 0,
    'ALTER TABLE radiography_report_template_lines ADD UNIQUE KEY uk_rg_template_line_no (template_id, course_code)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'idx_rg_template_line_template'
);
SET @has_course_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_report_template_lines'
      AND index_name = 'uk_rg_template_line_no'
      AND column_name = 'course_code'
);
SET @sql = IF(@has_fk_index > 0 AND @has_course_unique > 0,
    'ALTER TABLE radiography_report_template_lines DROP INDEX idx_rg_template_line_template',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- header snapshot and status
SET @has_part_name = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_reports'
      AND column_name = 'part_name'
);
SET @sql = IF(@has_part_name = 0,
    'ALTER TABLE radiography_reports ADD COLUMN part_name VARCHAR(255) NULL AFTER request_number, ADD COLUMN project_code VARCHAR(255) NULL AFTER part_name, ADD COLUMN reference_standard VARCHAR(255) NULL AFTER project_code, ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''DRAFT'' AFTER reference_standard',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE radiography_reports r
    JOIN pieces p ON p.id = r.piece_id
    JOIN piece_definitions pd ON pd.id = p.piece_definition_id
    JOIN projects pr ON pr.id = pd.project_id
SET r.part_name = pd.piece_name
WHERE r.part_name IS NULL OR r.part_name = '';

UPDATE radiography_reports r
    JOIN pieces p ON p.id = r.piece_id
    JOIN piece_definitions pd ON pd.id = p.piece_definition_id
    JOIN projects pr ON pr.id = pd.project_id
SET r.project_code = pr.code
WHERE r.project_code IS NULL OR r.project_code = '';

UPDATE radiography_reports
SET status = 'DRAFT'
WHERE status IS NULL OR status = '';

ALTER TABLE radiography_reports
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

UPDATE radiography_report_lines
SET result = 'A.C.C'
WHERE result = 'ACC';

UPDATE radiography_reports r
SET r.status = 'IN_INTERPRETATION'
WHERE r.status = 'DRAFT'
  AND (
      EXISTS (
          SELECT 1 FROM radiography_report_lines l
          WHERE l.report_id = r.id
            AND (
                (l.iqi_real IS NOT NULL AND l.iqi_real <> '')
                OR (l.result IS NOT NULL AND l.result <> '')
            )
      )
      OR EXISTS (
          SELECT 1
          FROM radiography_report_lines l
          JOIN radiography_defects d ON d.report_line_id = l.id
          WHERE l.report_id = r.id
      )
  );

-- structured defect location
SET @has_start = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'radiography_defects'
      AND column_name = 'start_cm'
);
SET @sql = IF(@has_start = 0,
    'ALTER TABLE radiography_defects ADD COLUMN start_cm INT NULL AFTER location_cm, ADD COLUMN end_cm INT NULL AFTER start_cm, ADD COLUMN description VARCHAR(1000) NULL AFTER end_cm',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE radiography_defects
SET start_cm = location_cm,
    end_cm = location_cm
WHERE location_cm IS NOT NULL
  AND start_cm IS NULL;
