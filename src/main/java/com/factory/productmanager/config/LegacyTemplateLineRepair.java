package com.factory.productmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * جدول radiography_report_template_lines در دیتابیس‌های قدیمی هنوز ستون line_no
 * (NOT NULL و بدون مقدار پیش‌فرض) دارد. Entity فعلی فقط course_code را می‌شناسد،
 * پس هر INSERT با «Field 'line_no' doesn't have a default value» رد می‌شود.
 *
 * Flyway در این پروژه عملاً اجرا نمی‌شود (Spring Boot 4 برای اجرای خودکار Flyway
 * به spring-boot-starter-flyway نیاز دارد)، پس همان کار V21/V23 را اینجا و در هر استارت
 * انجام می‌دهیم. اگر ستون قدیمی وجود نداشته باشد هیچ کاری نمی‌کند.
 */
@Component
@Order(1)
public class LegacyTemplateLineRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyTemplateLineRepair.class);
    private static final String TABLE = "radiography_report_template_lines";

    private final JdbcTemplate jdbcTemplate;

    public LegacyTemplateLineRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            dropLegacyLineNo();
            widenImageCount();
        } catch (DataAccessException e) {
            // استارت اپ نباید به‌خاطر ترمیم ناموفق بخوابد؛ خطای واقعی هنگام ذخیره‌ی قالب دیده می‌شود
            log.error("ترمیم جدول {} ناموفق بود: {}", TABLE, e.getMessage(), e);
        }
    }

    private void dropLegacyLineNo() {
        if (!columnExists("line_no")) {
            return;
        }

        if (!columnExists("course_code")) {
            jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` CHANGE COLUMN `line_no` `course_code` VARCHAR(50) NOT NULL");
            log.warn("ستون {}.line_no به course_code تغییر نام داد", TABLE);
        } else {
            int copied = jdbcTemplate.update(
                    "UPDATE `" + TABLE + "` SET course_code = CAST(line_no AS CHAR) "
                            + "WHERE course_code IS NULL OR course_code = ''");
            if (copied > 0) {
                log.warn("مقدار line_no برای {} سطر به course_code کپی شد", copied);
            }
        }

        if (columnExists("line_no")) {
            // کلید خارجی template_id به یک ایندکس نیاز دارد؛ تا حذف ایندکس یکتای قدیمی یکی موقت می‌سازیم
            boolean oldUniqueOnLineNo = indexHasColumn("uk_rg_template_line_no", "line_no");
            String helper = "idx_rg_template_line_template_tmp";
            if (oldUniqueOnLineNo) {
                jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` ADD INDEX `" + helper + "` (template_id)");
                jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` DROP INDEX `uk_rg_template_line_no`");
            }
            jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` DROP COLUMN `line_no`");
            log.warn("ستون قدیمی {}.line_no حذف شد", TABLE);

            if (!indexHasColumn("uk_rg_template_line_no", "course_code")) {
                try {
                    jdbcTemplate.execute(
                            "ALTER TABLE `" + TABLE + "` ADD UNIQUE KEY `uk_rg_template_line_no` (template_id, course_code)");
                } catch (DataAccessException e) {
                    log.error("ساخت یکتایی (template_id, course_code) ممکن نشد؛ احتمالاً LINE NO تکراری وجود دارد: {}",
                            e.getMessage());
                }
            }
            if (oldUniqueOnLineNo) {
                jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` DROP INDEX `" + helper + "`");
            }
        }
    }

    /** Entity برای image_count رشته می‌خواهد؛ اگر در دیتابیس قدیمی هنوز عددی است اصلاح شود */
    private void widenImageCount() {
        String type = jdbcTemplate.query(
                """
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'image_count'
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                TABLE
        );
        if (type != null && !"varchar".equalsIgnoreCase(type)) {
            jdbcTemplate.execute("ALTER TABLE `" + TABLE + "` MODIFY COLUMN `image_count` VARCHAR(50) NULL");
            log.warn("ستون {}.image_count از {} به VARCHAR(50) تبدیل شد", TABLE, type);
        }
    }

    private boolean columnExists(String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """,
                Integer.class,
                TABLE,
                column
        );
        return count != null && count > 0;
    }

    private boolean indexHasColumn(String index, String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? AND COLUMN_NAME = ?
                """,
                Integer.class,
                TABLE,
                index,
                column
        );
        return count != null && count > 0;
    }
}
