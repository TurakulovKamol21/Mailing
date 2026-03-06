package com.company.mailing.dto;

import java.util.List;

public record MessageListResponse(
        int total,
        List<MessageSummaryResponse> messages
) {
}
