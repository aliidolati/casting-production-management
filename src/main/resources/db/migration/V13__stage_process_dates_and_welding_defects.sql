-- تاریخ انجام فرآیند به‌ازای هر مرحله، و پیوند نوبت جوشکاری به عیب‌های رادیوگرافی

CREATE TABLE piece_stage_process_dates (
    id BINARY(16) NOT NULL,
    piece_id BINARY(16) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    process_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_piece_stage_process_date UNIQUE (piece_id, stage),
    CONSTRAINT fk_piece_stage_process_date_piece
        FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE INDEX idx_piece_stage_process_date_piece ON piece_stage_process_dates (piece_id);

INSERT INTO piece_stage_process_dates (id, piece_id, stage, process_date)
SELECT UUID_TO_BIN(UUID()), p.id, p.current_stage, p.process_date
FROM pieces p
WHERE p.process_date IS NOT NULL;

CREATE TABLE piece_welding_dispatch_defects (
    dispatch_id BINARY(16) NOT NULL,
    defect_id BINARY(16) NOT NULL,
    PRIMARY KEY (dispatch_id, defect_id),
    CONSTRAINT fk_weld_dispatch_defects_dispatch
        FOREIGN KEY (dispatch_id) REFERENCES piece_welding_dispatches (id),
    CONSTRAINT fk_weld_dispatch_defects_defect
        FOREIGN KEY (defect_id) REFERENCES radiography_defects (id)
) ENGINE = InnoDB;
