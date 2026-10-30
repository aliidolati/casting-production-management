-- Additive schema/manual upgrade. Automatic Flyway execution is NOT established.
-- Apply only after reconciling the existing users table; do not replay the incomplete old chain.
-- No permission codes, grants, startup backfills, or production data conversions are required.
CREATE TABLE IF NOT EXISTS performance_periods (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(160) NOT NULL,
    open_user_key VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_performance_open_user UNIQUE (open_user_key),
    KEY idx_performance_user_created (user_id, created_at),
    CONSTRAINT fk_performance_period_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS performance_criteria (
    id BINARY(16) NOT NULL PRIMARY KEY,
    period_id BINARY(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    maximum_score DECIMAL(12,2) NOT NULL,
    weight DECIMAL(12,2) NOT NULL,
    score DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    KEY idx_performance_criterion_period (period_id, created_at),
    CONSTRAINT fk_performance_criterion_period FOREIGN KEY (period_id) REFERENCES performance_periods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS performance_events (
    id BINARY(16) NOT NULL PRIMARY KEY,
    period_id BINARY(16) NOT NULL,
    criterion_id BINARY(16) NULL,
    action VARCHAR(40) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    actor_name VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,
    reason VARCHAR(2000) NULL,
    old_values MEDIUMTEXT NULL,
    new_values MEDIUMTEXT NULL,
    old_efficiency DECIMAL(5,4) NULL,
    new_efficiency DECIMAL(5,4) NULL,
    KEY idx_performance_event_period (period_id, occurred_at),
    CONSTRAINT fk_performance_event_period FOREIGN KEY (period_id) REFERENCES performance_periods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
