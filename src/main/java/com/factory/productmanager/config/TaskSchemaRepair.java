package com.factory.productmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * سازگارسازی دیتابیس‌های موجود با ماژول وظایف (با همان روش LegacyMysqlEnumRepair).
 * <ul>
 *   <li>ستون‌های ENUM قدیمیِ {@code user_permissions.permission} و {@code system_notifications.type}
 *       به VARCHAR تبدیل می‌شوند؛ وگرنه ذخیره‌ی مقدارهای تازه (MANAGE_TASKS، TASK_ASSIGNED، ...)
 *       با «Data truncated» رد می‌شود و Hibernate ddl-auto=update این ستون‌ها را عوض نمی‌کند.</li>
 *   <li>{@code piece_name}/{@code piece_number} در اعلان‌ها دیگر اجباری نیستند (اعلان وظیفه قطعه ندارد).</li>
 *   <li>دسترسی «دریافت وظیفه» فقط یک بار برای اپراتورهای موجود فعال می‌شود تا بعد از استقرار کسی
 *       از دریافت وظیفه جا نماند. ادمین بعداً می‌تواند آن را از هر کاربر بگیرد و دوباره برنمی‌گردد
 *       (ثبت «انجام شد» در جدول {@code app_markers}).</li>
 * </ul>
 * همه‌ی مراحل idempotent هستند و اگر نیازی نباشد کاری نمی‌کنند. روی دیتابیس غیر MySQL (تست) بی‌اثر است.
 */
@Component
@Order(2)
public class TaskSchemaRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskSchemaRepair.class);
    private static final String GRANT_MARKER = "tasks_receive_grant_v1";

    private final JdbcTemplate jdbcTemplate;

    public TaskSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("users")) {
            return;
        }
        convertIfEnum("user_permissions", "permission", "VARCHAR(64) NOT NULL");
        convertIfEnum("system_notifications", "type", "VARCHAR(64) NOT NULL");
        allowNull("system_notifications", "piece_name", "VARCHAR(255) NULL");
        allowNull("system_notifications", "piece_number", "INT NULL");
        grantReceiveTasksOnce();
    }

    private void grantReceiveTasksOnce() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS app_markers (
                        name VARCHAR(64) NOT NULL PRIMARY KEY,
                        applied_at DATETIME(6) NOT NULL
                    )
                    """);
            Integer done = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app_markers WHERE name = ?", Integer.class, GRANT_MARKER);
            if (done != null && done > 0) {
                return;
            }
            int granted = jdbcTemplate.update("""
                    INSERT INTO user_permissions (user_id, permission)
                    SELECT u.id, 'RECEIVE_TASKS' FROM users u
                    WHERE u.role = 'OPERATOR'
                      AND NOT EXISTS (
                        SELECT 1 FROM user_permissions p
                        WHERE p.user_id = u.id AND p.permission = 'RECEIVE_TASKS'
                      )
                    """);
            jdbcTemplate.update("INSERT INTO app_markers (name, applied_at) VALUES (?, NOW(6))", GRANT_MARKER);
            if (granted > 0) {
                log.info("دسترسی «دریافت وظیفه» برای {} اپراتور موجود فعال شد", granted);
            }
        } catch (RuntimeException e) {
            log.error("فعال‌سازی یک‌باره‌ی دسترسی «دریافت وظیفه» انجام نشد؛ آن را از بخش کاربران دستی بدهید", e);
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """,
                Integer.class, table);
        return count != null && count > 0;
    }

    private void convertIfEnum(String table, String column, String varcharDdl) {
        String dataType = columnInfo(table, column, "DATA_TYPE");
        if (!"enum".equalsIgnoreCase(dataType)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + varcharDdl);
        log.warn("ستون {}.{} از ENUM به {} تبدیل شد", table, column, varcharDdl);
    }

    private void allowNull(String table, String column, String ddl) {
        if (!"NO".equalsIgnoreCase(columnInfo(table, column, "IS_NULLABLE"))) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + ddl);
        log.warn("ستون {}.{} برای NULL مجاز شد", table, column);
    }

    private String columnInfo(String table, String column, String infoColumn) {
        return jdbcTemplate.query(
                "SELECT " + infoColumn + " FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                table, column);
    }
}
