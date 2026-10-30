package com.factory.productmanager.service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * زمان یک عملیات. اولین مقدار (base) برای updatedAt و فیلدهای وظیفه است و هر رویداد تاریخچه
 * یک «تیک» ۱ میکروثانیه‌ای بعد از قبلی می‌گیرد، تا چند رویدادِ یک عملیات (مثلاً «ساخته شد» و «محول شد»)
 * همیشه ترتیب قطعی و درست در خط زمانی داشته باشند (ستون‌های زمان دیتابیس دقت میکروثانیه دارند).
 */
final class OperationClock {

    private final LocalDateTime base;
    private int ticks = 0;

    OperationClock(Clock clock) {
        this.base = LocalDateTime.now(clock);
    }

    LocalDateTime base() {
        return base;
    }

    LocalDateTime tick() {
        return base.plusNanos(1_000L * (ticks++));
    }
}
