-- =====================================================================
-- اسکلت لاگ عملیات حرارتی
--
-- این جداول مستقل از piece_sub_stage_history هستند و آن را جایگزین نمی‌کنند:
--   * piece_sub_stage_history  → لاگ «جابه‌جایی وضعیت» (قطعه کِی از زیرمرحله‌ای به زیرمرحله‌ی بعد رفت)
--   * heat_treatment_batches   → یک محموله‌ی کوره؛ چند قطعه با هم در یک سیکل
--   * piece_heat_treatments    → حضور یک قطعه‌ی مشخص در یک محموله (لاگ «عملیات واقعی»)
--
-- فیلدهای تخصصی (دما، مدت، نوع عملیات و ...) هنوز نهایی نشده‌اند و در
-- migration بعدی اضافه می‌شوند. فعلاً فقط ستون‌های پایه‌ی امن و یک فیلد
-- متنی باز ساخته می‌شود تا ساختار قابل توسعه بماند.
-- =====================================================================

CREATE TABLE heat_treatment_batches (
    id         BINARY(16)  NOT NULL,
    -- شناسه‌ی محموله که کاربر وارد می‌کند، مثلاً "HT-1404-012"
    batch_code VARCHAR(50) NOT NULL,
    notes      TEXT,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(255),
    PRIMARY KEY (id),
    -- هر محموله فقط یک بار ساخته می‌شود؛ ثبت قطعه‌ی بعدی همین رکورد را پیدا می‌کند
    UNIQUE KEY uk_heat_treatment_batch_code (batch_code),
    KEY idx_heat_treatment_batch_created_at (created_at)
) ENGINE = InnoDB;

CREATE TABLE piece_heat_treatments (
    id                     BINARY(16)  NOT NULL,
    piece_id               BINARY(16)  NOT NULL,
    batch_id               BINARY(16)  NOT NULL,
    -- اگر این عملیات نتیجه‌ی تجویز یک تعیین‌تکلیف کشش باشد، اینجا به آن وصل می‌شود.
    -- با این لینک می‌توان «فاصله‌ی بین تجویز و اجرا» و «نرخ قبولی کشش بعد از عملیات» را گزارش گرفت.
    tensile_disposition_id BINARY(16),
    details                TEXT,
    created_at             DATETIME(6) NOT NULL,
    created_by             VARCHAR(255),
    PRIMARY KEY (id),
    -- یک قطعه نمی‌تواند دو بار در یک محموله ثبت شود
    UNIQUE KEY uk_piece_heat_treatment (batch_id, piece_id),
    KEY idx_piece_heat_treatment_piece (piece_id),
    KEY idx_piece_heat_treatment_disposition (tensile_disposition_id),
    CONSTRAINT fk_piece_heat_treatment_piece FOREIGN KEY (piece_id)
        REFERENCES pieces (id),
    CONSTRAINT fk_piece_heat_treatment_batch FOREIGN KEY (batch_id)
        REFERENCES heat_treatment_batches (id),
    CONSTRAINT fk_piece_heat_treatment_disposition FOREIGN KEY (tensile_disposition_id)
        REFERENCES tensile_test_dispositions (id) ON DELETE SET NULL
) ENGINE = InnoDB;
