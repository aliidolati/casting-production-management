package com.factory.productmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * دیتابیس‌های قدیمی شرکت بعضی ستون‌ها را به‌صورت MySQL ENUM دارند.
 * Hibernate ddl-auto=update آن‌ها را عوض نمی‌کند و اگر Flyway هم آن migration
 * را اعمال نکرده باشد، مقدار جدیدی مثل AWAITING_SHOT با Data truncated رد می‌شود.
 * این کلاس در هر استارت، فقط همان ستون‌های ENUM مانده را به VARCHAR تبدیل می‌کند.
 */
@Component
@Order(1)
public class LegacyMysqlEnumRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMysqlEnumRepair.class);

    private final JdbcTemplate jdbcTemplate;

    public LegacyMysqlEnumRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        convertIfEnum("pieces", "radiography_sub_stage", "VARCHAR(32) NULL");
        convertIfEnum("piece_sub_stage_history", "kind", "VARCHAR(32) NOT NULL");
        convertIfEnum("users", "role", "VARCHAR(32) NOT NULL");
        convertIfEnum("radiography_defects", "status", "VARCHAR(32) NOT NULL");
        allowNullIfRequired("piece_welding_dispatches", "notes", "TEXT NULL");
        remapLegacyRadiographyValues();
    }

    /**
     * اگر Flyway قبلاً به‌عنوان موفق ثبت شده ولی ردیف‌های قدیمی عوض نشده باشند،
     * همین استارت مقادیر INTERPRETATION_FINALIZED و AWAITING_RESHOOT را نگاشت می‌کند.
     */
    private void remapLegacyRadiographyValues() {
        if (!columnExists("pieces", "radiography_sub_stage")) {
            return;
        }
        int movedToHeat = jdbcTemplate.update("""
                UPDATE pieces
                SET current_stage = 'HEAT_TREATMENT',
                    heat_treatment_sub_stage = 'AWAITING_OPERATIONS',
                    radiography_sub_stage = 'INTERPRETATION'
                WHERE current_stage = 'RADIOGRAPHY'
                  AND radiography_sub_stage = 'INTERPRETATION_FINALIZED'
                """);
        int toShot = jdbcTemplate.update("""
                UPDATE pieces
                SET radiography_sub_stage = 'SHOT'
                WHERE radiography_sub_stage IN ('AWAITING_SHOT', 'AWAITING_RESHOOT')
                """);
        int toInterpretation = jdbcTemplate.update("""
                UPDATE pieces
                SET radiography_sub_stage = 'INTERPRETATION'
                WHERE radiography_sub_stage IN (
                    'AWAITING_INTERPRETATION', 'INTERPRETATION_REJECTED', 'INTERPRETATION_FINALIZED'
                )
                """);
        int historyChanged = 0;
        if (columnExists("piece_sub_stage_history", "sub_stage")) {
            historyChanged += jdbcTemplate.update("""
                    UPDATE piece_sub_stage_history
                    SET sub_stage = 'SHOT'
                    WHERE sub_stage IN ('AWAITING_SHOT', 'AWAITING_RESHOOT')
                    """);
            historyChanged += jdbcTemplate.update("""
                    UPDATE piece_sub_stage_history
                    SET sub_stage = 'INTERPRETATION'
                    WHERE sub_stage IN (
                        'AWAITING_INTERPRETATION', 'INTERPRETATION_REJECTED', 'INTERPRETATION_FINALIZED'
                    )
                    """);
        }
        int weldingChanged = 0;
        if (columnExists("piece_welding_dispatches", "origin_radiography_sub_stage")) {
            weldingChanged += jdbcTemplate.update("""
                    UPDATE piece_welding_dispatches
                    SET origin_radiography_sub_stage = 'SHOT'
                    WHERE origin_radiography_sub_stage IN ('AWAITING_SHOT', 'AWAITING_RESHOOT')
                    """);
            weldingChanged += jdbcTemplate.update("""
                    UPDATE piece_welding_dispatches
                    SET origin_radiography_sub_stage = 'INTERPRETATION'
                    WHERE origin_radiography_sub_stage IN (
                        'AWAITING_INTERPRETATION', 'INTERPRETATION_REJECTED', 'INTERPRETATION_FINALIZED'
                    )
                    """);
        }
        int total = movedToHeat + toShot + toInterpretation + historyChanged + weldingChanged;
        if (total > 0) {
            log.warn(
                    "مقادیر قدیمی رادیوگرافی نگاشت شد: heat={}, shot={}, interpretation={}, history={}, welding={}",
                    movedToHeat,
                    toShot,
                    toInterpretation,
                    historyChanged,
                    weldingChanged
            );
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                table,
                column
        );
        return count != null && count > 0;
    }

    private void convertIfEnum(String table, String column, String varcharDdl) {
        String dataType = jdbcTemplate.query(
                """
                SELECT DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                table,
                column
        );
        if (dataType == null || !"enum".equalsIgnoreCase(dataType)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + varcharDdl);
        log.warn("ستون {}.{} از ENUM به {} تبدیل شد تا مقدارهای جدید قابل ذخیره باشند", table, column, varcharDdl);
    }

    private void allowNullIfRequired(String table, String column, String ddl) {
        String nullable = jdbcTemplate.query(
                """
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                table,
                column
        );
        if (!"NO".equalsIgnoreCase(nullable)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + ddl);
        log.warn("ستون {}.{} برای NULL مجاز شد", table, column);
    }
}
