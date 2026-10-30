-- تاریخ انجام فرآیند به‌ازای هر زیرمرحله (تا اصلاح تاریخ یک زیرمرحله روی زیرمرحله‌ی دیگر اثر نگذارد)

CREATE TABLE IF NOT EXISTS piece_sub_stage_process_dates (
    id BINARY(16) NOT NULL,
    piece_id BINARY(16) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    sub_stage VARCHAR(64) NOT NULL,
    process_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_piece_sub_stage_process_date UNIQUE (piece_id, kind, sub_stage),
    CONSTRAINT fk_piece_sub_stage_process_date_piece
        FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;
