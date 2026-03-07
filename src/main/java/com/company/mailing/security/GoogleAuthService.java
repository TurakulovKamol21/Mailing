package com.company.mailing.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GoogleAuthService {

    private final GoogleAuthProperties googleAuthProperties;
    private final RestClient restClient;

    public GoogleAuthService(GoogleAuthProperties googleAuthProperties) {
        this.googleAuthProperties = googleAuthProperties;
        this.restClient = RestClient.create();
    }

    public GoogleUserProfile verify(String idToken) {
        String clientId = normalize(googleAuthProperties.getClientId());
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google login is not configured.");
        }

        GoogleTokenInfoResponse tokenInfo;
        try {
            String url = UriComponentsBuilder.fromUriString(googleAuthProperties.getTokenInfoUrl())
                    .queryParam("id_token", idToken)
                    .build()
                    .toUriString();
            tokenInfo = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(GoogleTokenInfoResponse.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token.");
        }

        if (tokenInfo == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token.");
        }

        String aud = normalize(tokenInfo.aud());
        String email = normalize(tokenInfo.email());
        String subject = normalize(tokenInfo.sub());
        if (!clientId.equals(aud)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google client id mismatch.");
        }
        if (!"true".equalsIgnoreCase(String.valueOf(tokenInfo.emailVerified()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified.");
        }
        if (email == null || subject == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token does not contain identity.");
        }

        UUID userId = UUID.nameUUIDFromBytes(("google-user:" + subject).getBytes(StandardCharsets.UTF_8));
        return new GoogleUserProfile(userId, email.toLowerCase(Locale.ROOT), normalize(tokenInfo.name()), subject);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record GoogleUserProfile(UUID userId, String email, String name, String googleSubject) {
    }

    private record GoogleTokenInfoResponse(
            String sub,
            String aud,
            String email,
            String name,
            @JsonProperty("email_verified") String emailVerified
    ) {
    }
}
