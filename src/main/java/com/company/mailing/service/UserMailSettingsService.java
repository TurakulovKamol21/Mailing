package com.company.mailing.service;

import com.company.mailing.config.MailConnectionSettings;
import com.company.mailing.config.MailProperties;
import com.company.mailing.dto.MailSettingsRequest;
import com.company.mailing.dto.MailSettingsResponse;
import com.company.mailing.entity.UserMailSettings;
import com.company.mailing.exception.MailServiceException;
import com.company.mailing.exception.MailSettingsRequiredException;
import com.company.mailing.repository.UserMailSettingsRepository;
import com.company.mailing.security.JwtPrincipal;
import com.company.mailing.security.MailPasswordCryptoService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserMailSettingsService {

    private final MailProperties defaults;
    private final UserMailSettingsRepository repository;
    private final MailPasswordCryptoService passwordCryptoService;

    public UserMailSettingsService(
            MailProperties defaults,
            UserMailSettingsRepository repository,
            MailPasswordCryptoService passwordCryptoService
    ) {
        this.defaults = defaults;
        this.repository = repository;
        this.passwordCryptoService = passwordCryptoService;
    }

    public MailSettingsResponse getSettings(JwtPrincipal principal) {
        UserMailSettings stored = repository.findByOwnerUserId(requireUserId(principal)).orElse(null);
        if (stored == null) {
            MailConnectionSettings fallback = defaultsFor(principal);
            return toResponse(false, false, fallback);
        }
        return toResponse(true, hasPassword(stored), toSettings(stored));
    }

    public MailConnectionSettings buildForSave(JwtPrincipal principal, MailSettingsRequest request) {
        UserMailSettings existing = repository.findByOwnerUserId(requireUserId(principal)).orElse(null);

        String password = normalizeNullable(request.password());
        if (password == null && existing != null) {
            password = normalizeNullable(passwordCryptoService.decrypt(existing.getMailboxPassword()));
        }
        if (password == null || password.isBlank()) {
            throw new MailServiceException("Password is required.");
        }

        String username = normalizeRequired(request.username(), "Mailbox username is required.");

        String imapHost = normalizeRequired(request.imapHost(), "IMAP host is required.");
        String smtpHost = normalizeRequired(request.smtpHost(), "SMTP host is required.");
        String defaultFolder = normalizeNullable(request.defaultFolder());
        String fromEmail = normalizeNullable(request.fromEmail());

        return new MailConnectionSettings(
                imapHost,
                request.imapPort(),
                request.imapSsl(),
                smtpHost,
                request.smtpPort(),
                request.smtpStarttls(),
                request.smtpSsl(),
                username.toLowerCase(Locale.ROOT),
                password,
                fromEmail,
                defaultFolder == null ? "INBOX" : defaultFolder,
                request.timeoutSeconds()
        );
    }

    @Transactional
    public MailSettingsResponse saveSettings(JwtPrincipal principal, MailConnectionSettings settings) {
        UUID ownerUserId = requireUserId(principal);
        UserMailSettings entity = repository.findByOwnerUserId(ownerUserId).orElseGet(UserMailSettings::new);
        entity.setOwnerUserId(ownerUserId);
        entity.setImapHost(settings.imapHost());
        entity.setImapPort(settings.imapPort());
        entity.setImapSsl(settings.imapSsl());
        entity.setSmtpHost(settings.smtpHost());
        entity.setSmtpPort(settings.smtpPort());
        entity.setSmtpStarttls(settings.smtpStarttls());
        entity.setSmtpSsl(settings.smtpSsl());
        entity.setMailboxUsername(settings.username());
        entity.setMailboxPassword(passwordCryptoService.encrypt(settings.password()));
        entity.setFromEmail(normalizeNullable(settings.fromEmail()));
        entity.setDefaultFolder(settings.resolveDefaultFolder());
        entity.setTimeoutSeconds(settings.timeoutSeconds());
        repository.save(entity);
        return toResponse(true, hasPassword(entity), settings);
    }

    public MailConnectionSettings requireSettings(JwtPrincipal principal) {
        return repository.findByOwnerUserId(requireUserId(principal))
                .map(this::toSettings)
                .orElseThrow(() ->
                        new MailSettingsRequiredException(
                                "Mailbox settings not configured. Open settings and save connection."));
    }

    private MailSettingsResponse toResponse(boolean configured, boolean hasPassword, MailConnectionSettings settings) {
        return new MailSettingsResponse(
                configured,
                hasPassword,
                settings.imapHost(),
                settings.imapPort(),
                settings.imapSsl(),
                settings.smtpHost(),
                settings.smtpPort(),
                settings.smtpStarttls(),
                settings.smtpSsl(),
                settings.username(),
                settings.fromEmail(),
                settings.resolveDefaultFolder(),
                settings.timeoutSeconds()
        );
    }

    private MailConnectionSettings toSettings(UserMailSettings entity) {
        return new MailConnectionSettings(
                entity.getImapHost(),
                entity.getImapPort(),
                entity.isImapSsl(),
                entity.getSmtpHost(),
                entity.getSmtpPort(),
                entity.isSmtpStarttls(),
                entity.isSmtpSsl(),
                entity.getMailboxUsername(),
                passwordCryptoService.decrypt(entity.getMailboxPassword()),
                entity.getFromEmail(),
                entity.getDefaultFolder(),
                entity.getTimeoutSeconds()
        );
    }

    private boolean hasPassword(UserMailSettings entity) {
        return normalizeNullable(entity.getMailboxPassword()) != null;
    }

    private MailConnectionSettings defaultsFor(JwtPrincipal principal) {
        String username = principal != null && principal.username() != null ? principal.username().trim() : "";
        String defaultFolder = normalizeNullable(defaults.getDefaultFolder());
        return new MailConnectionSettings(
                normalizeNullable(defaults.getImapHost()),
                defaults.getImapPort() > 0 ? defaults.getImapPort() : 993,
                defaults.isImapSsl(),
                normalizeNullable(defaults.getSmtpHost()),
                defaults.getSmtpPort() > 0 ? defaults.getSmtpPort() : 587,
                defaults.isSmtpStarttls(),
                defaults.isSmtpSsl(),
                username,
                "",
                username,
                defaultFolder == null ? "INBOX" : defaultFolder,
                defaults.getTimeoutSeconds() > 0 ? defaults.getTimeoutSeconds() : 30
        );
    }

    private UUID requireUserId(JwtPrincipal principal) {
        if (principal == null) {
            throw new MailServiceException("Authenticated user is required.");
        }
        if (principal.userId() == null) {
            throw new MailServiceException("JWT must contain user id claim (uid).");
        }
        return principal.userId();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new MailServiceException(message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
