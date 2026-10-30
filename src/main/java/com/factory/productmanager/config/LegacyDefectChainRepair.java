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
 * عیب‌های رادیوگرافی قبل از «زنجیره‌ی عیب» فقط start_cm/end_cm/location_cm داشتند.
 * این کلاس در هر استارت داده‌ی قدیمی را به ساختار جدید می‌برد تا چیزی از دست نرود:
 * <ul>
 *   <li>هر عیبی که موقعیت دارد ولی هنوز ردیفی در radiography_defect_positions ندارد، یک موقعیت می‌گیرد؛</li>
 *   <li>عیب‌های «نیاز به جوش» با welded = true علامت می‌خورند تا در متن زنجیره «جوش» نشان داده شود.</li>
 * </ul>
 * ستون‌ها و جدول جدید را ddl-auto می‌سازد (Flyway در این پروژه اجرا نمی‌شود).
 * کاملاً idempotent است و خطاها فقط لاگ می‌شوند.
 */
@Component
@Order(2)
public class LegacyDefectChainRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDefectChainRepair.class);

    private final JdbcTemplate jdbcTemplate;

    public LegacyDefectChainRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int welded = jdbcTemplate.update(
                    "UPDATE radiography_defects SET welded = 1 WHERE status = 'NEEDS_WELDING' AND welded = 0");
            if (welded > 0) {
                log.warn("{} عیب «نیاز به جوش» با welded=1 علامت خورد", welded);
            }

            int positions = jdbcTemplate.update(
                    """
                    INSERT INTO radiography_defect_positions (defect_id, position_order, start_cm, end_cm)
                    SELECT d.id, 0,
                           COALESCE(d.start_cm, d.location_cm),
                           COALESCE(d.end_cm, d.start_cm, d.location_cm)
                    FROM radiography_defects d
                    WHERE d.parent_defect_id IS NULL
                      AND COALESCE(d.start_cm, d.location_cm) IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM radiography_defect_positions p WHERE p.defect_id = d.id
                      )
                    """);
            if (positions > 0) {
                log.warn("برای {} عیب قدیمی موقعیت زنجیره از start/end/location ساخته شد", positions);
            }
        } catch (DataAccessException e) {
            log.error("انتقال عیب‌های قدیمی به ساختار زنجیره ناموفق بود: {}", e.getMessage(), e);
        }
    }
}
