-- Melt passports. Schema reference / manual upgrade for the existing MySQL installation.
-- Flyway execution is NOT established in this project; do not replay the incomplete old chain.
-- Hibernate ddl-auto=update also creates these entities. TaskSchemaRepair already converts
-- user_permissions.permission to VARCHAR(64); no automatic APPROVE_MELT_PASSPORT grant.
-- Historical casting rows do not receive fabricated approved passports.
SET @has_piece_version = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pieces' AND COLUMN_NAME='version');
SET @piece_version_ddl = IF(@has_piece_version=0,
 'ALTER TABLE pieces ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'DO 0');
PREPARE melt_stmt FROM @piece_version_ddl; EXECUTE melt_stmt; DEALLOCATE PREPARE melt_stmt;

-- Same compatibility conversion as TaskSchemaRepair, for controlled manual upgrades.
SET @melt_permission_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_permissions' AND COLUMN_NAME='permission');
SET @melt_permission_ddl = IF(@melt_permission_type='enum',
 'ALTER TABLE user_permissions MODIFY COLUMN permission VARCHAR(64) NOT NULL', 'DO 0');
PREPARE melt_stmt FROM @melt_permission_ddl; EXECUTE melt_stmt; DEALLOCATE PREPARE melt_stmt;

CREATE TABLE IF NOT EXISTS melt_materials (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    category VARCHAR(100) NOT NULL,
    active BIT(1) NOT NULL DEFAULT 1,
    UNIQUE KEY uk_melt_material_normalized (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS melt_passports (
    id BINARY(16) NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    number VARCHAR(48) NOT NULL,
    status VARCHAR(32) NOT NULL,
    melt_date DATE NULL,
    furnace_number INT NULL,
    ingot_type VARCHAR(160) NULL,
    primary_batch VARCHAR(160) NULL,
    recycled_batch VARCHAR(160) NULL,
    primary_weight DECIMAL(24,8) NULL,
    recycled_weight DECIMAL(24,8) NULL,
    injection_operator VARCHAR(160) NULL,
    notes VARCHAR(4000) NULL,
    additional_notes VARCHAR(4000) NULL,
    superheat_temperature DECIMAL(24,8) NULL,
    argon_injection_duration DECIMAL(24,8) NULL,
    time_at610 DECIMAL(24,8) NULL,
    spiral_test DECIMAL(24,8) NULL,
    pouring_temperature DECIMAL(24,8) NULL,
    pressure_holding_time DECIMAL(24,8) NULL,
    argon_humidity_percent DECIMAL(24,8) NULL,
    returned_melt_weight DECIMAL(24,8) NULL,
    rotary_flow DECIMAL(24,8) NULL,
    rotary_duration DECIMAL(24,8) NULL,
    rotary_speed DECIMAL(24,8) NULL,
    rotary_temperature DECIMAL(24,8) NULL,
    flux_injection_duration DECIMAL(24,8) NULL,
    lance_outlet_diameter DECIMAL(24,8) NULL,
    injected_coverall_weight DECIMAL(24,8) NULL,
    injection_start_time TIME(6) NULL,
    nozzle_lock VARCHAR(16) NULL,
    nozzle_full BIT(1) NULL,
    rpt_sample_status VARCHAR(255) NULL,
    runner_pipe_status VARCHAR(255) NULL,
    lance_number VARCHAR(255) NULL,
    injected_coverall_type VARCHAR(255) NULL,
    mg DECIMAL(24,8) NULL,
    mg_rotary DECIMAL(24,8) NULL,
    mg610 DECIMAL(24,8) NULL,
    mg_ar DECIMAL(24,8) NULL,
    mg_b DECIMAL(24,8) NULL,
    mg_w DECIMAL(24,8) NULL,
    magnesium_result DECIMAL(38,12) NULL,
    magnesium_calculated_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    created_by_id BINARY(16) NULL,
    created_by VARCHAR(255) NULL,
    updated_by_id BINARY(16) NULL,
    updated_by VARCHAR(255) NULL,
    submitted_at DATETIME(6) NULL,
    submitted_by_id BINARY(16) NULL,
    submitted_by VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    approved_by_id BINARY(16) NULL,
    approved_by VARCHAR(255) NULL,
    returned_at DATETIME(6) NULL,
    returned_by_id BINARY(16) NULL,
    returned_by VARCHAR(255) NULL,
    correction_reason VARCHAR(2000) NULL,
    UNIQUE KEY uk_melt_number (number),
    KEY idx_melt_status_created (status, created_at),
    KEY idx_melt_date_furnace (melt_date, furnace_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS melt_passport_pieces (
    id BINARY(16) NOT NULL PRIMARY KEY,
    passport_id BINARY(16) NOT NULL,
    piece_id BINARY(16) NOT NULL,
    active_piece_key BINARY(16) NULL,
    piece_name VARCHAR(255) NOT NULL,
    piece_ref VARCHAR(255) NOT NULL,
    UNIQUE KEY uk_melt_active_piece (active_piece_key),
    UNIQUE KEY uk_melt_passport_piece (passport_id, piece_id),
    KEY idx_melt_link_piece (piece_id),
    KEY idx_melt_link_passport (passport_id),
    CONSTRAINT fk_melt_link_passport FOREIGN KEY (passport_id) REFERENCES melt_passports(id),
    CONSTRAINT fk_melt_link_piece FOREIGN KEY (piece_id) REFERENCES pieces(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS melt_consumptions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    passport_id BINARY(16) NOT NULL,
    material_id BINARY(16) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    material_name VARCHAR(160) NOT NULL,
    category VARCHAR(100) NOT NULL,
    quantity DECIMAL(24,8) NULL,
    unit VARCHAR(16) NOT NULL,
    base_quantity DECIMAL(38,12) NULL,
    base_unit VARCHAR(16) NOT NULL,
    added_at TIME(6) NULL,
    batch_number VARCHAR(160) NULL,
    notes VARCHAR(2000) NULL,
    KEY idx_melt_consumption_material (material_id),
    KEY idx_melt_consumption_passport (passport_id),
    CONSTRAINT fk_melt_consumption_passport FOREIGN KEY (passport_id) REFERENCES melt_passports(id),
    CONSTRAINT fk_melt_consumption_material FOREIGN KEY (material_id) REFERENCES melt_materials(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS melt_checks (
    id BINARY(16) NOT NULL PRIMARY KEY,
    passport_id BINARY(16) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    notes VARCHAR(2000) NULL,
    UNIQUE KEY uk_melt_check_kind (passport_id,kind),
    KEY idx_melt_check_passport (passport_id),
    CONSTRAINT fk_melt_check_passport FOREIGN KEY (passport_id) REFERENCES melt_passports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS melt_passport_events (
    id BINARY(16) NOT NULL PRIMARY KEY,
    passport_id BINARY(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id BINARY(16) NULL,
    actor_name VARCHAR(255) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    reason VARCHAR(2000) NULL,
    KEY idx_melt_event_passport_time (passport_id,occurred_at),
    CONSTRAINT fk_melt_event_passport FOREIGN KEY (passport_id) REFERENCES melt_passports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

