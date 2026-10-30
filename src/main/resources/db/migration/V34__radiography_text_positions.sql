-- Widen position values to text without discarding existing numeric locations.
-- Apply to the existing JPA-managed chain schema; automatic Flyway execution is not assumed.
-- Repeating these MODIFY statements preserves text and numeric strings alike.
ALTER TABLE radiography_defects
    MODIFY COLUMN start_cm VARCHAR(255) NULL,
    MODIFY COLUMN end_cm VARCHAR(255) NULL,
    MODIFY COLUMN location_cm VARCHAR(255) NULL;

ALTER TABLE radiography_defect_positions
    MODIFY COLUMN start_cm VARCHAR(255) NOT NULL,
    MODIFY COLUMN end_cm VARCHAR(255) NOT NULL;
