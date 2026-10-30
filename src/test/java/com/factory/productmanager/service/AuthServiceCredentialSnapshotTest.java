package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.LoginRequest;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.UserRepository;
import com.factory.productmanager.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceCredentialSnapshotTest {
    @Test void passwordResetBetweenAuthenticationAndResponseCannotIssueTokenForNewCredentials() {
        var jwt=new JwtService("test-only-login-race-642418ba05464fca9dcf51efb6803f9b",60_000);
        var user=new User();user.setId(UUID.randomUUID());user.setUsername("synthetic-login-race");
        user.setRole(Role.OPERATOR);user.setPassword("hash-actually-verified");
        var verified=new AuthenticatedUserDetails(user.getId(),user.getUsername(),user.getPassword(),true,List.of(),jwt.credentialStamp(user));
        verified.eraseCredentials();
        var manager=mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(verified,null,List.of()));
        // Models a committed reset after the authentication provider checked the old password.
        user.setPassword("hash-after-concurrent-reset");
        var repository=mock(UserRepository.class);when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        var permissions=mock(PermissionService.class);when(permissions.resolvePermissionCodes(user)).thenReturn(List.of());
        var service=new AuthService(manager,repository,jwt,permissions,mock(UserActivityService.class));
        var request=new LoginRequest();request.setUsername(user.getUsername());request.setPassword("synthetic-password");
        var response=service.login(request);
        var current=new AuthenticatedUserDetails(user.getId(),user.getUsername(),user.getPassword(),true,List.of(),jwt.credentialStamp(user));
        assertFalse(jwt.isTokenValid(jwt.parseToken(response.getToken()),current));
    }
}
