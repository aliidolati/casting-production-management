CREATE TABLE IF NOT EXISTS record_edit_events (
 id BINARY(16) NOT NULL PRIMARY KEY,
 kind VARCHAR(30) NOT NULL,
 target_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NOT NULL,
 actor_username VARCHAR(255) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 before_state LONGTEXT NOT NULL,
 after_state LONGTEXT NOT NULL,
 INDEX idx_record_edit_target (kind,target_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
