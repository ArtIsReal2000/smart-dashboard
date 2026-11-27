package com.smartdashboard.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "emails",
       uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "provider_message_id"}),
       indexes = {
           @Index(name = "idx_received", columnList = "received_date"),
           @Index(name = "idx_org", columnList = "organization_id")
       })
public class EmailMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    private MailAccount account;

    @Column(name = "provider_message_id", nullable = false)
    private String providerMessageId;

    private String subject;
    private String sender;
    private Instant receivedDate;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    private boolean hasDeadline;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "email_tags",
        joinColumns = @JoinColumn(name = "email_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    @OneToOne(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true)
    private EmailBody body;

    @OneToOne(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true)
    private Deadline deadline;

    @OneToMany(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmailAttachment> attachments = new ArrayList<>();

    public EmailMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MailAccount getAccount() { return account; }
    public void setAccount(MailAccount account) { this.account = account; }

    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public Instant getReceivedDate() { return receivedDate; }
    public void setReceivedDate(Instant receivedDate) { this.receivedDate = receivedDate; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public boolean isHasDeadline() { return hasDeadline; }
    public void setHasDeadline(boolean hasDeadline) { this.hasDeadline = hasDeadline; }

    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }

    public EmailBody getBody() { return body; }
    public void setBody(EmailBody body) { this.body = body; }

    public Deadline getDeadline() { return deadline; }
    public void setDeadline(Deadline deadline) { this.deadline = deadline; }

    public List<EmailAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<EmailAttachment> attachments) { this.attachments = attachments; }
}
