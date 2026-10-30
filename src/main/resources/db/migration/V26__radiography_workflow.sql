-- گردش‌کار تفسیر و تأیید رادیوگرافی (idempotent؛ اگر Hibernate قبلاً ساختار را ساخته باشد، بی‌اثر است).
-- مثل V24: Flyway در این پروژه خودکار اجرا نمی‌شود و ساختار عملاً با Hibernate ddl-auto=update ساخته می‌شود؛
-- این فایل مستند ساختار و قابل اجرای دستی است. همه‌ی ستون‌های افزوده‌شده به جدول‌های موجود NULL‌پذیرند
-- تا ردیف‌های قبلی (وظایف عادی و کاربران بدون امضا) دست‌نخورده بمانند.

-- ---------------------------------------------------------------- tasks: نوع وظیفه + ارجاع به گردش‌کار
SET @c1 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'task_type');
SET @d1 = IF(@c1 = 0, 'ALTER TABLE tasks ADD COLUMN task_type VARCHAR(48) NULL', 'DO 0');
PREPARE s1 FROM @d1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c2 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'workflow_id');
SET @d2 = IF(@c2 = 0, 'ALTER TABLE tasks ADD COLUMN workflow_id BINARY(16) NULL', 'DO 0');
PREPARE s2 FROM @d2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ---------------------------------------------------------------- users: امضای پروفایل
SET @c3 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'signature_path');
SET @d3 = IF(@c3 = 0, 'ALTER TABLE users ADD COLUMN signature_path VARCHAR(255) NULL', 'DO 0');
PREPARE s3 FROM @d3; EXECUTE s3; DEALLOCATE PREPARE s3;

SET @c4 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'signature_content_type');
SET @d4 = IF(@c4 = 0, 'ALTER TABLE users ADD COLUMN signature_content_type VARCHAR(64) NULL', 'DO 0');
PREPARE s4 FROM @d4; EXECUTE s4; DEALLOCATE PREPARE s4;

SET @c5 = (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'signature_updated_at');
SET @d5 = IF(@c5 = 0, 'ALTER TABLE users ADD COLUMN signature_updated_at DATETIME(6) NULL', 'DO 0');
PREPARE s5 FROM @d5; EXECUTE s5; DEALLOCATE PREPARE s5;

-- ---------------------------------------------------------------- گردش‌کار
-- open_key = شناسه‌ی قطعه تا وقتی گردش‌کار باز است و NULL پس از پایان؛ ستون یکتا تضمین می‌کند
-- برای هر قطعه حداکثر یک گردش‌کار باز وجود دارد (چند NULL مجازند).
CREATE TABLE IF NOT EXISTS radiography_workflows (
    id                 BINARY(16)   NOT NULL PRIMARY KEY,
    piece_id           BINARY(16)   NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    interpreter_id     BINARY(16)   NOT NULL,
    assigned_by_id     BINARY(16)   NOT NULL,
    assigned_at        DATETIME(6)  NOT NULL,
    interpreter_task_id BINARY(16)  NULL,
    manager_task_id    BINARY(16)   NULL,
    round              INT          NOT NULL,
    rejection_reason   TEXT         NULL,
    rejected_at        DATETIME(6)  NULL,
    rejected_by        VARCHAR(255) NULL,
    open_key           VARCHAR(36)  NULL,
    completed_at       DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT uk_rg_workflow_open_piece UNIQUE (open_key),
    KEY idx_rg_workflow_piece (piece_id),
    KEY idx_rg_workflow_interpreter (interpreter_id),
    KEY idx_rg_workflow_status (status),
    CONSTRAINT fk_rg_workflow_piece FOREIGN KEY (piece_id) REFERENCES pieces (id) ON DELETE CASCADE,
    CONSTRAINT fk_rg_workflow_interpreter FOREIGN KEY (interpreter_id) REFERENCES users (id),
    CONSTRAINT fk_rg_workflow_assigned_by FOREIGN KEY (assigned_by_id) REFERENCES users (id),
    CONSTRAINT fk_rg_workflow_itask FOREIGN KEY (interpreter_task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_rg_workflow_mtask FOREIGN KEY (manager_task_id) REFERENCES tasks (id) ON DELETE SET NULL
);

-- تأییدها/ردها: فقط افزودنی؛ هویت و نقش تأییدکننده در لحظه‌ی ثبت کپی می‌شود و امضا فایل مستقل خودش را دارد.
CREATE TABLE IF NOT EXISTS radiography_workflow_approvals (
    id                     BINARY(16)   NOT NULL PRIMARY KEY,
    workflow_id            BINARY(16)   NOT NULL,
    piece_id               BINARY(16)   NOT NULL,
    report_version_id      BINARY(16)   NULL,
    approval_type          VARCHAR(16)  NOT NULL,
    decision               VARCHAR(16)  NOT NULL,
    round                  INT          NOT NULL,
    approver_user_id       BINARY(16)   NOT NULL,
    approver_username      VARCHAR(255) NOT NULL,
    approver_name          VARCHAR(255) NOT NULL,
    approver_role          VARCHAR(32)  NOT NULL,
    approved_at            DATETIME(6)  NOT NULL,
    rejection_reason       TEXT         NULL,
    signature_path         VARCHAR(255) NULL,
    signature_content_type VARCHAR(64)  NULL,
    KEY idx_rg_approval_workflow (workflow_id),
    KEY idx_rg_approval_piece (piece_id),
    CONSTRAINT fk_rg_approval_workflow FOREIGN KEY (workflow_id) REFERENCES radiography_workflows (id) ON DELETE CASCADE
);

-- نسخه‌های تغییرناپذیر گزارش (ORIGINAL / INTERPRETER_APPROVED / MANAGER_FINAL) با SHA-256 محتوا
CREATE TABLE IF NOT EXISTS radiography_report_versions (
    id           BINARY(16)   NOT NULL PRIMARY KEY,
    workflow_id  BINARY(16)   NOT NULL,
    piece_id     BINARY(16)   NOT NULL,
    version_no   INT          NOT NULL,
    kind         VARCHAR(32)  NOT NULL,
    snapshot     MEDIUMTEXT   NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    created_by   VARCHAR(255) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_rg_version_piece_no UNIQUE (piece_id, version_no),
    KEY idx_rg_version_workflow (workflow_id),
    CONSTRAINT fk_rg_version_workflow FOREIGN KEY (workflow_id) REFERENCES radiography_workflows (id) ON DELETE CASCADE
);

-- رویدادهای ممیزی گردش‌کار (فقط افزودنی)
CREATE TABLE IF NOT EXISTS radiography_workflow_events (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    workflow_id   BINARY(16)   NOT NULL,
    piece_id      BINARY(16)   NOT NULL,
    action        VARCHAR(48)  NOT NULL,
    actor_user_id BINARY(16)   NULL,
    actor_name    VARCHAR(255) NULL,
    old_value     VARCHAR(255) NULL,
    new_value     VARCHAR(255) NULL,
    reason        TEXT         NULL,
    created_at    DATETIME(6)  NOT NULL,
    KEY idx_rg_event_workflow_created (workflow_id, created_at),
    KEY idx_rg_event_piece (piece_id),
    CONSTRAINT fk_rg_event_workflow FOREIGN KEY (workflow_id) REFERENCES radiography_workflows (id) ON DELETE CASCADE
);
