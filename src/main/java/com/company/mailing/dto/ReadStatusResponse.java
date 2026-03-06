package com.company.mailing.dto;

public record ReadStatusResponse(
        long uid,
        boolean read
) {
}
