-- Category 1: preserve legacy role codes and all existing accounts; no automatic promotion.
-- Select the initial manager explicitly using APP_INITIAL_MANAGER_USERNAME at application upgrade.
ALTER TABLE users MODIFY COLUMN role VARCHAR(32) NOT NULL;

CREATE TABLE IF NOT EXISTS user_access_events (
    id BINARY(16) NOT NULL PRIMARY KEY,
    actor_id BINARY(16) NULL,
    actor_username VARCHAR(255) NOT NULL,
    target_id BINARY(16) NOT NULL,
    action VARCHAR(40) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    before_state TEXT NOT NULL,
    after_state TEXT NOT NULL,
    INDEX idx_user_access_target_time (target_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
