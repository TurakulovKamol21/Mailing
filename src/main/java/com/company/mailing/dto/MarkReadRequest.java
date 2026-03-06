package com.company.mailing.dto;

public record MarkReadRequest(Boolean read) {
    public MarkReadRequest {
        read = read == null ? Boolean.TRUE : read;
    }
}
