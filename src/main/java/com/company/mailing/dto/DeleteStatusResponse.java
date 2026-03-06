package com.company.mailing.dto;

public record DeleteStatusResponse(
        long uid,
        boolean deleted
) {
}
