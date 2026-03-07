package com.company.mailing.config;

public record MailConnectionSettings(
        String imapHost,
        int imapPort,
        boolean imapSsl,
        String smtpHost,
        int smtpPort,
        boolean smtpStarttls,
        boolean smtpSsl,
        String username,
        String password,
        String fromEmail,
        String defaultFolder,
        int timeoutSeconds
) {
    public String resolveFrom() {
        if (fromEmail != null && !fromEmail.isBlank()) {
            return fromEmail.trim();
        }
        return username;
    }

    public String resolveDefaultFolder() {
        if (defaultFolder == null || defaultFolder.isBlank()) {
            return "INBOX";
        }
        return defaultFolder.trim();
    }
}
