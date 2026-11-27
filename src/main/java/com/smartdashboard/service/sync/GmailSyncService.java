package com.smartdashboard.service.sync;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.*;
import com.smartdashboard.repository.*;
import com.smartdashboard.service.oauth.TokenRefreshService;
import com.smartdashboard.service.parse.AttachmentTextExtractorService;
import com.smartdashboard.service.parse.DeadlineParserService;
import com.smartdashboard.service.parse.OrganizationInferenceService;
import com.smartdashboard.service.parse.TagSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class GmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);

    private final TokenRefreshService tokens;
    private final AesEncryptor encryptor;
    private final EmailMessageRepository emails;
    private final DeadlineParserService deadlines;
    private final OrganizationInferenceService orgs;
    private final TagSuggestionService tagger;
    private final AttachmentTextExtractorService attachmentExtractor;

    public GmailSyncService(TokenRefreshService tokens, AesEncryptor encryptor,
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

    @Transactional
    public void sync(MailAccount account) {
        try {
            log.info("Starting Gmail sync for account: {}", account.getEmailAddress());
            String accessToken = tokens.getValidAccessToken(account);
            Gmail gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(GoogleCredentials.create(
                        new AccessToken(accessToken, null))))
                .setApplicationName("SmartDashboard").build();

            String query = account.getLastSyncAt() == null
                ? "in:inbox"
                : "in:inbox after:" + account.getLastSyncAt().getEpochSecond();

            log.info("Querying Gmail messages for {} with filter: {}", account.getEmailAddress(), query);
            ListMessagesResponse list = gmail.users().messages()
                .list("me").setQ(query).setMaxResults(50L).execute();

            if (list.getMessages() == null || list.getMessages().isEmpty()) {
                log.info("No messages found for {} matching query", account.getEmailAddress());
                account.setLastSyncAt(Instant.now());
                return;
            }

            log.info("Found {} messages to sync for {}", list.getMessages().size(), account.getEmailAddress());
            int syncedCount = 0;
            for (Message ref : list.getMessages()) {
                try {
                    if (emails.existsByAccountAndProviderMessageId(account, ref.getId())) {
                        continue;
                    }
                    Message full = gmail.users().messages().get("me", ref.getId())
                        .setFormat("full").execute();
                    persist(account, full, gmail);
                    syncedCount++;
                } catch (Exception msgEx) {
                    log.warn("Skipping message {} for {}: {}", ref.getId(), account.getEmailAddress(), msgEx.getMessage());
                }
            }
            account.setLastSyncAt(Instant.now());
            log.info("Gmail sync finished for {}: successfully imported {} new emails", account.getEmailAddress(), syncedCount);
        } catch (Exception e) {
            log.error("Gmail sync failed for {}: {}", account.getEmailAddress(), e.getMessage());
            throw new RuntimeException("Gmail sync failed for " + account.getEmailAddress(), e);
        }
    }

    private void persist(MailAccount account, Message msg, Gmail gmail) {
        EmailMessage em = new EmailMessage();
        em.setAccount(account);
        em.setProviderMessageId(msg.getId());
        em.setSnippet(msg.getSnippet());
        em.setReceivedDate(Instant.ofEpochMilli(msg.getInternalDate()));

        if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
            for (MessagePartHeader h : msg.getPayload().getHeaders()) {
                switch (h.getName().toLowerCase()) {
                    case "subject" -> em.setSubject(h.getValue());
                    case "from"    -> em.setSender(h.getValue());
                }
            }
        }

        String plain = extractPlainText(msg);
        String html = extractHtml(msg);

        EmailBody body = new EmailBody();
        body.setEmail(em);
        body.setPlainText(plain);
        body.setHtmlBody(html);
        em.setBody(body);

        // Process attachments and extract their text via Tika
        StringBuilder attachmentText = new StringBuilder();
        List<EmailAttachment> attachments = processAttachments(msg, em, gmail, attachmentText);
        em.setAttachments(attachments);

        em = emails.save(em);

        orgs.infer(em).ifPresent(em::setOrganization);
        tagger.suggest(em).forEach(em.getTags()::add);

        // Mine deadlines from both plain text and attachments
        String combinedAnalysisText = plain + "\n" + attachmentText;
        deadlines.extract(em, combinedAnalysisText);

        emails.save(em);
    }

    private List<EmailAttachment> processAttachments(Message msg, EmailMessage em, Gmail gmail, StringBuilder attachmentTextCollector) {
        List<EmailAttachment> list = new ArrayList<>();
        if (msg.getPayload() == null || msg.getPayload().getParts() == null) return list;

        for (MessagePart p : msg.getPayload().getParts()) {
            if (p.getFilename() != null && !p.getFilename().isBlank()) {
                String filename = p.getFilename();
                String contentType = p.getMimeType();
                long size = p.getBody() != null && p.getBody().getSize() != null ? p.getBody().getSize() : 0L;
                String extracted = "";

                try {
                    byte[] data = null;
                    if (p.getBody() != null && p.getBody().getData() != null) {
                        data = Base64.getUrlDecoder().decode(p.getBody().getData());
                    } else if (p.getBody() != null && p.getBody().getAttachmentId() != null) {
                        var att = gmail.users().messages().attachments()
                            .get("me", msg.getId(), p.getBody().getAttachmentId()).execute();
                        if (att.getData() != null) {
                            data = Base64.getUrlDecoder().decode(att.getData());
                        }
                    }

                    if (data != null && data.length > 0) {
                        extracted = attachmentExtractor.extractText(data, filename);
                        if (!extracted.isBlank()) {
                            attachmentTextCollector.append("\n--- Attachment: ").append(filename).append(" ---\n");
                            attachmentTextCollector.append(extracted).append("\n");
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Attachment extraction skipped for {}: {}", filename, ex.getMessage());
                }

                EmailAttachment attachment = new EmailAttachment(null, em, filename, contentType, size, extracted);
                list.add(attachment);
            }
        }
        return list;
    }

    private String extractPlainText(Message msg) {
        try {
            MessagePart part = msg.getPayload();
            if (part == null) return msg.getSnippet() != null ? msg.getSnippet() : "";

            if (part.getBody() != null && part.getBody().getData() != null) {
                return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            }
            if (part.getParts() != null) {
                for (MessagePart p : part.getParts()) {
                    if ("text/plain".equalsIgnoreCase(p.getMimeType()) && p.getBody() != null && p.getBody().getData() != null) {
                        return new String(Base64.getUrlDecoder().decode(p.getBody().getData()));
                    }
                }
            }
        } catch (Exception ignored) {}
        return msg.getSnippet() != null ? msg.getSnippet() : "";
    }

    private String extractHtml(Message msg) {
        try {
            MessagePart part = msg.getPayload();
            if (part == null) return null;

            if ("text/html".equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
                return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            }
            if (part.getParts() != null) {
                for (MessagePart p : part.getParts()) {
                    if ("text/html".equalsIgnoreCase(p.getMimeType()) && p.getBody() != null && p.getBody().getData() != null) {
                        return new String(Base64.getUrlDecoder().decode(p.getBody().getData()));
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
