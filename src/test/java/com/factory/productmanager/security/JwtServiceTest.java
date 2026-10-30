package com.factory.productmanager.security;

import com.factory.productmanager.model.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String TEST_KEY = "test-only-key-a-945fa91b51ca43c2ad34abc1542a120f";
    private final JwtService jwt = new JwtService(TEST_KEY,60_000);

    private User user() {
        var u=new User();u.setId(UUID.randomUUID());u.setUsername("synthetic-token-user");
        u.setPassword("synthetic-encoded-password-state");return u;
    }
    private AuthenticatedUserDetails principal(User u) {
        return new AuthenticatedUserDetails(u.getId(),u.getUsername(),u.getPassword(),!u.isDisabled(),List.of(),jwt.credentialStamp(u));
    }
    @Test void missingWeakOrUnresolvedKeyFailsWithoutDisclosingIt() {
        for(String key:Arrays.asList(null,""," ","short-test-secret","${JWT_SECRET}")) {
            var e=assertThrows(IllegalArgumentException.class,()->new JwtService(key,60_000));
            assertEquals("app.jwt.secret must contain at least 32 random bytes",e.getMessage());
        }
        assertThrows(IllegalArgumentException.class,()->new JwtService(TEST_KEY,0));
    }
    @Test void runtimeConfigurationRequiresExternalCredentialsAndSigningKey() throws Exception {
        var p=new Properties();try(var in=Files.newBufferedReader(Path.of("src/main/resources/application.properties"))){p.load(in);}
        assertEquals("${JWT_SECRET}",p.getProperty("app.jwt.secret"));
        assertEquals("${SPRING_DATASOURCE_URL}",p.getProperty("spring.datasource.url"));
        assertEquals("${SPRING_DATASOURCE_USERNAME}",p.getProperty("spring.datasource.username"));
        assertEquals("${SPRING_DATASOURCE_PASSWORD}",p.getProperty("spring.datasource.password"));
        assertThrows(IllegalArgumentException.class,()->new JwtService(p.getProperty("app.jwt.secret"),60_000));
    }
    @Test void tokenContainsIdentityAndOpaqueCredentialBindingRatherThanPasswordHash() {
        var u=user();var token=jwt.generateToken(principal(u),"OPERATOR");
        var identity=jwt.parseToken(token);
        assertEquals(u.getId(),identity.userId());assertTrue(jwt.isTokenValid(identity,principal(u)));
        var payload=new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]),StandardCharsets.UTF_8);
        assertFalse(payload.contains(u.getPassword()));assertFalse(payload.contains(u.getUsername()));
    }
    @Test void tokenFailsAfterPasswordStateChangesOrUsernameIsReused() {
        var u=user();var old=jwt.parseToken(jwt.generateToken(principal(u),"OPERATOR"));
        u.setPassword("a-new-salted-password-hash");assertFalse(jwt.isTokenValid(old,principal(u)));
        u.setPassword("synthetic-encoded-password-state");u.setId(UUID.randomUUID());
        assertFalse(jwt.isTokenValid(old,principal(u)));
    }
    @Test void disabledAccountAndDifferentSigningKeyRejectTokens() {
        var u=user();var token=jwt.generateToken(principal(u),"OPERATOR");u.setDisabled(true);
        assertFalse(jwt.isTokenValid(jwt.parseToken(token),principal(u)));
        var other=new JwtService("test-only-key-b-7da9c420207b4729af0b9988fa157447",60_000);
        assertThrows(Exception.class,()->other.parseToken(token));
    }
    @Test void legacyOrIncompleteSignedTokensAreRejected() {
        var key=Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8));
        var now=new Date();var expiry=new Date(now.getTime()+60_000);
        String legacy=Jwts.builder().subject("synthetic-user").issuedAt(now).expiration(expiry).signWith(key).compact();
        assertThrows(IllegalArgumentException.class,()->jwt.parseToken(legacy));
        String noExpiry=Jwts.builder().subject(UUID.randomUUID().toString()).claim("format","access-v2")
                .claim("credentials","a".repeat(43)).issuedAt(now).signWith(key).compact();
        assertThrows(IllegalArgumentException.class,()->jwt.parseToken(noExpiry));
    }
    @Test void providerErasingPasswordDoesNotChangeAuthenticatedCredentialSnapshot() {
        var u=user();var authenticated=principal(u);var stamp=authenticated.getCredentialStamp();
        authenticated.eraseCredentials();assertNull(authenticated.getPassword());
        assertEquals(stamp,authenticated.getCredentialStamp());
        assertTrue(jwt.isTokenValid(jwt.parseToken(jwt.generateToken(authenticated,"OPERATOR")),principal(u)));
    }
}
