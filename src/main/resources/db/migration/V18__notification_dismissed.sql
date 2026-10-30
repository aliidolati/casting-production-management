-- هر کاربر می‌تواند اعلان را برای خودش پاک کند؛ پاک‌کردن یکی، اعلان دیگری را حذف نمی‌کند.
-- اگر Hibernate قبلاً ستون را ساخته باشد این دستور رد می‌شود.

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'system_notification_reads'
      AND COLUMN_NAME = 'dismissed'
);

SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE system_notification_reads ADD COLUMN dismissed BIT NOT NULL DEFAULT 0',
    'DO 0'
);

PREPARE add_dismissed_column FROM @ddl;
EXECUTE add_dismissed_column;
DEALLOCATE PREPARE add_dismissed_column;
