-- =====================================================================
-- فیلدهای نهایی عملیات حرارتی
--
-- تقسیم فیلدها بین دو سطح، مطابق واقعیت فیزیکی سیکل کوره:
--   * محموله (heat_treatment_batches): تاریخ، دما، زمان، سایز کوره
--     همه‌ی قطعات یک محموله در یک سیکل مشترک پخته می‌شوند.
--   * قطعه (piece_heat_treatments): شماره و سختی تست پلیت
--     هر قطعه تست پلیت مخصوص خودش را دارد.
--
-- دما و زمان با مقادیر تجویزشده در تعیین‌تکلیف کشش پیش‌پر می‌شوند ولی روی
-- محموله ذخیره می‌شوند، تا (۱) عملیات‌های مسیر عادی تولید هم مقدار داشته باشند
-- و (۲) ویرایش بعدی تجویز، تاریخچه‌ی اجرای واقعی را تغییر ندهد. اختلاف این دو
-- خودش یک گزارش مفید است: «تجویز در مقابل اجرا».
-- =====================================================================

ALTER TABLE heat_treatment_batches
    ADD COLUMN treatment_date DATE          NULL AFTER batch_code,
    ADD COLUMN temperature_c  DECIMAL(7, 2) NULL,
    ADD COLUMN duration_hours DECIMAL(6, 2) NULL,
    -- ONE_METER / TWO_METER / TWO_METER_C
    ADD COLUMN furnace_size   VARCHAR(32)   NULL;

-- رکوردهای آزمایشی مرحله‌ی قبل تاریخ ندارند؛ تاریخ ثبتشان مبنا قرار می‌گیرد
-- تا بتوان ستون را اجباری کرد بدون اینکه migration روی داده‌ی موجود بشکند.
UPDATE heat_treatment_batches
SET treatment_date = DATE(created_at)
WHERE treatment_date IS NULL;

ALTER TABLE heat_treatment_batches
    MODIFY COLUMN treatment_date DATE NOT NULL;

-- گزارش «قطعاتی که در یک بازه‌ی زمانی مشخص عملیات شده‌اند» روی این ایندکس می‌نشیند
CREATE INDEX idx_heat_treatment_batch_date ON heat_treatment_batches (treatment_date);
CREATE INDEX idx_heat_treatment_batch_furnace ON heat_treatment_batches (furnace_size);

ALTER TABLE piece_heat_treatments
    -- شماره تست پلیت مثل شماره نمونه‌ی کشش، یک برچسب کوتاه است (مثلاً "C4")
    ADD COLUMN test_plate_label       VARCHAR(20)    NULL,
    -- سختی تست پلیت بر حسب HB، هم‌مقیاس با ستون hb در tensile_test_samples
    ADD COLUMN test_plate_hardness_hb DECIMAL(10, 2) NULL;
