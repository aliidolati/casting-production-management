-- مبدأ جوشکاری برای بازگشت به همان مرحله/زیرمرحله، و مهاجرت زیرمرحله‌های قدیمی رادیوگرافی.

ALTER TABLE pieces
    MODIFY COLUMN radiography_sub_stage VARCHAR(32) NULL;

ALTER TABLE piece_welding_dispatches
    ADD COLUMN origin_stage VARCHAR(32) NULL,
    ADD COLUMN origin_radiography_sub_stage VARCHAR(32) NULL,
    ADD COLUMN origin_heat_treatment_sub_stage VARCHAR(32) NULL,
    ADD COLUMN origin_rough_machining_sub_stage VARCHAR(32) NULL;

-- تفسیر نهایی‌شده یعنی تایید شده؛ این قطعات به منتظر عملیات می‌روند.
UPDATE pieces
SET current_stage = 'HEAT_TREATMENT',
    heat_treatment_sub_stage = 'AWAITING_OPERATIONS',
    radiography_sub_stage = 'INTERPRETATION'
WHERE current_stage = 'RADIOGRAPHY'
  AND radiography_sub_stage = 'INTERPRETATION_FINALIZED';

UPDATE pieces
SET radiography_sub_stage = 'SHOT'
WHERE radiography_sub_stage IN ('AWAITING_SHOT', 'AWAITING_RESHOOT');

UPDATE pieces
SET radiography_sub_stage = 'INTERPRETATION'
WHERE radiography_sub_stage IN ('AWAITING_INTERPRETATION', 'INTERPRETATION_REJECTED', 'INTERPRETATION_FINALIZED');
