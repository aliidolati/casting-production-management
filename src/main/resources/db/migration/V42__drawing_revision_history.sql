CREATE TABLE IF NOT EXISTS drawing_revisions (
 id BINARY(16) NOT NULL PRIMARY KEY,
 definition_id BINARY(16) NOT NULL,
 revision_number INT NOT NULL,
 relative_path VARCHAR(255) NOT NULL,
 original_name VARCHAR(255) NULL,
 uploaded_at DATETIME(6) NULL,
 captured_at DATETIME(6) NOT NULL,
 uploaded_by VARCHAR(255) NULL,
 source_revision_id BINARY(16) NULL,
 UNIQUE KEY uk_drawing_revision_number(definition_id,revision_number),
 CONSTRAINT fk_drawing_revision_definition FOREIGN KEY(definition_id) REFERENCES piece_definitions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET @drawing_has_revision = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='welding_map_marks' AND COLUMN_NAME='drawing_revision_id');
SET @drawing_revision_ddl = IF(@drawing_has_revision=0,'ALTER TABLE welding_map_marks ADD COLUMN drawing_revision_id BINARY(16) NULL','DO 0');
PREPARE drawing_revision_stmt FROM @drawing_revision_ddl;
EXECUTE drawing_revision_stmt;
DEALLOCATE PREPARE drawing_revision_stmt;
