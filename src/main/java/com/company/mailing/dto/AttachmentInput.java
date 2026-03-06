package com.company.mailing.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachmentInput(
        @NotBlank String filename,
        @NotBlank String contentBase64,
        String contentType
) {
    public AttachmentInput {
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
    }
}
