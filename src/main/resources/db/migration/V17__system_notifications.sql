-- اعلان‌های سیستمی (فعلاً تایید خودکار تست کشش) و وضعیت خوانده‌شدن به‌ازای هر کاربر.
-- خودِ رویداد برای همهٔ مسئولان کشش مشترک است؛ خوانده‌شدن یکی، خوانده‌شدن دیگری را عوض نمی‌کند.

CREATE TABLE IF NOT EXISTS system_notifications (
    id              BINARY(16)    NOT NULL,
    type            VARCHAR(64)   NOT NULL,
    message         VARCHAR(512)  NOT NULL,
    piece_id        BINARY(16)    NULL,
    tensile_test_id BINARY(16)    NULL,
    piece_name      VARCHAR(255)  NOT NULL,
    piece_number    INT           NOT NULL,
    project_code    VARCHAR(255)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_notification_tensile_test (tensile_test_id),
    KEY idx_system_notifications_created_at (created_at),
    KEY idx_system_notifications_type (type),
    CONSTRAINT fk_system_notification_piece FOREIGN KEY (piece_id)
        REFERENCES pieces (id) ON DELETE SET NULL,
    CONSTRAINT fk_system_notification_tensile FOREIGN KEY (tensile_test_id)
        REFERENCES tensile_tests (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS system_notification_reads (
    id              BINARY(16)   NOT NULL,
    notification_id BINARY(16)   NOT NULL,
    user_id         BINARY(16)   NOT NULL,
    read_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_notification_read_user (notification_id, user_id),
    KEY idx_system_notification_reads_user (user_id),
    CONSTRAINT fk_system_notification_read_notif FOREIGN KEY (notification_id)
        REFERENCES system_notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_system_notification_read_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
