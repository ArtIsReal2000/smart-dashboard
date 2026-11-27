package com.smartdashboard.domain;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "tags",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class Tag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    private String color; // hex, e.g. "#2563eb"

    public Tag() {}

    public Tag(Long id, User user, String name, String color) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.color = color;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        if (id != null && tag.id != null) return Objects.equals(id, tag.id);
        return Objects.equals(name != null ? name.toLowerCase() : null,
                              tag.name != null ? tag.name.toLowerCase() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : null);
    }
}
