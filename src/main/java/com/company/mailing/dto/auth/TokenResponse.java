package com.company.mailing.dto.auth;

public record TokenResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
