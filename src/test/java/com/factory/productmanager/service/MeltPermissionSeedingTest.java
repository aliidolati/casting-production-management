package com.factory.productmanager.service;

import com.factory.productmanager.config.DataSeeder;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeltPermissionSeedingTest {
    @Test void startupDoesNotGrantApprovalToLegacyOperatorsAndRetainsExplicitGrant() {
        var repo=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        var legacy=new User();legacy.setUsername("synthetic-legacy");legacy.setRole(Role.OPERATOR);legacy.setPermissions(new HashSet<>());
        var qc=new User();qc.setUsername("synthetic-qc");qc.setRole(Role.OPERATOR);qc.setPermissions(new HashSet<>(Set.of(Permission.APPROVE_MELT_PASSPORT)));
        when(repo.count()).thenReturn(2L);when(repo.findAll()).thenReturn(List.of(legacy,qc));
        new DataSeeder(repo,encoder).run();
        assertFalse(legacy.getPermissions().contains(Permission.APPROVE_MELT_PASSPORT));
        assertTrue(qc.getPermissions().contains(Permission.APPROVE_MELT_PASSPORT));
        assertFalse(legacy.getPermissions().contains(Permission.MANAGE_PIECE_ASSIGNMENT));
        assertFalse(PermissionService.DEFAULT_OPERATOR_PERMISSIONS.contains(Permission.APPROVE_MELT_PASSPORT));
    }
}
