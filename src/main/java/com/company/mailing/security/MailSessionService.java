package com.company.mailing.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MailSessionService {

    private final AppAuthProperties appAuthProperties;
    private final Map<UUID, MailUserSession> sessions = new ConcurrentHashMap<>();

    public MailSessionService(AppAuthProperties appAuthProperties) {
        this.appAuthProperties = appAuthProperties;
    }

    public MailUserSession createSession(MailCredentials credentials, UUID userId, List<String> roles) {
        purgeExpired();
        UUID sid = UUID.randomUUID();
        MailUserSession session = new MailUserSession(
                sid,
                userId,
                credentials.username(),
                credentials.password(),
                roles,
                nextExpiry()
        );
        sessions.put(sid, session);
        return session;
    }

    public MailUserSession requireActive(UUID sid, String username) {
        if (sid == null) {
            throw new IllegalArgumentException("Session id is missing.");
        }

        MailUserSession current = sessions.get(sid);
        if (current == null) {
            throw new IllegalArgumentException("Session not found.");
        }
        if (username != null && !username.isBlank() && !current.username().equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Session user mismatch.");
        }

        Instant now = Instant.now();
        if (current.isExpired(now)) {
            sessions.remove(sid);
            throw new IllegalArgumentException("Session expired.");
        }

        MailUserSession refreshed = new MailUserSession(
                current.sid(),
                current.userId(),
                current.username(),
                current.password(),
                current.roles(),
                nextExpiry()
        );
        sessions.put(sid, refreshed);
        return refreshed;
    }

    public MailCredentials resolveCredentials(JwtPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authenticated principal is required.");
        }
        MailUserSession session = requireActive(sessionKey(principal), principal.username());
        return new MailCredentials(session.username(), session.password());
    }

    public MailUserSession bindCredentials(JwtPrincipal principal, MailCredentials credentials) {
        if (principal == null) {
            throw new IllegalArgumentException("Authenticated principal is required.");
        }
        UUID sid = sessionKey(principal);
        List<String> roles = principal.roles() == null || principal.roles().isEmpty()
                ? appAuthProperties.resolveRoles()
                : principal.roles();

        MailUserSession session = new MailUserSession(
                sid,
                principal.userId(),
                credentials.username(),
                credentials.password(),
                roles,
                nextExpiry()
        );
        sessions.put(sid, session);
        return session;
    }

    public void clearCredentials(JwtPrincipal principal) {
        if (principal == null) {
            return;
        }
        sessions.remove(sessionKey(principal));
    }

    public void invalidate(UUID sid) {
        if (sid != null) {
            sessions.remove(sid);
        }
    }

    private Instant nextExpiry() {
        return Instant.now().plus(resolveSessionTtlMinutes(), ChronoUnit.MINUTES);
    }

    private long resolveSessionTtlMinutes() {
        return Math.max(1, appAuthProperties.getSessionTtlMinutes());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired(now));
    }

    private UUID sessionKey(JwtPrincipal principal) {
        if (principal.sid() != null) {
            return principal.sid();
        }
        String username = principal.username() == null ? "unknown" : principal.username().trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(("mail-session:" + username).getBytes(StandardCharsets.UTF_8));
    }
}
