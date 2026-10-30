package com.factory.productmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * یک منبع واحد برای «اکنون». بقیه‌ی برنامه از LocalDateTime.now() (منطقه‌ی زمانی پیش‌فرض JVM)
 * استفاده می‌کند؛ این Clock دقیقاً همان منطقه را به کار می‌برد تا زمان‌های وظایف با بقیه‌ی
 * داده‌ها هم‌خوان بماند، و در تست‌ها قابل جایگزینی باشد.
 */
@Configuration
@EnableScheduling
public class TimeAndSchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
