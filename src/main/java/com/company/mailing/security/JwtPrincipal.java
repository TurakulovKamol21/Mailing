package com.company.mailing.security;

import java.util.List;
import java.util.UUID;

public record JwtPrincipal(
        String username,
        List<String> roles,
        UUID userId,
        UUID sid
) {
}
