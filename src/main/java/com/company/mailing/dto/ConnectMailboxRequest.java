package com.company.mailing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ConnectMailboxRequest(
        @Email String username,
        @NotBlank String password
) {
}
