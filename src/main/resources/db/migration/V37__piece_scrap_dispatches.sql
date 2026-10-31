-- Additive only. Does not invent reasons or modify existing scrapped pieces/history.
CREATE TABLE IF NOT EXISTS piece_scrap_dispatches (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    piece_id BINARY(16) NOT NULL,
    active_piece_key BINARY(16) NULL,
    defect_type VARCHAR(255) NOT NULL,
    defect_type_custom VARCHAR(255) NULL,
    notes TEXT NULL,
    sent_at DATETIME(6) NULL,
    sent_by VARCHAR(255) NULL,
    origin_stage VARCHAR(32) NULL,
    origin_sub_stage VARCHAR(32) NULL,
    reversed_at DATETIME(6) NULL,
    reversed_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_scrap_active_piece UNIQUE (active_piece_key),
    INDEX idx_scrap_piece_sent (piece_id, sent_at),
    CONSTRAINT fk_scrap_piece FOREIGN KEY (piece_id) REFERENCES pieces(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS piece_scrap_dispatch_defects (
    dispatch_id BINARY(16) NOT NULL,
    defect_id BINARY(16) NOT NULL,
    PRIMARY KEY (dispatch_id, defect_id),
    CONSTRAINT fk_scrap_link_dispatch FOREIGN KEY (dispatch_id) REFERENCES piece_scrap_dispatches(id),
    CONSTRAINT fk_scrap_link_defect FOREIGN KEY (defect_id) REFERENCES radiography_defects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
