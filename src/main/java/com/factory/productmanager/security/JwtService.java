package com.factory.productmanager.security;

import com.factory.productmanager.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Signed access tokens bound to an immutable user ID and the stored credential state.
 */
@Component
public class JwtService {
    private static final String FORMAT = "access-v2";
    private static final String STAMP_CONTEXT = "productmanager/access-credentials/v1\0";
    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        if (secret == null || secret.isBlank() || secret.contains("${")
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must contain at least 32 random bytes");
        }
        if (expirationMs <= 0) throw new IllegalArgumentException("JWT expiration must be positive");
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AuthenticatedUserDetails authenticated, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(authenticated.getUserId().toString())
                .claim("format", FORMAT)
                .claim("credentials", authenticated.getCredentialStamp())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public record TokenIdentity(UUID userId, String credentialStamp) {}

    /** Verify once; legacy username-only tokens are intentionally rejected. */
    public TokenIdentity parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!FORMAT.equals(claims.get("format", String.class)) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || !claims.getExpiration().after(new Date())) {
            throw new IllegalArgumentException("Invalid access token");
        }
        String stamp = claims.get("credentials", String.class);
        if (stamp == null || !stamp.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid access token");
        return new TokenIdentity(UUID.fromString(claims.getSubject()), stamp);
    }

    public boolean isTokenValid(TokenIdentity token, AuthenticatedUserDetails current) {
        return current.isEnabled() && token.userId().equals(current.getUserId())
                && MessageDigest.isEqual(token.credentialStamp().getBytes(StandardCharsets.US_ASCII),
                current.getCredentialStamp().getBytes(StandardCharsets.US_ASCII));
    }

    /** No password/hash is put in a token. A new BCrypt hash invalidates all prior stamps. */
    public String credentialStamp(User user) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getEncoded(), "HmacSHA256"));
            byte[] material = (STAMP_CONTEXT + user.getId() + "\0" + user.getPassword()).getBytes(StandardCharsets.UTF_8);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(material));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Credential binding is unavailable", e);
        }
    }
}
