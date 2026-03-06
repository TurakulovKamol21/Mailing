package com.company.mailing.dto.auth;

import java.util.UUID;

public record SessionCheckReq(
        UUID userId,
        UUID sid
) {
}
