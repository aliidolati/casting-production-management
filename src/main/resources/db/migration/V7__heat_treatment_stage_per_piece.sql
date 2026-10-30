-- =====================================================================
-- انتقال «مرحله» از محموله به رکورد قطعه
--
-- در V6 این ستون روی محموله گذاشته شده بود، با این برداشت که مرحله‌ی سیکل کوره
-- است. برداشت درست این است که «چندمین بار است که روی همین قطعه عملیات انجام
-- می‌شود» (مرحله اول، دوم، سوم و ...)، پس ذاتاً مخصوص هر قطعه است نه محموله:
-- ممکن است دو قطعه در یک محموله باشند ولی برای یکی مرحله‌ی اول و برای دیگری
-- مرحله‌ی سوم باشد.
--
-- مقدارهای ثبت‌شده‌ی احتمالی به رکوردهای قطعه‌ی همان محموله کپی می‌شوند تا
-- داده‌ای از دست نرود، بعد ستون قدیمی حذف می‌شود.
-- =====================================================================

ALTER TABLE piece_heat_treatments
    ADD COLUMN treatment_stage VARCHAR(50) NULL AFTER batch_id;

UPDATE piece_heat_treatments pht
    JOIN heat_treatment_batches b ON b.id = pht.batch_id
SET pht.treatment_stage = b.treatment_stage
WHERE b.treatment_stage IS NOT NULL;

DROP INDEX idx_heat_treatment_batch_stage ON heat_treatment_batches;

ALTER TABLE heat_treatment_batches
    DROP COLUMN treatment_stage;

-- گزارش «قطعاتی که بیش از یک مرحله عملیات خورده‌اند» روی این ایندکس می‌نشیند
CREATE INDEX idx_piece_heat_treatment_stage ON piece_heat_treatments (treatment_stage);
