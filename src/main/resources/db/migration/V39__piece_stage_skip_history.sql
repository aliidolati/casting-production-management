-- Independent normal-advance and reasoned forward-skip access.
-- No existing user's permissions are granted/regranted automatically.
CREATE TABLE IF NOT EXISTS piece_stage_skips (
    id BINARY(16) NOT NULL PRIMARY KEY,
    piece_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    actor_name VARCHAR(255) NOT NULL,
    from_stage VARCHAR(32) NOT NULL,
    from_sub_stage VARCHAR(64) NULL,
    to_stage VARCHAR(32) NOT NULL,
    to_sub_stage VARCHAR(64) NULL,
    reason VARCHAR(1000) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_stage_skip_piece_time (piece_id, occurred_at),
    CONSTRAINT fk_stage_skip_piece FOREIGN KEY (piece_id) REFERENCES pieces(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
