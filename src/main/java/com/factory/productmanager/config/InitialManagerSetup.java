package com.factory.productmanager.config;

import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.repository.UserRepository;
import com.factory.productmanager.service.UserAdministrationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit one-time opt-in for an existing installation; never chooses an account by name convention. */
@Component @Order(20) @RequiredArgsConstructor
public class InitialManagerSetup implements ApplicationRunner {
    private final UserRepository users;
    private final UserAdministrationPolicy administration;
    @Value("${app.initial-manager.username:}")
    private String initialManagerUsername = "";

    @Override @Transactional
    public void run(ApplicationArguments args) {
        var locked = users.lockForAdministration();
        if (locked.stream().anyMatch(u -> u.getRole() == Role.MANAGER && !u.isDisabled())) return;
        if (initialManagerUsername.isBlank()) {
            throw new IllegalStateException("Existing installation requires APP_INITIAL_MANAGER_USERNAME selecting an enabled ADMIN before upgrade; no user was promoted");
        }
        var selected = locked.stream().filter(u -> u.getUsername().equals(initialManagerUsername)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected initial manager account does not exist"));
        if (selected.isDisabled() || selected.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Selected initial manager must be an enabled existing ADMIN");
        }
        String before = administration.snapshot(selected);
        selected.setRole(Role.MANAGER);
        users.saveAndFlush(selected);
        administration.record(null, selected, "INITIAL_MANAGER_SELECTED", before, administration.snapshot(selected));
    }
}
