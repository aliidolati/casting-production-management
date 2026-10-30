-- زیرمرحله «منتظر شوت»، حذف VT تایید شده، تاریخ انجام فرآیند، بایگانی قطعات، و عیب سفارشی

UPDATE pieces
SET radiography_sub_stage = 'INTERPRETATION_FINALIZED'
WHERE radiography_sub_stage = 'VT_APPROVED';

UPDATE piece_sub_stage_history
SET sub_stage = 'INTERPRETATION_FINALIZED'
WHERE kind = 'RADIOGRAPHY'
  AND sub_stage = 'VT_APPROVED';

ALTER TABLE pieces
    ADD COLUMN process_date DATE NULL,
    ADD COLUMN archived_at DATETIME(6) NULL,
    ADD COLUMN archived_by VARCHAR(255) NULL;

ALTER TABLE radiography_defects
    ADD COLUMN defect_type_custom VARCHAR(255) NULL;

ALTER TABLE piece_welding_dispatches
    MODIFY COLUMN defect_type VARCHAR(255) NOT NULL,
    ADD COLUMN defect_type_custom VARCHAR(255) NULL,
    MODIFY COLUMN notes TEXT NULL;
