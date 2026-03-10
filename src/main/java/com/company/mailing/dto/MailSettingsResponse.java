package com.company.mailing.dto;

public record MailSettingsResponse(
        boolean configured,
        boolean hasPassword,
        String provider,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        String smtpHost,
        int smtpPort,
        boolean smtpStarttls,
        boolean smtpSsl,
        String username,
        String fromEmail,
        String defaultFolder,
        int timeoutSeconds
) {
}
