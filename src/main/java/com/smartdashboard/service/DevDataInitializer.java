package com.smartdashboard.service;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.*;
import com.smartdashboard.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final UserRepository users;
    private final MailAccountRepository accounts;
    private final EmailMessageRepository emails;
    private final TagRepository tags;
    private final OrganizationRepository orgs;
    private final DeadlineRepository deadlines;
    private final AesEncryptor encryptor;

    public DevDataInitializer(UserRepository users, MailAccountRepository accounts,
                              EmailMessageRepository emails,
                              TagRepository tags, OrganizationRepository orgs,
                              DeadlineRepository deadlines, AesEncryptor encryptor) {
        this.users = users;
        this.accounts = accounts;
        this.emails = emails;
        this.tags = tags;
        this.orgs = orgs;
        this.deadlines = deadlines;
        this.encryptor = encryptor;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;

        log.info("Seeding Dev Demo Data for Smart Dashboard...");

        User user = new User();
        user.setEmail("demo.user@smartdash.io");
        user.setDisplayName("Alex Mercer");
        user.setAvatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&auto=format&fit=crop&q=80");
        user = users.save(user);

        MailAccount gmail = new MailAccount();
        gmail.setUser(user);
        gmail.setProvider(MailAccount.Provider.GMAIL);
        gmail.setEmailAddress("alex.work@gmail.com");
        gmail.setLastSyncAt(Instant.now());
        gmail.setEncryptedAccessToken(encryptor.encrypt("mock-access-token-gmail"));
        gmail.setEncryptedRefreshToken(encryptor.encrypt("mock-refresh-token-gmail"));
        gmail = accounts.save(gmail);

        MailAccount imap = new MailAccount();
        imap.setUser(user);
        imap.setProvider(MailAccount.Provider.IMAP);
        imap.setEmailAddress("alex@acmecorp.internal");
        imap.setImapHost("imap.acmecorp.internal");
        imap.setImapPort(993);
        imap.setLastSyncAt(Instant.now());
        imap = accounts.save(imap);

        Tag tagUrgent = tags.save(new Tag(null, user, "Urgent", "#ef4444"));
        Tag tagFinance = tags.save(new Tag(null, user, "Finance", "#10b981"));
        Tag tagMeetings = tags.save(new Tag(null, user, "Meetings", "#8b5cf6"));
        Tag tagEngineering = tags.save(new Tag(null, user, "Engineering", "#3b82f6"));
        Tag tagNewsletter = tags.save(new Tag(null, user, "Newsletter", "#f59e0b"));

        Organization orgStripe = orgs.save(new Organization(null, user, "Stripe", "stripe.com"));
        Organization orgAws = orgs.save(new Organization(null, user, "Amazon Web Services", "aws.amazon.com"));
        Organization orgGithub = orgs.save(new Organization(null, user, "GitHub", "github.com"));
        Organization orgLinear = orgs.save(new Organization(null, user, "Linear", "linear.app"));

        createEmail(gmail, "msg-001",
            "Urgent: Action Required — Q3 Cloud Infrastructure Invoice #48291",
            "billing@aws.amazon.com",
            Instant.now().minus(2, ChronoUnit.HOURS),
            "Your monthly consolidated statement for AWS infrastructure is ready. Payment due by tomorrow 5:00 PM to avoid service interruption.",
            "Please review your monthly statement for cloud computing instances, S3 storage, and database read replicas. The total amount due is $4,290.50. Payment must be submitted no later than tomorrow at 5:00 PM UTC.",
            orgAws,
            Set.of(tagFinance, tagUrgent),
            LocalDateTime.now().plusHours(26),
            "Payment due by tomorrow 5:00 PM",
            "Invoice #48291 must be paid by tomorrow at 5:00 PM to avoid disruption.",
            false,
            List.of(new EmailAttachment(null, null, "invoice_48291.pdf", "application/pdf", 142000L, "Invoice #48291 Total: $4,290.50 Due Date: Tomorrow 5 PM"))
        );

        createEmail(gmail, "msg-002",
            "Sprint Planning & Q4 Product Roadmap Review",
            "sarah.chen@linear.app",
            Instant.now().minus(5, ChronoUnit.HOURS),
            "Hey team, please RSVP by Friday 10:00 AM for the upcoming quarterly roadmap sync. We will finalize engineering capacity.",
            "Hey team,\n\nWe are gearing up for the Q4 sprint kick-off. Please review the attached draft tickets and RSVP by Friday at 10:00 AM so we can lock in calendar invites and room bookings.\n\nBest,\nSarah",
            orgLinear,
            Set.of(tagMeetings, tagEngineering),
            LocalDateTime.now().plusDays(2).withHour(10).withMinute(0),
            "RSVP by Friday 10:00 AM",
            "Please RSVP by Friday at 10:00 AM so we can lock in calendar invites.",
            false,
            List.of()
        );

        createEmail(imap, "msg-003",
            "Security Advisory: Critical patch release for OAuth2 token gateway",
            "security-alerts@github.com",
            Instant.now().minus(1, ChronoUnit.DAYS),
            "A high-priority vulnerability was remediated in upstream dependency tree. Submit verification report before November 25.",
            "Security bulletin GHSA-2026-8812:\nAll backend services operating OAuth2 token relays must verify dependency versions. Please run test suites and submit verification before November 25, 2026.",
            orgGithub,
            Set.of(tagUrgent, tagEngineering),
            LocalDateTime.now().plusDays(5).withHour(17).withMinute(0),
            "Submit verification report before November 25",
            "Submit verification report before November 25, 2026.",
            false,
            List.of(new EmailAttachment(null, null, "security_patch_v2.patch", "text/x-diff", 8940L, "CVE-2026-8812 remediation patch diff"))
        );

        createEmail(gmail, "msg-004",
            "Receipt for your subscription to Stripe Atlas",
            "notifications@stripe.com",
            Instant.now().minus(2, ChronoUnit.DAYS),
            "Thank you for your payment of $500.00. Your transaction receipt is attached.",
            "Stripe receipt #1092-2026. This confirms receipt of payment. No further action is required.",
            orgStripe,
            Set.of(tagFinance),
            null,
            null,
            null,
            false,
            List.of()
        );

        createEmail(gmail, "msg-005",
            "The Architecture Gazette — Issue #142: Zero Trust & AI Agents",
            "editor@architecture-digest.io",
            Instant.now().minus(3, ChronoUnit.DAYS),
            "In this week's issue: orchestrating autonomous multi-agent swarms, resilient token security, and reactive data pipelines.",
            "Welcome to Issue #142! Discover how leading engineering organizations design resilient AI agent backends with OAuth offline tokens and event-driven architectures.",
            null,
            Set.of(tagNewsletter),
            null,
            null,
            null,
            false,
            List.of()
        );

        log.info("Demo data created successfully (5 emails, 4 organizations, 5 tags, 3 deadlines, 2 attachments).");
    }

    private void createEmail(MailAccount account, String pid, String subject, String sender,
                             Instant received, String snippet, String plainText,
                             Organization org, Set<Tag> emailTags,
                             LocalDateTime dueDate, String sourceText, String description,
                             boolean resolved, List<EmailAttachment> attachments) {
        EmailMessage em = new EmailMessage();
        em.setAccount(account);
        em.setProviderMessageId(pid);
        em.setSubject(subject);
        em.setSender(sender);
        em.setReceivedDate(received);
        em.setSnippet(snippet);
        em.setOrganization(org);
        em.getTags().addAll(emailTags);

        EmailBody body = new EmailBody();
        body.setEmail(em);
        body.setPlainText(plainText);
        em.setBody(body);

        List<EmailAttachment> attList = new ArrayList<>();
        if (attachments != null) {
            for (EmailAttachment a : attachments) {
                a.setEmail(em);
                attList.add(a);
            }
        }
        em.setAttachments(attList);

        em = emails.save(em);

        if (dueDate != null) {
            Deadline dl = new Deadline();
            dl.setEmail(em);
            dl.setDueDate(dueDate);
            dl.setSourceText(sourceText);
            dl.setDescription(description != null ? description : "Action deadline detected from message content");
            dl.setConfidenceScore(0.85);
            dl.setManual(false);
            dl.setResolved(resolved);
            deadlines.save(dl);
            em.setHasDeadline(true);
            em.setDeadline(dl);
            emails.save(em);
        }
    }
}
