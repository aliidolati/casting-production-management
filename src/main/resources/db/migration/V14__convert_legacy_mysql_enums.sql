-- =====================================================================
-- تبدیل ENUMهای ساخته‌شده توسط Hibernate قدیمی به VARCHAR
--
-- دیتابیس شرکت (حدود ۲۵ مرداد ۱۴۰۴ / آگوست ۲۰۲۶) این ستون‌ها را به‌صورت
-- MySQL ENUM دارد. مقدار جدید AWAITING_SHOT و kind=ROUGH_MACHINING در آن
-- ENUMها نیست؛ بدون این تبدیل، ثبت زیرمرحله‌ی جدید با خطای
-- "Data truncated" شکست می‌خورد. خودِ داده‌ها دست نمی‌خورند.
-- =====================================================================

ALTER TABLE piece_sub_stage_history
    MODIFY COLUMN kind VARCHAR(32) NOT NULL;

ALTER TABLE pieces
    MODIFY COLUMN radiography_sub_stage VARCHAR(32) NULL;

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(32) NOT NULL;

ALTER TABLE radiography_defects
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
