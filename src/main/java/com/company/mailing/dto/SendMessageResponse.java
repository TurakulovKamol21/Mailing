package com.company.mailing.dto;

public record SendMessageResponse(
        String status,
        int recipientCount
) {
}
