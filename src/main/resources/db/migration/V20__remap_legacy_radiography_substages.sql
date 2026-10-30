-- اگر V19 روی داده‌های قدیمی اثر نگذاشته باشد، این مهاجرت همان نگاشت را دوباره اعمال می‌کند.

ALTER TABLE pieces
    MODIFY COLUMN radiography_sub_stage VARCHAR(32) NULL;

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
