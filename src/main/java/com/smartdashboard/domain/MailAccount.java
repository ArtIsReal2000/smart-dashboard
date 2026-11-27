package com.smartdashboard.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mail_accounts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "email_address"}))
public class MailAccount {

    public enum Provider { GMAIL, IMAP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Column(columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(columnDefinition = "TEXT")
    private String encryptedAccessToken;

    private Instant accessTokenExpiry;

    private String imapHost;
    private Integer imapPort;

    private Instant lastSyncAt;

    public MailAccount() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public void setEncryptedRefreshToken(String encryptedRefreshToken) { this.encryptedRefreshToken = encryptedRefreshToken; }

    public String getEncryptedAccessToken() { return encryptedAccessToken; }
    public void setEncryptedAccessToken(String encryptedAccessToken) { this.encryptedAccessToken = encryptedAccessToken; }

    public Instant getAccessTokenExpiry() { return accessTokenExpiry; }
    public void setAccessTokenExpiry(Instant accessTokenExpiry) { this.accessTokenExpiry = accessTokenExpiry; }

    public String getImapHost() { return imapHost; }
    public void setImapHost(String imapHost) { this.imapHost = imapHost; }

    public Integer getImapPort() { return imapPort; }
    public void setImapPort(Integer imapPort) { this.imapPort = imapPort; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}
