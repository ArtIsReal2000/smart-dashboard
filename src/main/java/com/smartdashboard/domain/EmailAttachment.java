package com.smartdashboard.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "email_attachments")
public class EmailAttachment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "email_id")
    private EmailMessage email;

    @Column(nullable = false)
    private String filename;

    private String contentType;

    private Long sizeBytes;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    public EmailAttachment() {}

    public EmailAttachment(Long id, EmailMessage email, String filename, String contentType, Long sizeBytes, String extractedText) {
        this.id = id;
        this.email = email;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.extractedText = extractedText;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmailMessage getEmail() { return email; }
    public void setEmail(EmailMessage email) { this.email = email; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
}
