package com.company.mailing.service;

import com.company.mailing.config.MailProperties;
import com.company.mailing.dto.AttachmentInfoResponse;
import com.company.mailing.dto.AttachmentInput;
import com.company.mailing.dto.MessageDetailResponse;
import com.company.mailing.dto.MessageListResponse;
import com.company.mailing.dto.MessageSummaryResponse;
import com.company.mailing.dto.SendMessageRequest;
import com.company.mailing.dto.SendMessageResponse;
import com.company.mailing.exception.MailServiceException;
import com.company.mailing.security.MailCredentials;
import com.sun.mail.imap.IMAPFolder;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final MailProperties properties;

    public MailService(MailProperties properties) {
        this.properties = properties;
    }

    public void testConnection(MailCredentials credentials) {
        Store store = null;
        Transport transport = null;
        try {
            store = openStore(credentials);
            Session smtpSession = createSmtpSession(credentials);
            transport = openSmtpTransport(smtpSession, credentials);
        } catch (MessagingException ex) {
            throw new MailServiceException("Mail connection failed: " + ex.getMessage(), ex);
        } finally {
            closeService(transport);
            closeStore(store);
        }
    }

    public List<String> listFolders(MailCredentials credentials) {
        Store store = null;
        try {
            store = openStore(credentials);
            Folder defaultFolder = store.getDefaultFolder();
            Folder[] folders = defaultFolder.list("*");
            Set<String> names = new LinkedHashSet<>();
            for (Folder folder : folders) {
                if (folder != null && folder.getFullName() != null && !folder.getFullName().isBlank()) {
                    names.add(folder.getFullName());
                }
            }
            if (names.isEmpty()) {
                names.add(properties.getDefaultFolder());
            }
            return List.copyOf(names);
        } catch (MessagingException ex) {
            throw new MailServiceException("Could not fetch folders: " + ex.getMessage(), ex);
        } finally {
            closeStore(store);
        }
    }

    public MessageListResponse listMessages(
            MailCredentials credentials,
            String folderName,
            int limit,
            int offset,
            boolean unseenOnly
    ) {
        Store store = null;
        IMAPFolder folder = null;
        try {
            store = openStore(credentials);
            folder = openFolder(store, resolveFolder(folderName), Folder.READ_ONLY);

            Message[] pageMessages;
            int total;

            if (unseenOnly) {
                Message[] unseenMessages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                total = unseenMessages.length;
                pageMessages = paginateFromNewest(unseenMessages, limit, offset);
            } else {
                total = folder.getMessageCount();
                pageMessages = pageByMessageNumber(folder, limit, offset, total);
            }

            prefetchSummaryData(folder, pageMessages);
            List<MessageSummaryResponse> items = new ArrayList<>(pageMessages.length);
            for (Message message : pageMessages) {
                items.add(toSummary(folder, message));
            }

            return new MessageListResponse(total, items);
        } catch (MessagingException ex) {
            throw new MailServiceException("Could not fetch message list: " + ex.getMessage(), ex);
        } finally {
            closeFolder(folder, false);
            closeStore(store);
        }
    }

    public MessageDetailResponse getMessage(MailCredentials credentials, long uid, String folderName) {
        Store store = null;
        IMAPFolder folder = null;
        try {
            store = openStore(credentials);
            folder = openFolder(store, resolveFolder(folderName), Folder.READ_ONLY);
            Message message = folder.getMessageByUID(uid);
            if (message == null) {
                throw new MailServiceException("Message not found: " + uid);
            }
            return toDetail(folder, message);
        } catch (MessagingException | IOException ex) {
            throw new MailServiceException("Could not read message: " + ex.getMessage(), ex);
        } finally {
            closeFolder(folder, false);
            closeStore(store);
        }
    }

    public AttachmentDownloadData getAttachment(
            MailCredentials credentials,
            long uid,
            String folderName,
            int attachmentIndex
    ) {
        if (attachmentIndex < 0) {
            throw new MailServiceException("Attachment index must be non-negative.");
        }

        Store store = null;
        IMAPFolder folder = null;
        try {
            store = openStore(credentials);
            folder = openFolder(store, resolveFolder(folderName), Folder.READ_ONLY);
            Message message = folder.getMessageByUID(uid);
            if (message == null) {
                throw new MailServiceException("Message not found: " + uid);
            }

            AttachmentDownloadData attachment = findAttachment(message, attachmentIndex, new int[]{0});
            if (attachment == null) {
                throw new MailServiceException("Attachment not found at index: " + attachmentIndex);
            }
            return attachment;
        } catch (MessagingException | IOException ex) {
            throw new MailServiceException("Could not read attachment: " + ex.getMessage(), ex);
        } finally {
            closeFolder(folder, false);
            closeStore(store);
        }
    }

    public void markRead(MailCredentials credentials, long uid, boolean read, String folderName) {
        Store store = null;
        IMAPFolder folder = null;
        try {
            store = openStore(credentials);
            folder = openFolder(store, resolveFolder(folderName), Folder.READ_WRITE);
            Message message = folder.getMessageByUID(uid);
            if (message == null) {
                throw new MailServiceException("Message not found: " + uid);
            }
            message.setFlag(Flags.Flag.SEEN, read);
        } catch (MessagingException ex) {
            throw new MailServiceException("Could not update read status: " + ex.getMessage(), ex);
        } finally {
            closeFolder(folder, false);
            closeStore(store);
        }
    }

    public void moveMessage(
            MailCredentials credentials,
            long uid,
            String folderName,
            String targetFolderName
    ) {
        Store store = null;
        IMAPFolder sourceFolder = null;
        Folder targetFolder = null;
        try {
            store = openStore(credentials);
            sourceFolder = openFolder(store, resolveFolder(folderName), Folder.READ_WRITE);
            targetFolder = store.getFolder(targetFolderName);
            if (targetFolder == null) {
                throw new MailServiceException("Target folder not found: " + targetFolderName);
            }
            if (!targetFolder.exists() && !targetFolder.create(Folder.HOLDS_MESSAGES)) {
                throw new MailServiceException("Target folder could not be created: " + targetFolderName);
            }

            Message message = sourceFolder.getMessageByUID(uid);
            if (message == null) {
                throw new MailServiceException("Message not found: " + uid);
            }

            sourceFolder.copyMessages(new Message[]{message}, targetFolder);
            message.setFlag(Flags.Flag.DELETED, true);
            sourceFolder.expunge();
        } catch (MessagingException ex) {
            throw new MailServiceException("Could not move message: " + ex.getMessage(), ex);
        } finally {
            closeFolder(sourceFolder, false);
            closeStore(store);
        }
    }

    public void deleteMessage(MailCredentials credentials, long uid, String folderName) {
        Store store = null;
        IMAPFolder folder = null;
        try {
            store = openStore(credentials);
            folder = openFolder(store, resolveFolder(folderName), Folder.READ_WRITE);
            Message message = folder.getMessageByUID(uid);
            if (message == null) {
                throw new MailServiceException("Message not found: " + uid);
            }
            message.setFlag(Flags.Flag.DELETED, true);
            folder.expunge();
        } catch (MessagingException ex) {
            throw new MailServiceException("Could not delete message: " + ex.getMessage(), ex);
        } finally {
            closeFolder(folder, false);
            closeStore(store);
        }
    }

    public SendMessageResponse sendMessage(MailCredentials credentials, SendMessageRequest request) {
        Session smtpSession = createSmtpSession(credentials);
        Transport transport = null;
        try {
            MimeMessage mimeMessage = buildMimeMessage(smtpSession, request, credentials);
            Address[] recipients = mimeMessage.getAllRecipients();
            if (recipients == null || recipients.length == 0) {
                throw new MailServiceException("At least one recipient must be provided.");
            }

            transport = openSmtpTransport(smtpSession, credentials);
            transport.sendMessage(mimeMessage, recipients);
            return new SendMessageResponse("sent", recipients.length);
        } catch (MessagingException | IOException ex) {
            throw new MailServiceException("Could not send message: " + ex.getMessage(), ex);
        } finally {
            closeService(transport);
        }
    }

    private MessageSummaryResponse toSummary(IMAPFolder folder, Message message)
            throws MessagingException {
        long uid = folder.getUID(message);
        String subject = safeString(message.getSubject());
        String snippet = buildSummarySnippet(subject, extractFirstEmail(message.getFrom()));

        return new MessageSummaryResponse(
                uid,
                subject,
                extractFirstEmail(message.getFrom()),
                List.of(),
                toOffsetDateTime(message.getSentDate(), message.getReceivedDate()),
                message.isSet(Flags.Flag.SEEN),
                extractFlags(message.getFlags()),
                snippet
        );
    }

    private MessageDetailResponse toDetail(IMAPFolder folder, Message message)
            throws MessagingException, IOException {
        long uid = folder.getUID(message);
        ParsedBody parsedBody = parseBody(message);

        return new MessageDetailResponse(
                uid,
                extractSingleHeader(message, "Message-ID"),
                extractSingleHeader(message, "In-Reply-To"),
                extractSingleHeader(message, "References"),
                safeString(message.getSubject()),
                extractFirstEmail(message.getFrom()),
                extractEmails(message.getRecipients(Message.RecipientType.TO)),
                extractEmails(message.getRecipients(Message.RecipientType.CC)),
                extractEmails(message.getRecipients(Message.RecipientType.BCC)),
                toOffsetDateTime(message.getSentDate(), message.getReceivedDate()),
                message.isSet(Flags.Flag.SEEN),
                extractFlags(message.getFlags()),
                parsedBody.textBody(),
                parsedBody.htmlBody(),
                parsedBody.attachments()
        );
    }

    private ParsedBody parseBody(Part part) throws MessagingException, IOException {
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<AttachmentInfoResponse> attachments = new ArrayList<>();
        collectParts(part, text, html, attachments, new int[]{0});
        return new ParsedBody(
                blankToNull(text.toString()),
                blankToNull(html.toString()),
                List.copyOf(attachments)
        );
    }

    private void collectParts(
            Part part,
            StringBuilder text,
            StringBuilder html,
            List<AttachmentInfoResponse> attachments,
            int[] attachmentCounter
    )
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    collectParts(multipart.getBodyPart(i), text, html, attachments, attachmentCounter);
                }
            }
            return;
        }

        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                collectParts(nestedPart, text, html, attachments, attachmentCounter);
            }
            return;
        }

        String filename = part.getFileName();
        String disposition = part.getDisposition();
        boolean attachment = isAttachmentPart(filename, disposition);

        if (attachment) {
            int attachmentIndex = attachmentCounter[0]++;
            attachments.add(toAttachmentInfo(part, attachmentIndex));
            return;
        }

        Object content = part.getContent();
        if (!(content instanceof String bodyContent)) {
            return;
        }

        if (part.isMimeType("text/plain")) {
            appendContent(text, bodyContent);
        } else if (part.isMimeType("text/html")) {
            appendContent(html, bodyContent);
        }
    }

    private AttachmentDownloadData findAttachment(Part part, int targetIndex, int[] attachmentCounter)
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    AttachmentDownloadData nested = findAttachment(multipart.getBodyPart(i), targetIndex, attachmentCounter);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
            return null;
        }

        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                return findAttachment(nestedPart, targetIndex, attachmentCounter);
            }
            return null;
        }

        String filename = part.getFileName();
        String disposition = part.getDisposition();
        if (!isAttachmentPart(filename, disposition)) {
            return null;
        }

        int currentIndex = attachmentCounter[0]++;
        if (currentIndex != targetIndex) {
            return null;
        }

        return toAttachmentDownloadData(part, currentIndex);
    }

    private boolean isAttachmentPart(String filename, String disposition) {
        boolean hasAttachmentName = filename != null && !filename.isBlank();
        return Part.ATTACHMENT.equalsIgnoreCase(disposition) || hasAttachmentName;
    }

    private AttachmentInfoResponse toAttachmentInfo(Part part, int attachmentIndex) throws MessagingException {
        return new AttachmentInfoResponse(
                attachmentIndex,
                resolveAttachmentFilename(part, attachmentIndex),
                normalizeContentType(part.getContentType()),
                toAttachmentSize(part.getSize())
        );
    }

    private AttachmentDownloadData toAttachmentDownloadData(Part part, int attachmentIndex)
            throws MessagingException, IOException {
        String filename = resolveAttachmentFilename(part, attachmentIndex);
        String contentType = normalizeContentType(part.getContentType());
        byte[] content;
        try (var inputStream = part.getInputStream()) {
            content = inputStream.readAllBytes();
        }
        return new AttachmentDownloadData(filename, contentType, content);
    }

    private String resolveAttachmentFilename(Part part, int attachmentIndex) throws MessagingException {
        String filename = part.getFileName();
        if (filename != null && !filename.isBlank()) {
            return decodeFilename(filename);
        }
        return "attachment-" + (attachmentIndex + 1);
    }

    private Long toAttachmentSize(int size) {
        return size < 0 ? null : (long) size;
    }

    private void appendContent(StringBuilder target, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(content.trim());
    }

    private MimeMessage buildMimeMessage(
            Session session,
            SendMessageRequest request,
            MailCredentials credentials
    )
            throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(credentials.username()));
        setRecipients(message, Message.RecipientType.TO, request.to());
        setRecipients(message, Message.RecipientType.CC, request.cc());
        setRecipients(message, Message.RecipientType.BCC, request.bcc());

        if (request.replyTo() != null && !request.replyTo().isBlank()) {
            message.setReplyTo(new Address[]{new InternetAddress(request.replyTo())});
        }

        message.setSubject(safeString(request.subject()), StandardCharsets.UTF_8.name());
        setThreadHeaders(message, request);

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart contentPart = new MimeBodyPart();
        setBodyContent(contentPart, request.bodyText(), request.bodyHtml());
        mixed.addBodyPart(contentPart);

        for (AttachmentInput attachment : request.attachments()) {
            mixed.addBodyPart(buildAttachmentPart(attachment));
        }

        message.setContent(mixed);
        message.saveChanges();
        return message;
    }

    private void setRecipients(MimeMessage message, Message.RecipientType type, List<String> recipients)
            throws MessagingException {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        InternetAddress[] addresses = recipients.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::toInternetAddress)
                .toArray(InternetAddress[]::new);
        if (addresses.length > 0) {
            message.setRecipients(type, addresses);
        }
    }

    private void setThreadHeaders(MimeMessage message, SendMessageRequest request) throws MessagingException {
        setOptionalHeader(message, "In-Reply-To", request.inReplyTo());
        setOptionalHeader(message, "References", request.references());
    }

    private void setOptionalHeader(MimeMessage message, String name, String value) throws MessagingException {
        if (value == null || value.isBlank()) {
            return;
        }
        String sanitized = sanitizeHeaderValue(value);
        if (!sanitized.isBlank()) {
            message.setHeader(name, sanitized);
        }
    }

    private InternetAddress toInternetAddress(String value) {
        try {
            return new InternetAddress(value);
        } catch (MessagingException ex) {
            throw new MailServiceException("Invalid email address: " + value, ex);
        }
    }

    private void setBodyContent(MimeBodyPart bodyPart, String bodyText, String bodyHtml)
            throws MessagingException {
        boolean hasText = bodyText != null && !bodyText.isBlank();
        boolean hasHtml = bodyHtml != null && !bodyHtml.isBlank();

        if (hasText && hasHtml) {
            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(bodyText, StandardCharsets.UTF_8.name());
            alternative.addBodyPart(textPart);

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(bodyHtml, "text/html; charset=UTF-8");
            alternative.addBodyPart(htmlPart);

            bodyPart.setContent(alternative);
            return;
        }

        if (hasHtml) {
            bodyPart.setContent(bodyHtml, "text/html; charset=UTF-8");
        } else {
            bodyPart.setText(bodyText == null ? "" : bodyText, StandardCharsets.UTF_8.name());
        }
    }

    private MimeBodyPart buildAttachmentPart(AttachmentInput attachment)
            throws MessagingException, IOException {
        byte[] content;
        try {
            content = Base64.getDecoder().decode(attachment.contentBase64());
        } catch (IllegalArgumentException ex) {
            throw new MailServiceException("Attachment base64 is invalid: " + attachment.filename(), ex);
        }

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(content, attachment.contentType())));
        attachmentPart.setFileName(attachment.filename());
        attachmentPart.setDisposition(Part.ATTACHMENT);
        return attachmentPart;
    }

    private List<String> extractEmails(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (Address address : addresses) {
            if (address instanceof InternetAddress internetAddress) {
                String email = internetAddress.getAddress();
                if (email != null && !email.isBlank()) {
                    values.add(email);
                }
                continue;
            }

            if (address != null) {
                String value = address.toString();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private String extractFirstEmail(Address[] addresses) {
        List<String> values = extractEmails(addresses);
        return values.isEmpty() ? "" : values.get(0);
    }

    private List<String> extractFlags(Flags flags) {
        List<String> result = new ArrayList<>();

        if (flags.contains(Flags.Flag.ANSWERED)) {
            result.add("\\Answered");
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            result.add("\\Deleted");
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            result.add("\\Draft");
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            result.add("\\Flagged");
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            result.add("\\Recent");
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            result.add("\\Seen");
        }
        result.addAll(Arrays.asList(flags.getUserFlags()));
        return List.copyOf(result);
    }

    private String buildSnippet(String textBody, String htmlBody) {
        String source;
        if (textBody != null && !textBody.isBlank()) {
            source = textBody;
        } else if (htmlBody != null && !htmlBody.isBlank()) {
            source = htmlBody.replaceAll("<[^>]+>", " ");
        } else {
            source = "";
        }

        String normalized = source.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 140);
    }

    private String buildSummarySnippet(String subject, String fromEmail) {
        String summary = (subject == null ? "" : subject).trim();
        if (!summary.isBlank()) {
            return summary.length() > 140 ? summary.substring(0, 140) : summary;
        }
        String fallback = (fromEmail == null ? "" : fromEmail).trim();
        if (fallback.isBlank()) {
            return "";
        }
        return fallback.length() > 140 ? fallback.substring(0, 140) : fallback;
    }

    private OffsetDateTime toOffsetDateTime(Date sentDate, Date receivedDate) {
        Date date = sentDate != null ? sentDate : receivedDate;
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private Store openStore(MailCredentials credentials) throws MessagingException {
        Session session = Session.getInstance(buildImapProperties());
        String protocol = properties.isImapSsl() ? "imaps" : "imap";
        Store store = session.getStore(protocol);
        store.connect(
                properties.getImapHost(),
                properties.getImapPort(),
                credentials.username(),
                credentials.password()
        );
        return store;
    }

    private Message[] paginateFromNewest(Message[] sourceMessages, int limit, int offset) {
        if (sourceMessages == null || sourceMessages.length == 0 || limit <= 0) {
            return new Message[0];
        }

        List<Message> page = new ArrayList<>(limit);
        int startIndex = sourceMessages.length - 1 - offset;
        for (int index = startIndex; index >= 0 && page.size() < limit; index--) {
            page.add(sourceMessages[index]);
        }
        return page.toArray(new Message[0]);
    }

    private Message[] pageByMessageNumber(IMAPFolder folder, int limit, int offset, int total)
            throws MessagingException {
        if (total <= 0 || limit <= 0) {
            return new Message[0];
        }

        List<Message> page = new ArrayList<>(limit);
        int messageNumber = total - offset;
        while (messageNumber >= 1 && page.size() < limit) {
            page.add(folder.getMessage(messageNumber));
            messageNumber--;
        }
        return page.toArray(new Message[0]);
    }

    private void prefetchSummaryData(IMAPFolder folder, Message[] messages) throws MessagingException {
        if (messages == null || messages.length == 0) {
            return;
        }
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(messages, profile);
    }

    private IMAPFolder openFolder(Store store, String folderName, int mode) throws MessagingException {
        Folder rawFolder = store.getFolder(folderName);
        if (rawFolder == null || !rawFolder.exists()) {
            throw new MailServiceException("Folder not found: " + folderName);
        }
        rawFolder.open(mode);
        if (!(rawFolder instanceof IMAPFolder imapFolder)) {
            throw new MailServiceException("Folder is not IMAP compatible: " + folderName);
        }
        return imapFolder;
    }

    private Session createSmtpSession(MailCredentials credentials) {
        Properties smtpProperties = buildSmtpProperties();
        return Session.getInstance(smtpProperties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(credentials.username(), credentials.password());
            }
        });
    }

    private Transport openSmtpTransport(Session session, MailCredentials credentials) throws MessagingException {
        Transport transport = session.getTransport("smtp");
        transport.connect(
                properties.getSmtpHost(),
                properties.getSmtpPort(),
                credentials.username(),
                credentials.password()
        );
        return transport;
    }

    private Properties buildImapProperties() {
        Properties props = new Properties();
        int timeoutMs = properties.getTimeoutSeconds() * 1000;

        props.put("mail.store.protocol", properties.isImapSsl() ? "imaps" : "imap");
        props.put("mail.imap.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.imap.timeout", String.valueOf(timeoutMs));
        props.put("mail.imaps.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.imaps.timeout", String.valueOf(timeoutMs));
        props.put("mail.imap.ssl.enable", String.valueOf(properties.isImapSsl()));
        props.put("mail.imaps.ssl.enable", String.valueOf(properties.isImapSsl()));
        return props;
    }

    private Properties buildSmtpProperties() {
        Properties props = new Properties();
        int timeoutMs = properties.getTimeoutSeconds() * 1000;

        props.put("mail.smtp.host", properties.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(properties.getSmtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.timeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.writetimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.starttls.enable", String.valueOf(properties.isSmtpStarttls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(properties.isSmtpSsl()));
        return props;
    }

    private void closeFolder(Folder folder, boolean expunge) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(expunge);
            }
        } catch (MessagingException ignored) {
            // no-op
        }
    }

    private void closeStore(Store store) {
        if (store == null) {
            return;
        }
        try {
            if (store.isConnected()) {
                store.close();
            }
        } catch (MessagingException ignored) {
            // no-op
        }
    }

    private void closeService(jakarta.mail.Service service) {
        if (service == null) {
            return;
        }
        try {
            if (service.isConnected()) {
                service.close();
            }
        } catch (MessagingException ignored) {
            // no-op
        }
    }

    private String resolveFolder(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return properties.getDefaultFolder();
        }
        return folderName.trim();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String decodeFilename(String value) {
        try {
            return MimeUtility.decodeText(value);
        } catch (UnsupportedEncodingException ex) {
            return value;
        }
    }

    private String normalizeContentType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return "application/octet-stream";
        }
        String normalized = rawContentType.split(";", 2)[0].trim();
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }

    private String extractSingleHeader(Message message, String headerName) throws MessagingException {
        String[] values = message.getHeader(headerName);
        if (values == null || values.length == 0) {
            return null;
        }
        return blankToNull(values[0]);
    }

    private String sanitizeHeaderValue(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AttachmentDownloadData(String filename, String contentType, byte[] content) {
    }

    private record ParsedBody(String textBody, String htmlBody, List<AttachmentInfoResponse> attachments) {
    }
}
