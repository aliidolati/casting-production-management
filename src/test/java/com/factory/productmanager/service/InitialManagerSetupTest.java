package com.factory.productmanager.service;

import com.factory.productmanager.config.InitialManagerSetup;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialManagerSetupTest {
    @Test void upgradeRequiresExplicitSelectionAndOnlyPromotesChosenEnabledLegacyAdmin() {
        var repo = mock(UserRepository.class); var audit = mock(UserAdministrationPolicy.class);
        var first = new User(); first.setUsername("chosen"); first.setRole(Role.ADMIN);
        var second = new User(); second.setUsername("other"); second.setRole(Role.ADMIN);
        when(repo.lockForAdministration()).thenReturn(List.of(first, second));
        var setup = new InitialManagerSetup(repo, audit);
        assertThrows(IllegalStateException.class, () -> setup.run(null));
        assertEquals(Role.ADMIN, first.getRole()); assertEquals(Role.ADMIN, second.getRole());
        ReflectionTestUtils.setField(setup, "initialManagerUsername", "chosen");
        first.setDisabled(true);
        assertThrows(IllegalStateException.class, () -> setup.run(null));
        first.setDisabled(false);
        when(audit.snapshot(first)).thenReturn("before", "after");
        setup.run(null);
        assertEquals(Role.MANAGER, first.getRole()); assertEquals(Role.ADMIN, second.getRole());
        verify(audit).record(null, first, "INITIAL_MANAGER_SELECTED", "before", "after");
        setup.run(null);
        verify(repo, times(1)).saveAndFlush(first);
    }
    @Test void existingManagerMeansAnOldSelectionNeverRegrantsAnotherUser() {
        var repo = mock(UserRepository.class); var audit = mock(UserAdministrationPolicy.class);
        var manager = new User(); manager.setRole(Role.MANAGER);
        when(repo.lockForAdministration()).thenReturn(List.of(manager));
        var setup = new InitialManagerSetup(repo, audit);
        ReflectionTestUtils.setField(setup, "initialManagerUsername", "previous-manager");
        setup.run(null); verify(repo, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
}
