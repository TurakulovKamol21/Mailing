package com.company.mailing.security;

public record MailCredentials(String username, String password) {

    public MailCredentials {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Mail username is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Mail password is required.");
        }
        username = normalizedUsername;
    }
}
