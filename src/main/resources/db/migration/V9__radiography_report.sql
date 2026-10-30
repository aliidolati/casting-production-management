-- =====================================================================
-- گزارش آزمایش رادیوگرافی
--
-- ساختار دو لایه است:
--   * قالب  (radiography_report_templates)  → روی «ردیف قطعه» تعریف می‌شود،
--     چون هندسه‌ی قطعه (کورس‌ها، ضخامت، IQI انتظاری) بین همه‌ی قطعات آن ردیف
--     مشترک است. یک ردیف می‌تواند چند قالب با نام‌های متفاوت داشته باشد.
--   * گزارش (radiography_reports)            → برای هر قطعه‌ی فیزیکی، یکی
--     به‌ازای هر قالب. مقادیر واقعی آزمون اینجا پر می‌شوند.
--
-- بقیه‌ی اجزای فرم (شماره استاندارد مرجع، راهنمای اختصارات، مشخصات فیلم و
-- دستگاه، بلوک امضاها) متن ثابت‌اند و در قالب چاپ رندر می‌شوند، نه در دیتابیس.
-- =====================================================================

CREATE TABLE radiography_report_templates (
    id                  BINARY(16)   NOT NULL,
    piece_definition_id BINARY(16)   NOT NULL,
    -- نام دلخواه که کاربر برای تفکیک قالب‌های یک ردیف انتخاب می‌کند
    name                VARCHAR(150) NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rg_template_name (piece_definition_id, name),
    CONSTRAINT fk_rg_template_definition FOREIGN KEY (piece_definition_id)
        REFERENCES piece_definitions (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE radiography_report_template_lines (
    id           BINARY(16)   NOT NULL,
    template_id  BINARY(16)   NOT NULL,
    -- LINE NO
    line_no      INT          NOT NULL,
    -- image NO، مثلاً C1؛ با فیلد course در radiography_defects هم‌معناست
    course_code  VARCHAR(50)  NOT NULL,
    -- Number: تعداد عکس‌های گرفته‌شده در این کورس
    image_count  INT,
    -- IQI NO. / ABS: مقدار انتظاری که هنگام تعریف قطعه مشخص می‌شود
    iqi_abs      VARCHAR(50),
    -- A.C.C Criteria، مثلاً B
    acc_criteria VARCHAR(50),
    -- TH: ضخامت این بخش بر حسب میلی‌متر
    thickness_mm DECIMAL(8, 2),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rg_template_line_no (template_id, line_no),
    CONSTRAINT fk_rg_template_line_template FOREIGN KEY (template_id)
        REFERENCES radiography_report_templates (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE radiography_reports (
    id          BINARY(16)  NOT NULL,
    piece_id    BINARY(16)  NOT NULL,
    template_id BINARY(16)  NOT NULL,
    -- تاریخ آزمون که اپراتور ثبت می‌کند
    report_date DATE,
    created_at  DATETIME(6) NOT NULL,
    created_by  VARCHAR(255),
    updated_at  DATETIME(6),
    updated_by  VARCHAR(255),
    PRIMARY KEY (id),
    -- هر قطعه به‌ازای هر قالب فقط یک گزارش دارد؛ ثبت مجدد همین رکورد را ویرایش می‌کند
    UNIQUE KEY uk_rg_report_piece_template (piece_id, template_id),
    KEY idx_rg_report_piece (piece_id),
    CONSTRAINT fk_rg_report_piece FOREIGN KEY (piece_id)
        REFERENCES pieces (id) ON DELETE CASCADE,
    CONSTRAINT fk_rg_report_template FOREIGN KEY (template_id)
        REFERENCES radiography_report_templates (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE radiography_report_lines (
    id               BINARY(16)  NOT NULL,
    report_id        BINARY(16)  NOT NULL,
    template_line_id BINARY(16)  NOT NULL,
    -- IQI NO. / REAL: مقدار واقعی که هنگام صدور گزارش ثبت می‌شود
    iqi_real         VARCHAR(50),
    -- RESULT: یکی از RS / RT / RP / RJ / ACC
    result           VARCHAR(20),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rg_report_line (report_id, template_line_id),
    CONSTRAINT fk_rg_report_line_report FOREIGN KEY (report_id)
        REFERENCES radiography_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_rg_report_line_template_line FOREIGN KEY (template_line_id)
        REFERENCES radiography_report_template_lines (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- عیوبی که از داخل جدول گزارش ثبت می‌شوند به لاین مربوطه وصل می‌مانند.
-- ON DELETE SET NULL است تا با حذف گزارش، خودِ عیب و پیگیری «نیاز به جوش» آن
-- از بین نرود و به‌صورت عیب سطح قطعه باقی بماند.
ALTER TABLE radiography_defects
    ADD COLUMN report_line_id BINARY(16) NULL AFTER piece_id,
    ADD KEY idx_radiography_defects_report_line (report_line_id),
    ADD CONSTRAINT fk_radiography_defects_report_line FOREIGN KEY (report_line_id)
        REFERENCES radiography_report_lines (id) ON DELETE SET NULL;

-- یکسان‌سازی اختصارات عیب با راهنمای پایین فرم گزارش، تا یک واژگان واحد
-- بین ثبت عیب و خروجی چاپی وجود داشته باشد.
UPDATE radiography_defects SET defect_type = 'S.P' WHERE defect_type = 'Sh.P';
UPDATE radiography_defects SET defect_type = 'S.C' WHERE defect_type = 'Sh.C';
UPDATE radiography_defects SET defect_type = 'CR' WHERE defect_type = 'Crack';
UPDATE radiography_defects SET defect_type = 'G' WHERE defect_type = 'G.H';
UPDATE radiography_defects SET defect_type = 'FML' WHERE defect_type = 'F.M.L';
UPDATE radiography_defects SET defect_type = 'FMM' WHERE defect_type = 'F.M.M';
-- فرم، تخلخل گازی را به گرد (GPR) و کشیده (GPE) تفکیک کرده است. رکوردهای
-- قدیمی این تفکیک را ندارند و به GPR نگاشت می‌شوند.
UPDATE radiography_defects SET defect_type = 'GPR' WHERE defect_type = 'G.P';
