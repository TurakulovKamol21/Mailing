package com.company.mailing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import java.util.List;

public record SendMessageRequest(
        List<@Email String> to,
        List<@Email String> cc,
        List<@Email String> bcc,
        String subject,
        String bodyText,
        String bodyHtml,
        @Email String fromEmail,
        @Email String replyTo,
        String inReplyTo,
        String references,
        List<@Valid AttachmentInput> attachments
) {
    public SendMessageRequest {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        subject = subject == null ? "" : subject;
        replyTo = normalizeNullable(replyTo);
        inReplyTo = normalizeNullable(inReplyTo);
        references = normalizeNullable(references);
    }

    @AssertTrue(message = "At least one recipient must be provided.")
    public boolean hasRecipient() {
        return !to.isEmpty() || !cc.isEmpty() || !bcc.isEmpty();
    }

    @AssertTrue(message = "bodyText or bodyHtml must be provided.")
    public boolean hasBody() {
        return hasText(bodyText) || hasText(bodyHtml);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
