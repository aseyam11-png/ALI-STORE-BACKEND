package com.store.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * HMAC-SHA signed JWTs. Session version claim "av" supports immediate invalidation
 * of existing tokens after a password change/reset. Tokens created before that
 * claim existed are treated as version zero for backward compatibility.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32; // 256 bits - the floor for HS256.

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${jwt.issuer:ali-store-backend}")
    private String issuer;

    @PostConstruct
    void validateSecretStrength() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "JWT_SECRET must be valid Base64 - refusing to start with an unparsable signing key.", ex);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET decodes to " + keyBytes.length + " bytes, below the required minimum of "
                            + MIN_SECRET_BYTES + " bytes (256 bits) for HMAC-SHA signing. Refusing to start with a weak signing key.");
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        var builder = Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration));

        if (userDetails instanceof AccountVersionAware versionAware) {
            builder.claim("av", versionAware.getAuthVersion());
        }

        return builder.signWith(getSignInKey()).compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }

        // UserDetails are loaded from the database for each authenticated request.
        // A disabled account must stop authenticating immediately, regardless of the
        // token's remaining lifetime. Account lockout is a login control, not a way
        // to revoke already-established sessions.
        if (!userDetails.isEnabled()) {
            return false;
        }

        if (userDetails instanceof AccountVersionAware versionAware) {
            Number tokenVersion = extractClaim(token, claims -> (Number) claims.get("av"));
            long version = tokenVersion == null ? 0L : tokenVersion.longValue();
            return version == versionAware.getAuthVersion();
        }

        return true;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
