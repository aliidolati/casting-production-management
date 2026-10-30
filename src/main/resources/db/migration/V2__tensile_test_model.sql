-- =====================================================================
-- مدل جدید تست کشش
--
-- جایگزین مدل قدیمی «حداکثر دو انتساب هارد‌کدشده» روی جدول pieces می‌شود:
--   * هر قطعه می‌تواند هر تعداد تست کشش داشته باشد
--   * هر تست از چند نمونه تشکیل می‌شود (فعلاً UI دقیقاً دو نمونه می‌گیرد)
--   * هر تست دقیقاً یک رکورد تعیین‌تکلیف دارد
--   * حداقل پارامترهای تاییدی در سطح تعریف قطعه نگه‌داری می‌شوند
--
-- طبق تایید کارفرما، داده‌ی قدیمی تعیین‌تکلیف ارزشی ندارد و حذف می‌شود.
-- =====================================================================

CREATE TABLE tensile_tests (
    id                 BINARY(16)   NOT NULL,
    piece_id           BINARY(16)   NOT NULL,
    -- شماره ترتیبی تست برای همان قطعه (۱، ۲، ۳ ...)
    test_index         INT          NOT NULL,
    -- PENDING_REVIEW / APPROVED / REJECTED / RETEST_REQUIRED
    status             VARCHAR(32)  NOT NULL,
    -- پرچم هشدار خودکار: حداقل یکی از مقادیر یکی از نمونه‌ها زیر آستانه بوده
    below_threshold    BIT          NOT NULL DEFAULT 0,
    -- فهرست پارامترهای مردود برای گزارش‌گیری، مثلاً "UTS,EL"
    threshold_failures VARCHAR(64),
    created_at         DATETIME(6)  NOT NULL,
    created_by         VARCHAR(255),
    -- زمان تعیین‌تکلیف؛ اختلافش با created_at معیار سرعت پاسخ‌گویی مسئول کشش است
    reviewed_at        DATETIME(6),
    reviewed_by        VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tensile_piece_index (piece_id, test_index),
    KEY idx_tensile_status (status),
    KEY idx_tensile_below_threshold (below_threshold),
    CONSTRAINT fk_tensile_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE TABLE tensile_test_samples (
    id              BINARY(16)    NOT NULL,
    tensile_test_id BINARY(16)    NOT NULL,
    -- شماره ترتیبی نمونه داخل تست (۱ و ۲)
    sample_index    INT           NOT NULL,
    -- شماره نمونه که اپراتور وارد می‌کند، مثلاً "C4"
    sample_label    VARCHAR(20),
    uts             DECIMAL(10, 2),
    ys              DECIMAL(10, 2),
    el              DECIMAL(10, 2),
    hb              DECIMAL(10, 2),
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tensile_sample_index (tensile_test_id, sample_index),
    CONSTRAINT fk_tensile_sample_test FOREIGN KEY (tensile_test_id)
        REFERENCES tensile_tests (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE tensile_test_dispositions (
    id                  BINARY(16)   NOT NULL,
    -- دقیقاً یک تعیین‌تکلیف به ازای هر تست؛ ویرایش مجدد همین رکورد را بازنویسی می‌کند
    tensile_test_id     BINARY(16)   NOT NULL,
    disposition_date    DATE         NOT NULL,
    -- پارامترهای عملیات حرارتی مجدد تجویزشده
    quantity            INT,
    duration_hours      DECIMAL(6, 2),
    temperature_c       DECIMAL(7, 2),
    -- RE_OPERATION / CONCESSION_APPROVAL / SCRAP / NEXT_TENSILE / AGING
    next_stage          VARCHAR(32)  NOT NULL,
    -- ACCEPTED / REJECTED
    inspection_result   VARCHAR(16)  NOT NULL,
    responsible_user_id BINARY(16),
    notes               TEXT,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6),
    created_by          VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tensile_disposition_test (tensile_test_id),
    KEY idx_tensile_disposition_next_stage (next_stage),
    CONSTRAINT fk_tensile_disposition_test FOREIGN KEY (tensile_test_id)
        REFERENCES tensile_tests (id) ON DELETE CASCADE,
    CONSTRAINT fk_tensile_disposition_user FOREIGN KEY (responsible_user_id)
        REFERENCES users (id)
) ENGINE = InnoDB;

-- حداقل پارامترهای تاییدی، مشترک بین همه‌ی قطعات یک تعریف قطعه
ALTER TABLE piece_definitions
    ADD COLUMN min_uts DECIMAL(10, 2) NULL,
    ADD COLUMN min_ys  DECIMAL(10, 2) NULL,
    ADD COLUMN min_el  DECIMAL(10, 2) NULL,
    ADD COLUMN min_hb  DECIMAL(10, 2) NULL;

-- ---------------------------------------------------------------------
-- برچیدن مدل قدیمی
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS piece_assignment_history;

-- ستون‌های قدیمی روی pieces به‌صورت شرطی حذف می‌شوند؛ MySQL برای DROP COLUMN
-- از IF EXISTS پشتیبانی نمی‌کند، پس دستور از روی information_schema ساخته می‌شود
-- تا اگر بعضی ستون‌ها (مثل ستون‌های بدون پیشوند نسخه‌های خیلی قدیمی) وجود
-- نداشته باشند، migration شکست نخورد.
SET SESSION group_concat_max_len = 8192;

SET @drop_list = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN `', COLUMN_NAME, '`') SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pieces'
      AND COLUMN_NAME IN (
          'assign1_test_plate_name', 'assign1_uts', 'assign1_ys',
          'assign1_hb', 'assign1_el', 'assign1_description',
          'assign2_test_plate_name', 'assign2_uts', 'assign2_ys',
          'assign2_hb', 'assign2_el', 'assign2_description',
          'test_plate_name', 'uts', 'ys', 'hb', 'el', 'assignment_description'
      )
);

SET @ddl = IF(@drop_list IS NULL, 'DO 0', CONCAT('ALTER TABLE `pieces` ', @drop_list));
PREPARE drop_legacy_assignment_columns FROM @ddl;
EXECUTE drop_legacy_assignment_columns;
DEALLOCATE PREPARE drop_legacy_assignment_columns;
