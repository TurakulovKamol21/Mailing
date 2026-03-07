package com.company.mailing.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MailUserSession(
        UUID sid,
        UUID userId,
        String username,
        String password,
        List<String> roles,
        Instant expiresAt
) {
    public MailUserSession {
        roles = roles == null ? List.of("USER") : List.copyOf(roles);
        password = password == null ? "" : password;
    }

    public boolean isExpired(Instant now) {
        return now == null || expiresAt == null || !expiresAt.isAfter(now);
    }
}
