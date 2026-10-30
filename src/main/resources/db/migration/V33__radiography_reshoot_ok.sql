-- Additive MySQL upgrade for the existing JPA-managed radiography_defects table.
-- Flyway execution is not assumed. Safe to repeat after Hibernate schema update.
-- NULL preserves old rows and means no reshoot result. No data is deleted/backfilled.
SET @has_radiography_reshoot_ok = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='radiography_defects' AND COLUMN_NAME='reshoot_ok');
SET @radiography_reshoot_ok_ddl = IF(@has_radiography_reshoot_ok=0,
 'ALTER TABLE radiography_defects ADD COLUMN reshoot_ok BIT NULL', 'DO 0');
PREPARE radiography_reshoot_ok_stmt FROM @radiography_reshoot_ok_ddl;
EXECUTE radiography_reshoot_ok_stmt;
DEALLOCATE PREPARE radiography_reshoot_ok_stmt;
