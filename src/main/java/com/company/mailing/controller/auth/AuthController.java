package com.company.mailing.controller.auth;

import com.company.mailing.dto.auth.GoogleLoginRequest;
import com.company.mailing.dto.auth.LoginRequest;
import com.company.mailing.dto.auth.RefreshRequest;
import com.company.mailing.dto.auth.TokenResponse;
import com.company.mailing.exception.MailServiceException;
import com.company.mailing.security.AppAuthProperties;
import com.company.mailing.security.GoogleAuthService;
import com.company.mailing.security.GoogleAuthProperties;
import com.company.mailing.security.JwtPrincipal;
import com.company.mailing.security.JwtService;
import com.company.mailing.security.MailCredentials;
import com.company.mailing.security.MailSessionService;
import com.company.mailing.security.MailUserSession;
import com.company.mailing.service.MailService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AppAuthProperties appAuthProperties;
    private final JwtService jwtService;
    private final MailSessionService mailSessionService;
    private final MailService mailService;
    private final GoogleAuthService googleAuthService;
    private final GoogleAuthProperties googleAuthProperties;

    public AuthController(
            AppAuthProperties appAuthProperties,
            JwtService jwtService,
            MailSessionService mailSessionService,
            MailService mailService,
            GoogleAuthService googleAuthService,
            GoogleAuthProperties googleAuthProperties
    ) {
        this.appAuthProperties = appAuthProperties;
        this.jwtService = jwtService;
        this.mailSessionService = mailSessionService;
        this.mailService = mailService;
        this.googleAuthService = googleAuthService;
        this.googleAuthProperties = googleAuthProperties;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        String clientId = googleAuthProperties.getClientId() == null ? "" : googleAuthProperties.getClientId().trim();
        return Map.of(
                "googleClientId", clientId,
                "googleEnabled", !clientId.isBlank()
        );
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String username = normalizeUsername(request.username());
        MailCredentials credentials = new MailCredentials(username, request.password());

        try {
            mailService.testConnection(credentials);
        } catch (MailServiceException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid mailbox username or password.");
        }

        UUID userId = deriveLocalUserId(username);
        List<String> roles = appAuthProperties.resolveRoles();
        MailUserSession session = mailSessionService.createSession(credentials, userId, roles);
        return generateTokens(session.username(), session.roles(), session.userId(), session.sid());
    }

    @PostMapping("/google")
    public TokenResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleAuthService.GoogleUserProfile profile = googleAuthService.verify(request.idToken());
        List<String> roles = appAuthProperties.resolveRoles();
        MailUserSession session = mailSessionService.createGoogleSession(profile.email(), profile.userId(), roles);
        return generateTokens(session.username(), session.roles(), session.userId(), session.sid());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        final JwtPrincipal principal;
        try {
            principal = jwtService.parseRefreshToken(request.refreshToken());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        final MailUserSession session;
        try {
            session = mailSessionService.requireActive(principal.sid(), principal.username());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again.");
        }

        return generateTokens(session.username(), session.roles(), session.userId(), session.sid());
    }

    private TokenResponse generateTokens(String username, List<String> roles, UUID userId, UUID sid) {
        String accessToken = jwtService.generateAccessToken(username, roles, userId, sid);
        String refreshToken = jwtService.generateRefreshToken(username, userId, sid);
        return new TokenResponse("Bearer", accessToken, refreshToken, jwtService.getAccessTokenExpiresInSeconds());
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private UUID deriveLocalUserId(String username) {
        return UUID.nameUUIDFromBytes(("mail-user:" + username).getBytes(StandardCharsets.UTF_8));
    }
}
