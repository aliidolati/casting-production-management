package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.UpdateUserAccountRequest;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:last_manager_race;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "app.upload.dir=./target/test-uploads/last-manager-race"})
class LastManagerConcurrencyIntegrationTest {
    @Autowired UserRepository repository;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;

    @Test void simultaneousSelfDemotionsCannotRemoveBothManagers() throws Exception {
        var tx = new TransactionTemplate(transactions);
        List<User> managers = tx.execute(status -> {
            repository.findAll().forEach(u -> u.setRole(Role.OPERATOR));
            List<User> result = new ArrayList<>();
            for (int i=0; i<2; i++) {
                var u = new User(); u.setUsername("synthetic-manager-"+i);
                u.setFullName("مدیر ساختگی"); u.setPassword(encoder.encode("Synthetic@123")); u.setRole(Role.MANAGER);
                result.add(repository.saveAndFlush(u));
            }
            return result;
        });
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (User manager : managers) outcomes.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(manager.getUsername(), null, List.of()));
                ready.countDown();
                try {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    users.updateAccount(manager.getId(), new UpdateUserAccountRequest("مدیر سابق", Role.ADMIN, Set.of()));
                    return true;
                } catch (com.factory.productmanager.exception.DuplicateResourceException expected) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int successes = 0;
            for (var outcome : outcomes) if (outcome.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1L, tx.execute(status -> repository.findAll().stream()
                    .filter(u -> u.getRole() == Role.MANAGER && !u.isDisabled()).count()).longValue());
        } finally { start.countDown(); pool.shutdownNow(); }
    }
}
