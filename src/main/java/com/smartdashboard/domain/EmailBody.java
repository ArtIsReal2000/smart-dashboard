package com.smartdashboard.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "email_bodies")
public class EmailBody {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "email_id")
    private EmailMessage email;

    @Column(columnDefinition = "TEXT")
    private String htmlBody;

    @Column(columnDefinition = "TEXT")
    private String plainText;

    public EmailBody() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmailMessage getEmail() { return email; }
    public void setEmail(EmailMessage email) { this.email = email; }

    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }

    public String getPlainText() { return plainText; }
    public void setPlainText(String plainText) { this.plainText = plainText; }
}
