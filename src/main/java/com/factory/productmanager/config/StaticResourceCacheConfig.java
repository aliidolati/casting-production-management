package com.factory.productmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * کش مرورگر برای فایل‌های ثابت (JS / CSS / فونت).
 *
 * <p>index.html همه‌ی اسکریپت‌ها و استایل‌ها را با پارامتر نسخه (مثلاً {@code app.js?v=74}) صدا می‌زند؛
 * پس با عوض‌شدن شماره‌ی نسخه، مرورگر خودکار فایل جدید را می‌گیرد و می‌توان این فایل‌ها را مدتی کش کرد.
 * روی سیستم‌های ضعیف این کار از دانلود و parse دوباره‌ی چندین‌صد کیلوبایت در هر بار لود صفحه جلوگیری می‌کند.
 * خود index.html کش نمی‌شود (با ETag/Last-Modified هر بار اعتبارسنجی می‌شود) تا نسخه‌های جدید دیده شوند.
 */
@Configuration
public class StaticResourceCacheConfig implements WebMvcConfigurer {

    private static final Duration ASSET_MAX_AGE = Duration.ofDays(7);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(ASSET_MAX_AGE).cachePublic());
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(ASSET_MAX_AGE).cachePublic());
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(CacheControl.maxAge(ASSET_MAX_AGE).cachePublic());
    }
}
