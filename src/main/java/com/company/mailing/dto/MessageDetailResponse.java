package com.company.mailing.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageDetailResponse(
        long uid,
        String messageId,
        String inReplyTo,
        String references,
        String subject,
        String fromEmail,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        OffsetDateTime date,
        boolean seen,
        List<String> flags,
        String textBody,
        String htmlBody,
        List<AttachmentInfoResponse> attachments
) {
}
