package com.smartdashboard.service.sync;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.*;
import com.smartdashboard.repository.*;
import com.smartdashboard.service.oauth.TokenRefreshService;
import com.smartdashboard.service.parse.AttachmentTextExtractorService;
import com.smartdashboard.service.parse.DeadlineParserService;
import com.smartdashboard.service.parse.OrganizationInferenceService;
import com.smartdashboard.service.parse.TagSuggestionService;
import jakarta.mail.*;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
public class ImapSyncService {

    private static final Logger log = LoggerFactory.getLogger(ImapSyncService.class);

    private final TokenRefreshService tokens;
    private final AesEncryptor encryptor;
    private final EmailMessageRepository emails;
    private final DeadlineParserService deadlines;
    private final OrganizationInferenceService orgs;
    private final TagSuggestionService tagger;
    private final AttachmentTextExtractorService attachmentExtractor;

    public ImapSyncService(TokenRefreshService tokens, AesEncryptor encryptor,
                           EmailMessageRepository emails,
                           DeadlineParserService deadlines,
                           OrganizationInferenceService orgs,
                           TagSuggestionService tagger,
                           AttachmentTextExtractorService attachmentExtractor) {
        this.tokens = tokens;
        this.encryptor = encryptor;
        this.emails = emails;
        this.deadlines = deadlines;
        this.orgs = orgs;
        this.tagger = tagger;
        this.attachmentExtractor = attachmentExtractor;
    }

    public void sync(MailAccount account) {
        if (account.getImapHost() == null || account.getImapHost().isBlank()) {
            log.debug("Skipping IMAP sync for {} (no host set)", account.getEmailAddress());
            return;
        }

        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", account.getImapHost());
            props.put("mail.imaps.port", account.getImapPort() != null ? String.valueOf(account.getImapPort()) : "993");
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "10000");
            props.put("mail.imaps.timeout", "15000");

            boolean isOAuth = account.getEncryptedRefreshToken() != null && !account.getEncryptedRefreshToken().isBlank();
            if (isOAuth) {
                props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            }

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");

            String credential;
            if (isOAuth) {
                credential = tokens.getValidAccessToken(account);
            } else if (account.getEncryptedAccessToken() != null) {
                credential = encryptor.decrypt(account.getEncryptedAccessToken());
            } else {
                throw new IllegalStateException("No password or OAuth credentials found for IMAP account: " + account.getEmailAddress());
            }

            store.connect(account.getImapHost(), account.getEmailAddress(), credential);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Date since = account.getLastSyncAt() == null
                ? new Date(System.currentTimeMillis() - 7L * 86400_000)
                : Date.from(account.getLastSyncAt());

            Message[] msgs = inbox.search(new ReceivedDateTerm(ComparisonTerm.GE, since));
            for (Message m : msgs) {
                String pid = String.valueOf(m.getMessageNumber());
                if (emails.existsByAccountAndProviderMessageId(account, pid)) continue;
                persist(account, m, pid);
            }
            account.setLastSyncAt(Instant.now());
            inbox.close(false);
            store.close();
            log.info("IMAP sync finished for {}", account.getEmailAddress());
        } catch (Exception e) {
            log.error("IMAP sync failed for {}: {}", account.getEmailAddress(), e.getMessage());
            throw new RuntimeException("IMAP sync failed for " + account.getEmailAddress(), e);
        }
    }

    private void persist(MailAccount account, Message m, String pid) throws Exception {
        EmailMessage em = new EmailMessage();
        em.setAccount(account);
        em.setProviderMessageId(pid);
        em.setSubject(m.getSubject());
        Address[] senders = m.getFrom();
        em.setSender(senders != null && senders.length > 0 ? senders[0].toString() : "Unknown");
        em.setReceivedDate(m.getReceivedDate() != null
            ? m.getReceivedDate().toInstant() : Instant.now());

        StringBuilder textBuilder = new StringBuilder();
        StringBuilder attachmentTextBuilder = new StringBuilder();
        List<EmailAttachment> attachments = new ArrayList<>();

        extractMessageContent(m, em, textBuilder, attachmentTextBuilder, attachments);

        String text = textBuilder.toString();
        em.setSnippet(text.length() > 250 ? text.substring(0, 250) : text);

        EmailBody body = new EmailBody();
        body.setEmail(em);
        body.setPlainText(text);
        em.setBody(body);
        em.setAttachments(attachments);

        em = emails.save(em);

        orgs.infer(em).ifPresent(em::setOrganization);
        tagger.suggest(em).forEach(em.getTags()::add);

        // Include text extracted from attachments for deadline detection
        String allTextForAnalysis = text + "\n" + attachmentTextBuilder;
        deadlines.extract(em, allTextForAnalysis);

        emails.save(em);
    }

    private void extractMessageContent(Part part, EmailMessage em,
                                       StringBuilder textBuilder,
                                       StringBuilder attachmentTextBuilder,
                                       List<EmailAttachment> attachments) throws Exception {
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ||
            (part.getFileName() != null && !part.getFileName().isBlank() && !part.isMimeType("multipart/*"))) {
            String filename = part.getFileName() != null ? part.getFileName() : "attachment";
            String contentType = part.getContentType();
            long size = part.getSize() > 0 ? part.getSize() : 0L;

            String extracted = "";
            try {
                extracted = attachmentExtractor.extractText(part.getInputStream(), filename);
                if (!extracted.isBlank()) {
                    attachmentTextBuilder.append("\n--- Attachment: ").append(filename).append(" ---\n");
                    attachmentTextBuilder.append(extracted).append("\n");
                }
            } catch (Exception ex) {
                log.debug("Attachment text extraction skipped for {}: {}", filename, ex.getMessage());
            }

            EmailAttachment att = new EmailAttachment(null, em, filename, contentType, size, extracted);
            attachments.add(att);
            return;
        }

        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String s) {
                textBuilder.append(s).append("\n");
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                extractMessageContent(mp.getBodyPart(i), em, textBuilder, attachmentTextBuilder, attachments);
            }
        }
    }
}
