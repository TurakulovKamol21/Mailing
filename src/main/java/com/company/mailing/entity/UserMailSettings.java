package com.company.mailing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "user_mail_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_mail_settings_owner_user_id", columnNames = "owner_user_id")
)
public class UserMailSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private UUID ownerUserId;

    @Column(name = "imap_host", nullable = false, length = 255)
    private String imapHost;

    @Column(name = "imap_port", nullable = false)
    private int imapPort;

    @Column(name = "imap_ssl", nullable = false)
    private boolean imapSsl;

    @Column(name = "smtp_host", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "smtp_starttls", nullable = false)
    private boolean smtpStarttls;

    @Column(name = "smtp_ssl", nullable = false)
    private boolean smtpSsl;

    @Column(name = "mailbox_username", nullable = false, length = 320)
    private String mailboxUsername;

    @Column(name = "mailbox_password", nullable = false, length = 512)
    private String mailboxPassword;

    @Column(name = "from_email", length = 320)
    private String fromEmail;

    @Column(name = "default_folder", nullable = false, length = 255)
    private String defaultFolder;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    public UserMailSettings() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public int getImapPort() {
        return imapPort;
    }

    public void setImapPort(int imapPort) {
        this.imapPort = imapPort;
    }

    public boolean isImapSsl() {
        return imapSsl;
    }

    public void setImapSsl(boolean imapSsl) {
        this.imapSsl = imapSsl;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public boolean isSmtpStarttls() {
        return smtpStarttls;
    }

    public void setSmtpStarttls(boolean smtpStarttls) {
        this.smtpStarttls = smtpStarttls;
    }

    public boolean isSmtpSsl() {
        return smtpSsl;
    }

    public void setSmtpSsl(boolean smtpSsl) {
        this.smtpSsl = smtpSsl;
    }

    public String getMailboxUsername() {
        return mailboxUsername;
    }

    public void setMailboxUsername(String mailboxUsername) {
        this.mailboxUsername = mailboxUsername;
    }

    public String getMailboxPassword() {
        return mailboxPassword;
    }

    public void setMailboxPassword(String mailboxPassword) {
        this.mailboxPassword = mailboxPassword;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getDefaultFolder() {
        return defaultFolder;
    }

    public void setDefaultFolder(String defaultFolder) {
        this.defaultFolder = defaultFolder;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
