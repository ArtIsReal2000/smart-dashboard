package com.smartdashboard.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "organizations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class Organization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    private String domain; // e.g. "acme.com"

    public Organization() {}

    public Organization(Long id, User user, String name, String domain) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.domain = domain;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
