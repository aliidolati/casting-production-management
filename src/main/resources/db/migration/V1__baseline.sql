-- =====================================================================
-- Baseline اسکیمای موجود پیش از ورود Flyway به پروژه.
--
-- روی دیتابیس‌هایی که از قبل داده دارند این فایل اجرا نمی‌شود؛
-- Flyway با baseline-on-migrate آن‌ها را مستقیماً روی نسخه ۱ علامت می‌زند.
-- این فایل فقط برای نصب‌های تازه (دیتابیس خالی) اجرا می‌شود تا ساختار
-- پایه دقیقاً مثل چیزی باشد که تا امروز Hibernate می‌ساخت.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id         BINARY(16)   NOT NULL,
    username   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    full_name  VARCHAR(255),
    role       VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_permissions (
    user_id    BINARY(16)  NOT NULL,
    permission VARCHAR(64),
    KEY idx_user_permissions_user (user_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS projects (
    id         BINARY(16)   NOT NULL,
    code       VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_code (code)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS piece_definitions (
    id                 BINARY(16)   NOT NULL,
    project_id         BINARY(16)   NOT NULL,
    row_num            INTEGER      NOT NULL,
    piece_name         VARCHAR(255) NOT NULL,
    drawing_image_path VARCHAR(255),
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_row_num (project_id, row_num),
    CONSTRAINT fk_piece_definitions_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS pieces (
    id                         BINARY(16)  NOT NULL,
    piece_definition_id        BINARY(16)  NOT NULL,
    piece_number               INTEGER     NOT NULL,
    current_stage              VARCHAR(32) NOT NULL,
    radiography_sub_stage      VARCHAR(255),
    heat_treatment_sub_stage   VARCHAR(32),
    created_at                 DATETIME(6) NOT NULL,
    description                TEXT,
    assign1_test_plate_name    VARCHAR(100),
    assign1_uts                VARCHAR(50),
    assign1_ys                 VARCHAR(50),
    assign1_hb                 VARCHAR(50),
    assign1_el                 VARCHAR(50),
    assign1_description        VARCHAR(20),
    assign2_test_plate_name    VARCHAR(100),
    assign2_uts                VARCHAR(50),
    assign2_ys                 VARCHAR(50),
    assign2_hb                 VARCHAR(50),
    assign2_el                 VARCHAR(50),
    assign2_description        VARCHAR(20),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_row_piece (piece_definition_id, piece_number),
    CONSTRAINT fk_pieces_definition FOREIGN KEY (piece_definition_id) REFERENCES piece_definitions (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS piece_stage_history (
    id         BINARY(16)  NOT NULL,
    piece_id   BINARY(16)  NOT NULL,
    stage      VARCHAR(32) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    changed_by VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_piece_stage_history_piece (piece_id),
    CONSTRAINT fk_piece_stage_history_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS piece_sub_stage_history (
    id         BINARY(16)  NOT NULL,
    piece_id   BINARY(16)  NOT NULL,
    kind       VARCHAR(32) NOT NULL,
    sub_stage  VARCHAR(64) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    changed_by VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_piece_sub_stage_history_piece (piece_id),
    CONSTRAINT fk_piece_sub_stage_history_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS piece_assignment_history (
    id                     BINARY(16)  NOT NULL,
    piece_id               BINARY(16)  NOT NULL,
    row_index              INTEGER     NOT NULL,
    test_plate_name        VARCHAR(100),
    uts                    VARCHAR(50),
    ys                     VARCHAR(50),
    hb                     VARCHAR(50),
    el                     VARCHAR(50),
    assignment_description VARCHAR(20),
    changed_at             DATETIME(6) NOT NULL,
    changed_by             VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_piece_assignment_history_piece (piece_id),
    CONSTRAINT fk_piece_assignment_history_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS radiography_defects (
    id                BINARY(16)   NOT NULL,
    piece_id          BINARY(16)   NOT NULL,
    course            VARCHAR(255) NOT NULL,
    defect_type       VARCHAR(255) NOT NULL,
    type_code         VARCHAR(255) NOT NULL,
    location_cm       INTEGER,
    status            VARCHAR(255) NOT NULL,
    welding_confirmed BIT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    recorded_by       VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_radiography_defects_piece (piece_id),
    CONSTRAINT fk_radiography_defects_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS piece_notes (
    id                  BINARY(16)  NOT NULL,
    piece_id            BINARY(16)  NOT NULL,
    content             TEXT        NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    created_by          VARCHAR(255),
    created_by_username VARCHAR(255),
    PRIMARY KEY (id),
    KEY idx_piece_notes_piece (piece_id),
    CONSTRAINT fk_piece_notes_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS welding_map_marks (
    id          BINARY(16)   NOT NULL,
    piece_id    BINARY(16)   NOT NULL,
    x_percent   DOUBLE       NOT NULL,
    y_percent   DOUBLE       NOT NULL,
    defect_type VARCHAR(255) NOT NULL,
    note        TEXT,
    created_by  VARCHAR(255),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_welding_map_marks_piece (piece_id),
    CONSTRAINT fk_welding_map_marks_piece FOREIGN KEY (piece_id) REFERENCES pieces (id)
) ENGINE = InnoDB;
