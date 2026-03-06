package com.company.mailing.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageSummaryResponse(
        long uid,
        String subject,
        String fromEmail,
        List<String> to,
        OffsetDateTime date,
        boolean seen,
        List<String> flags,
        String snippet
) {
}
