package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThreeRolePermissionTest {
    @Test void onlyManagerHasImplicitPermissionsAndAdminGrantsCanBeRevoked() {
        var repo = mock(UserRepository.class);
        var service = new PermissionService(repo);
        var user = new User(); user.setRole(Role.ADMIN); user.setPermissions(new HashSet<>());
        when(repo.findByUsername("synthetic")).thenReturn(Optional.of(user));
        assertFalse(service.hasPermission("synthetic", Permission.MANAGE_PROJECTS));
        user.getPermissions().add(Permission.MANAGE_PROJECTS);
        assertTrue(service.hasPermission("synthetic", Permission.MANAGE_PROJECTS));
        assertFalse(service.hasPermission("synthetic", Permission.VIEW_DASHBOARD));
        user.getPermissions().clear();
        assertFalse(service.hasPermission("synthetic", Permission.MANAGE_PROJECTS));
        user.setRole(Role.MANAGER);
        for (Permission p : Permission.values()) {
            assertEquals(p != Permission.SKIP_PIECE_STAGE, service.hasPermission("synthetic", p));
        }
        assertFalse(service.resolvePermissionCodes(user).contains("SKIP_PIECE_STAGE"));
        assertEquals(Set.of(), service.sanitizePermissionsForRole(Role.ADMIN, Set.of(Permission.SKIP_PIECE_STAGE)));
        user.setDisabled(true);
        assertFalse(service.hasPermission("synthetic", Permission.MANAGE_PROJECTS));
        assertFalse(service.isManager("synthetic"));
        assertEquals(Set.of(), service.sanitizePermissionsForRole(Role.ADMIN, Set.of()));
        assertEquals(Set.of(Permission.RECEIVE_TASKS), service.sanitizePermissionsForRole(Role.ADMIN, Set.of(Permission.RECEIVE_TASKS)));
    }
}
