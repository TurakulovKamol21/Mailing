package com.company.mailing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MailSettingsRequest(
        @NotBlank String imapHost,
        @Min(1) @Max(65535) int imapPort,
        boolean imapSsl,
        @NotBlank String smtpHost,
        @Min(1) @Max(65535) int smtpPort,
        boolean smtpStarttls,
        boolean smtpSsl,
        @Email @NotBlank String username,
        String password,
        @Email String fromEmail,
        String defaultFolder,
        @Min(1) @Max(300) int timeoutSeconds
) {
}
