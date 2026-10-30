-- ستون‌های پروفایل کاربر برای ماژول وظایف (idempotent؛ اگر Hibernate قبلاً ساخته باشد کاری نمی‌کند).
-- جدول‌های وظایف (tasks, task_comments, task_checklist_items, task_activities) و ستون‌های تازه‌ی
-- system_notifications را Hibernate با ddl-auto=update می‌سازد و تبدیل ENUM→VARCHAR را TaskSchemaRepair
-- انجام می‌دهد؛ این فایل فقط برای ثبت صریح تغییرات جدول موجود users است.

SET @c1 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'department');
SET @d1 = IF(@c1 = 0, 'ALTER TABLE users ADD COLUMN department VARCHAR(100) NULL', 'DO 0');
PREPARE s1 FROM @d1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c2 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'disabled');
SET @d2 = IF(@c2 = 0, 'ALTER TABLE users ADD COLUMN disabled BIT NOT NULL DEFAULT 0', 'DO 0');
PREPARE s2 FROM @d2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @c3 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'last_activity_at');
SET @d3 = IF(@c3 = 0, 'ALTER TABLE users ADD COLUMN last_activity_at DATETIME(6) NULL', 'DO 0');
PREPARE s3 FROM @d3; EXECUTE s3; DEALLOCATE PREPARE s3;
