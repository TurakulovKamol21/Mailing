package com.company.mailing.dto;

import jakarta.validation.constraints.NotBlank;

public record MoveMessageRequest(@NotBlank String targetFolder) {
}
