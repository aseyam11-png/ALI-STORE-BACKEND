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
 * HMAC-SHA (jjwt picks the strongest HS256/384/512 variant the key length supports -
 * see getSignInKey) signed, symmetric-key JWTs. Rotation strategy: NOT IMPLEMENTED - this
 * is a single static secret with no key ID (kid) claim or multi-key verification, so
 * rotating JWT_SECRET in production invalidates every outstanding token immediately (all
 * users forced to re-login) rather than allowing a grace-period overlap. A real rotation
 * scheme needs either a JWKS endpoint or a small ordered list of currently-valid keys
 * checked in turn, which is a genuine architectural addition, not something bolted on
 * here - documented as technical debt, not fabricated.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32; // 256 bits - the floor for HS256.

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // 24 ساعة بالمللي ثانية
    private long jwtExpiration;

    @Value("${jwt.issuer:ali-store-backend}")
    private String issuer;

    // Fails application startup outright if JWT_SECRET is too weak, instead of accepting it
    // silently and only failing (or worse, succeeding with a trivially forgeable signature)
    // the first time a token is actually signed or verified.
    @PostConstruct
    void validateSecretStrength() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64 - refusing to start with an unparsable signing key.", ex);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET decodes to " + keyBytes.length + " bytes, below the required minimum of "
                            + MIN_SECRET_BYTES + " bytes (256 bits) for HMAC-SHA signing. Refusing to start with a weak signing key.");
        }
    }

    // استخراج اسم المستخدم (Subject) من الـ Token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // استخراج Claim معين
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // إنشاء Token للمستخدم
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    // إنشاء Token مع بيانات إضافية (Extra Claims)
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey()) // Sign with SecretKey directly
                .compact();
    }

    // التحقق من صحة الـ Token
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // استخراج كل الـ Claims باستخدام JJWT 0.12+ (بدون parserBuilder)
    // requireIssuer rejects any signature-valid token whose "iss" claim doesn't match ours -
    // e.g. a token signed by this same secret but minted for a different purpose/service.
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // تحويل الـ Secret Key النصي إلى SecretKey Object
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}