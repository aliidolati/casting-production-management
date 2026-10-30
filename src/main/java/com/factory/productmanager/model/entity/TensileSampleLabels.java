package com.factory.productmanager.model.entity;

import java.util.List;
import java.util.Set;

/** شماره‌های مجاز تست‌پلیت. فرم ثبت و فیلتر گزارش هر دو همین فهرست را می‌خوانند. */
public final class TensileSampleLabels {

    public static final List<String> ALLOWED = List.of(
            "2C4-1", "2C4-2", "C4-1", "C4-2",
            "2C3-1", "2C3-2", "C3-2", "C3-1",
            "2C2-1", "2C2-2", "C2-1", "C2-2",
            "2C1-1", "2C1-2", "C1-1", "C1-2",
            "سایر"
    );

    private static final Set<String> ALLOWED_SET = Set.copyOf(ALLOWED);

    private TensileSampleLabels() {
    }

    public static boolean isAllowed(String label) {
        return label != null && ALLOWED_SET.contains(label);
    }
}
