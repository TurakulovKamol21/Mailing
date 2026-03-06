package com.company.mailing.dto;

public record AttachmentInfoResponse(
        int index,
        String filename,
        String contentType,
        Long size
) {
}
