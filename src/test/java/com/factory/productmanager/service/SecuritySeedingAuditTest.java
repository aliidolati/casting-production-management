package com.factory.productmanager.service;

import com.factory.productmanager.config.DataSeeder;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression: restarting must preserve the administrator's explicit permission decisions. */
class SecuritySeedingAuditTest {
    @Test void emptyDatabaseRequiresExplicitBootstrapPasswordAndDoesNotCreateUser() {
        var repository=mock(UserRepository.class);
        when(repository.count()).thenReturn(0L);
        var seeder=new DataSeeder(repository,mock(PasswordEncoder.class));
        assertThrows(IllegalStateException.class, () -> seeder.run());
        verify(repository,never()).save(any());
    }
    @Test void emptyDatabaseUsesExplicitBootstrapPassword() {
        var repository=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        when(repository.count()).thenReturn(0L);
        when(encoder.encode("synthetic-bootstrap-only")).thenReturn("synthetic-hash");
        var seeder=new DataSeeder(repository,encoder);
        org.springframework.test.util.ReflectionTestUtils.setField(seeder,"bootstrapPassword","synthetic-bootstrap-only");
        seeder.run();
        var user=org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(user.capture());
        assertEquals(Role.MANAGER,user.getValue().getRole());
        assertEquals("synthetic-hash",user.getValue().getPassword());
        assertTrue(user.getValue().getPermissions().isEmpty());
    }
    private User operator(Set<Permission> permissions) {
        var u=new User();u.setUsername("synthetic-audit-operator");u.setRole(Role.OPERATOR);
        u.setPermissions(new HashSet<>(permissions));return u;
    }
    private DataSeeder seeder(User user) {
        var repository=mock(UserRepository.class);
        when(repository.count()).thenReturn(1L);
        when(repository.findAll()).thenReturn(List.of(user));
        return new DataSeeder(repository,mock(PasswordEncoder.class));
    }
    @Test void startupPreservesIntentionallyEmptyOperatorPermissions() {
        var user=operator(Set.of());seeder(user).run();
        assertTrue(user.getPermissions().isEmpty());
    }
    @Test void repeatedStartupPreservesRevokedTensileEntryWhenOtherPermissionRemains() {
        var user=operator(Set.of(Permission.RECEIVE_TASKS));var seeder=seeder(user);
        seeder.run();user.getPermissions().remove(Permission.MANAGE_PIECE_ASSIGNMENT);
        seeder.run();
        assertEquals(Set.of(Permission.RECEIVE_TASKS),user.getPermissions());
    }

    @Test void startupPreservesExplicitTensileAndApprovalGrants() {
        var granted=Set.of(Permission.MANAGE_PIECE_ASSIGNMENT,Permission.APPROVE_MELT_PASSPORT);
        var user=operator(granted);seeder(user).run();
        assertEquals(granted,user.getPermissions());
    }
}
