package com.factory.productmanager.config;

import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/**
 * اگر جدول کاربران خالی باشد (مثلاً اولین اجرای پروژه)، یک کاربر مدیر
 * پیش‌فرض می‌سازد تا بشود اولین ورود را انجام داد.
 * دسترسی کاربران موجود فقط با تصمیم مدیر تغییر می‌کند؛ مجموعهٔ خالی نیز انتخاب معتبر است.
 */
@Component
@org.springframework.core.annotation.Order(10)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEFAULT_USERNAME = "admin";
    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapPassword = "";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            if (bootstrapPassword.isBlank()) {
                throw new IllegalStateException("Set APP_BOOTSTRAP_ADMIN_PASSWORD before initializing an empty database");
            }
            User admin = new User();
            admin.setUsername(DEFAULT_USERNAME);
            admin.setPassword(passwordEncoder.encode(bootstrapPassword));
            admin.setFullName("مدیر سیستم");
            admin.setRole(Role.MANAGER);
            userRepository.save(admin);

            log.warn("Initial manager created. Change the bootstrap password after first login.");
        }

    }
}
