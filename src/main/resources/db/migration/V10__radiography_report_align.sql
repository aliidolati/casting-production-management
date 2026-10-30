-- =====================================================================
-- هم‌راستاسازی قالب گزارش با فرم واقعی
--
-- LINE NO در فرم همان C1 / C2 / C3 است، نه شماره‌ی ردیف ۱ و ۲ و ۳.
-- Number Image مقدار عددی جدا (در نمونه ۱۳) است و برای هر لاین جداگانه ثبت می‌شود.
-- تاریخ / شماره گزارش / شماره درخواست روی خودِ گزارش پر می‌شوند.
-- =====================================================================

ALTER TABLE radiography_report_template_lines
    ADD COLUMN sort_order    INT         NOT NULL DEFAULT 0 AFTER template_id,
    ADD COLUMN number_image  INT         NULL AFTER course_code;

-- شماره‌ی ترتیبی نمایش همان line_no عددی قبلی است
UPDATE radiography_report_template_lines
SET sort_order = line_no,
    number_image = image_count;

ALTER TABLE radiography_report_template_lines
    DROP INDEX uk_rg_template_line_no;

ALTER TABLE radiography_report_template_lines
    DROP COLUMN line_no,
    DROP COLUMN image_count;

ALTER TABLE radiography_report_template_lines
    CHANGE COLUMN course_code line_no VARCHAR(50) NOT NULL;

ALTER TABLE radiography_report_template_lines
    ADD UNIQUE KEY uk_rg_template_line_no (template_id, line_no);

ALTER TABLE radiography_reports
    ADD COLUMN report_number  VARCHAR(80) NULL AFTER report_date,
    ADD COLUMN request_number VARCHAR(80) NULL AFTER report_number;
