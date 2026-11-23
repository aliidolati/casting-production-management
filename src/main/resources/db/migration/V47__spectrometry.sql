-- Independent chemistry observations. Test date and piece do not form a unique key.
CREATE TABLE IF NOT EXISTS spectrometry_tests (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL,
    piece_id BINARY(16) NOT NULL,
    test_date DATE NOT NULL,
    al DECIMAL(13,10) NOT NULL,
    si DECIMAL(13,10) NOT NULL,
    mg DECIMAL(13,10) NOT NULL,
    cu DECIMAL(13,10) NOT NULL,
    fe DECIMAL(13,10) NOT NULL,
    mn DECIMAL(13,10) NOT NULL,
    zn DECIMAL(13,10) NOT NULL,
    ti DECIMAL(13,10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_spectrometry_test_date (test_date, id),
    KEY ix_spectrometry_piece_date (piece_id, test_date),
    CONSTRAINT fk_spectrometry_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A receipt survives deletion of its test rows and keeps retries idempotent.
CREATE TABLE IF NOT EXISTS spectrometry_batch_receipts (
    id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    request_id BINARY(16) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    row_count INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spectrometry_batch_request UNIQUE (actor_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
