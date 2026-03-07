package com.company.mailing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(String username, List<String> roles, UUID userId, UUID sid) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenMinutes(), ChronoUnit.MINUTES);

        var builder = Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("token_type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (userId != null) {
            builder.claim("uid", userId.toString());
        }
        if (sid != null) {
            builder.claim("sid", sid.toString());
        }

        return builder.signWith(signingKey(), Jwts.SIG.HS256).compact();
    }

    public String generateRefreshToken(String username, UUID userId, UUID sid) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getRefreshTokenDays(), ChronoUnit.DAYS);

        var builder = Jwts.builder()
                .subject(username)
                .claim("token_type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (userId != null) {
            builder.claim("uid", userId.toString());
        }
        if (sid != null) {
            builder.claim("sid", sid.toString());
        }

        return builder.signWith(signingKey(), Jwts.SIG.HS256).compact();
    }

    public JwtPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get("token_type", String.class);
        if (tokenType != null && !tokenType.isBlank() && !"access".equals(tokenType)) {
            throw new IllegalArgumentException("Token is not an access token.");
        }

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Token subject is missing.");
        }

        List<String> roles = extractRoles(claims.get("roles"));
        if (roles.isEmpty()) {
            roles = extractRolesFromUser(claims.get("user"));
        }
        UUID userId = extractUserId(claims);
        UUID sid = parseUuid(claims.get("sid"));

        return new JwtPrincipal(username, roles, userId, sid);
    }

    public JwtPrincipal parseRefreshToken(String refreshToken) {
        Claims claims = parseClaims(refreshToken);
        String tokenType = claims.get("token_type", String.class);
        if (!"refresh".equals(tokenType)) {
            throw new IllegalArgumentException("Token is not a refresh token.");
        }

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Token subject is missing.");
        }

        UUID userId = extractUserId(claims);
        UUID sid = parseUuid(claims.get("sid"));
        return new JwtPrincipal(username, List.of(), userId, sid);
    }

    public long getAccessTokenExpiresInSeconds() {
        return jwtProperties.getAccessTokenMinutes() * 60;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        String secret = jwtProperties.getSecret() == null ? "" : jwtProperties.getSecret().trim();
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private List<String> extractRoles(Object rolesObj) {
        if (rolesObj instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        if (rolesObj instanceof String roleString) {
            return Arrays.stream(roleString.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        return List.of();
    }

    private UUID extractUserId(Claims claims) {
        UUID direct = parseUuid(claims.get("uid"));
        if (direct != null) {
            return direct;
        }

        Object user = claims.get("user");
        if (user instanceof Map<?, ?> userMap) {
            Object id = userMap.get("id");
            return parseUuid(id);
        }
        if (user instanceof String userText) {
            Map<String, String> parsed = parseFlatObjectString(userText);
            return parseUuid(parsed.get("id"));
        }
        return null;
    }

    private List<String> extractRolesFromUser(Object user) {
        if (!(user instanceof Map<?, ?> userMap)) {
            return List.of();
        }

        Object userRoles = userMap.get("roles");
        if (userRoles instanceof List<?> list) {
            return list.stream()
                    .map(this::mapRoleItem)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        if (userRoles instanceof String roleString) {
            return extractRoles(roleString);
        }
        return List.of();
    }

    private String mapRoleItem(Object item) {
        if (item == null) {
            return "";
        }
        if (item instanceof Map<?, ?> roleMap) {
            Object name = roleMap.get("name");
            return name == null ? "" : String.valueOf(name).trim();
        }
        return String.valueOf(item).trim();
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return null;
            }
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, String> parseFlatObjectString(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("{") && value.endsWith("}")) {
            value = value.substring(1, value.length() - 1);
        }

        Map<String, String> map = new LinkedHashMap<>();
        if (value.isBlank()) {
            return map;
        }

        String[] pairs = value.split(",\\s*");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return map;
    }
}
