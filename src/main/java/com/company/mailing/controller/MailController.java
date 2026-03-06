package com.company.mailing.controller;

import com.company.mailing.dto.ConnectMailboxRequest;
import com.company.mailing.dto.DeleteStatusResponse;
import com.company.mailing.dto.MarkReadRequest;
import com.company.mailing.dto.MessageDetailResponse;
import com.company.mailing.dto.MessageListResponse;
import com.company.mailing.dto.MoveMessageRequest;
import com.company.mailing.dto.MoveStatusResponse;
import com.company.mailing.dto.ReadStatusResponse;
import com.company.mailing.dto.SendMessageRequest;
import com.company.mailing.dto.SendMessageResponse;
import com.company.mailing.exception.MailServiceException;
import com.company.mailing.security.JwtPrincipal;
import com.company.mailing.security.MailCredentials;
import com.company.mailing.security.MailSessionService;
import com.company.mailing.service.MailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/mail")
public class MailController {

    private final MailService mailService;
    private final MailSessionService mailSessionService;

    public MailController(MailService mailService, MailSessionService mailSessionService) {
        this.mailService = mailService;
        this.mailSessionService = mailSessionService;
    }

    @PostMapping("/test-connection")
    public Map<String, String> testConnection(@AuthenticationPrincipal JwtPrincipal principal) {
        mailService.testConnection(resolveCredentials(principal));
        return Map.of("status", "connected");
    }

    @PostMapping("/connect")
    public Map<String, String> connectMailbox(
            @Valid @RequestBody ConnectMailboxRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        String mailboxUsername = resolveMailboxUsername(request.username(), principal);
        MailCredentials credentials = new MailCredentials(mailboxUsername, request.password());
        mailService.testConnection(credentials);
        mailSessionService.bindCredentials(principal, credentials);
        return Map.of("status", "connected", "mailbox", mailboxUsername);
    }

    @DeleteMapping("/connect")
    public Map<String, String> disconnectMailbox(@AuthenticationPrincipal JwtPrincipal principal) {
        mailSessionService.clearCredentials(principal);
        return Map.of("status", "disconnected");
    }

    @GetMapping("/folders")
    public List<String> listFolders(@AuthenticationPrincipal JwtPrincipal principal) {
        return mailService.listFolders(resolveCredentials(principal));
    }

    @GetMapping("/messages")
    public MessageListResponse listMessages(
            @RequestParam(required = false) String folder,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "false") boolean unseenOnly,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return mailService.listMessages(resolveCredentials(principal), folder, limit, offset, unseenOnly);
    }

    @GetMapping("/messages/{uid}")
    public MessageDetailResponse getMessage(
            @PathVariable long uid,
            @RequestParam(required = false) String folder,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return mailService.getMessage(resolveCredentials(principal), uid, folder);
    }

    @GetMapping("/messages/{uid}/attachments/{index}")
    public ResponseEntity<ByteArrayResource> getAttachment(
            @PathVariable long uid,
            @PathVariable @Min(0) int index,
            @RequestParam(required = false) String folder,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        MailService.AttachmentDownloadData attachment = mailService.getAttachment(
                resolveCredentials(principal),
                uid,
                folder,
                index
        );
        MediaType mediaType = resolveMediaType(attachment.contentType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(attachment.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(attachment.content()));
    }

    @PostMapping("/messages/{uid}/read")
    public ReadStatusResponse markRead(
            @PathVariable long uid,
            @Valid @RequestBody MarkReadRequest request,
            @RequestParam(required = false) String folder,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        boolean read = Boolean.TRUE.equals(request.read());
        mailService.markRead(resolveCredentials(principal), uid, read, folder);
        return new ReadStatusResponse(uid, read);
    }

    @PostMapping("/messages/{uid}/move")
    public MoveStatusResponse moveMessage(
            @PathVariable long uid,
            @Valid @RequestBody MoveMessageRequest request,
            @RequestParam(required = false) String folder,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        mailService.moveMessage(resolveCredentials(principal), uid, folder, request.targetFolder());
        return new MoveStatusResponse(uid, request.targetFolder());
    }

    @DeleteMapping("/messages/{uid}")
    public DeleteStatusResponse deleteMessage(
            @PathVariable long uid,
            @RequestParam(required = false) String folder,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        mailService.deleteMessage(resolveCredentials(principal), uid, folder);
        return new DeleteStatusResponse(uid, true);
    }

    @PostMapping("/send")
    public SendMessageResponse sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return mailService.sendMessage(resolveCredentials(principal), request);
    }

    private MediaType resolveMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private MailCredentials resolveCredentials(JwtPrincipal principal) {
        try {
            return mailSessionService.resolveCredentials(principal);
        } catch (IllegalArgumentException ex) {
            throw new MailServiceException("Mailbox is not connected. Call /mail/connect first.");
        }
    }

    private String resolveMailboxUsername(String requestedUsername, JwtPrincipal principal) {
        if (requestedUsername != null && !requestedUsername.isBlank()) {
            return requestedUsername.trim();
        }
        if (principal != null && principal.username() != null && !principal.username().isBlank()) {
            return principal.username().trim();
        }
        throw new MailServiceException("Mailbox username is required.");
    }
}
