package com.company.mailing.dto;

import java.util.List;

public record MessageThreadResponse(
        long anchorUid,
        String folder,
        int total,
        List<MessageDetailResponse> messages
) {
}
