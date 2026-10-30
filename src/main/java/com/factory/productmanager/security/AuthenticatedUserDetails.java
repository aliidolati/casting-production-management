package com.factory.productmanager.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;
import java.util.UUID;

/** Identity and credential state captured by the password authentication provider. */
public final class AuthenticatedUserDetails extends User {
    private final UUID userId;
    private final String credentialStamp;

    public AuthenticatedUserDetails(UUID userId, String username, String password, boolean enabled,
                                    Collection<? extends GrantedAuthority> authorities, String credentialStamp) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.credentialStamp = credentialStamp;
    }

    public UUID getUserId() { return userId; }
    public String getCredentialStamp() { return credentialStamp; }
}
