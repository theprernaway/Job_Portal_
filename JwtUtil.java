package com.jobportal.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JwtUtil handles everything JWT-related:
 *   - generateToken()  → creates a signed JWT for a user
 *   - getUserEmail()   → reads the email claim from a token
 *   - isTokenValid()   → checks signature + expiry
 */
@Component
@Slf4j
public class JwtUtil {

    // Read from application.properties
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration.ms}")
    private long expirationMs;

    // Build a signing key from the secret string
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ── Generate token ────────────────────────────────────────────────────────
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)               // who this token is for
                .claim("role", role)             // embed the role in the token
                .setIssuedAt(new Date())         // when it was created
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Extract email from token ──────────────────────────────────────────────
    public String getUserEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // ── Extract role from token ───────────────────────────────────────────────
    public String getUserRole(String token) {
        return (String) parseClaims(token).get("role");
    }

    // ── Validate token ────────────────────────────────────────────────────────
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token); // throws if invalid
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT invalid: {}", e.getMessage());
        }
        return false;
    }

    // ── Parse the claims payload ──────────────────────────────────────────────
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
