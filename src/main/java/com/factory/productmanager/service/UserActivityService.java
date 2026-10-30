package com.factory.productmanager.service;

import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «آخرین فعالیت» کاربر را ثبت می‌کند. برای اینکه روی هر درخواست یک UPDATE نزنیم،
 * برای هر کاربر حداکثر هر ۵ دقیقه یک بار نوشته می‌شود.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> lastWrite = new ConcurrentHashMap<>();

    @Transactional
    public void touch(String username) {
        if (username == null) {
            return;
        }
        Instant now = clock.instant();
        Instant previous = lastWrite.get(username);
        if (previous != null && Duration.between(previous, now).compareTo(MIN_INTERVAL) < 0) {
            return;
        }
        try {
            userRepository.updateLastActivity(username, LocalDateTime.now(clock));
            lastWrite.put(username, now);
        } catch (RuntimeException ex) {
            // ثبت آخرین فعالیت هرگز نباید احراز هویت را خراب کند
            log.debug("ثبت آخرین فعالیت برای {} ناموفق بود", username, ex);
        }
    }
}
