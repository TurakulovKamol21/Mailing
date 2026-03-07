package com.company.mailing.security;

import com.company.mailing.exception.MailServiceException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailPasswordCryptoService {

    private static final String ENCRYPTION_PREFIX = "v1";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey secretKey;

    public MailPasswordCryptoService(
            @Value("${security.mail-settings.encryption-key:}") String encryptionKey
    ) {
        String value = encryptionKey == null ? "" : encryptionKey.trim();
        if (value.isBlank()) {
            throw new IllegalStateException("MAIL_SETTINGS_ENCRYPTION_KEY is required.");
        }
        this.secretKey = new SecretKeySpec(resolveKeyBytes(value), "AES");
    }

    public String encrypt(String plainText) {
        String value = plainText == null ? "" : plainText;
        if (value.isBlank()) {
            throw new MailServiceException("Mailbox password is empty.");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            return ENCRYPTION_PREFIX
                    + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new MailServiceException("Could not encrypt mailbox password.", ex);
        }
    }

    public String decrypt(String encryptedValue) {
        String value = encryptedValue == null ? "" : encryptedValue.trim();
        if (value.isEmpty()) {
            return "";
        }

        if (!isEncrypted(value)) {
            return value;
        }

        String[] parts = value.split(":", 3);
        if (parts.length != 3) {
            throw new MailServiceException("Stored mailbox password format is invalid.");
        }

        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] payload = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(payload);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new MailServiceException("Could not decrypt mailbox password.", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX + ":");
    }

    private byte[] resolveKeyBytes(String source) {
        byte[] decoded = tryDecodeBase64(source);
        if (decoded != null && (decoded.length == 16 || decoded.length == 24 || decoded.length == 32)) {
            return decoded;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not derive encryption key.", ex);
        }
    }

    private byte[] tryDecodeBase64(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
