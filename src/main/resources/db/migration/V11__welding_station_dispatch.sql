-- ایستگاه جوشکاری مستقل از مراحل خط تولید.
-- قطعه فقط با ثبت نوع عیب و توضیحات به این ایستگاه می‌رود؛
-- پس از اتمام فرآیند به رادیوگرافی / منتظر ری‌شوت برمی‌گردد.

CREATE TABLE piece_welding_dispatches (
    id            BINARY(16)   NOT NULL,
    piece_id      BINARY(16)   NOT NULL,
    defect_type   VARCHAR(32)  NOT NULL,
    notes         TEXT         NOT NULL,
    sent_at       DATETIME(6)  NOT NULL,
    sent_by       VARCHAR(255),
    completed_at  DATETIME(6)  NULL,
    completed_by  VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_welding_dispatch_piece (piece_id),
    CONSTRAINT fk_welding_dispatch_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;
