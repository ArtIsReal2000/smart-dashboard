package com.smartdashboard.web;

import com.smartdashboard.domain.EmailBody;
import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.Tag;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.EmailMessageRepository;
import com.smartdashboard.repository.TagRepository;
import com.smartdashboard.repository.UserRepository;
import com.smartdashboard.service.EmailQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/emails")
public class EmailApiController {

    private final EmailQueryService query;
    private final UserRepository users;
    private final EmailMessageRepository emails;
    private final TagRepository tags;

    public EmailApiController(EmailQueryService query, UserRepository users,
                              EmailMessageRepository emails,
                              TagRepository tags) {
        this.query = query;
        this.users = users;
        this.emails = emails;
        this.tags = tags;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            Authentication auth,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Boolean hasDeadline,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "date") String sortBy) {

        User user = resolveCurrentUser(auth);
        if (user == null) return List.of();

        List<EmailMessage> results = query.search(
            user.getId(), tagId, orgId, hasDeadline, q,
            from != null ? Instant.parse(from) : null,
            to != null ? Instant.parse(to) : null,
            sortBy
        );

        return results.stream().map(this::toMap).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable Long id, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<EmailMessage> opt = emails.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        EmailMessage e = opt.get();
        if (user != null && !e.getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        Map<String, Object> map = new HashMap<>(toMap(e));
        EmailBody body = e.getBody();
        if (body != null) {
            map.put("plainText", body.getPlainText());
            map.put("htmlBody", body.getHtmlBody());
        } else {
            map.put("plainText", e.getSnippet());
            map.put("htmlBody", null);
        }

        map.put("attachments", e.getAttachments().stream().map(a -> Map.<String, Object>of(
            "id", a.getId(),
            "filename", a.getFilename(),
            "contentType", a.getContentType() != null ? a.getContentType() : "",
            "sizeBytes", a.getSizeBytes() != null ? a.getSizeBytes() : 0L,
            "hasExtractedText", a.getExtractedText() != null && !a.getExtractedText().isBlank()
        )).toList());

        return ResponseEntity.ok(map);
    }

    @PostMapping("/{id}/tags/{tagId}")
    public ResponseEntity<?> addTag(@PathVariable Long id, @PathVariable Long tagId, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<EmailMessage> optEmail = emails.findById(id);
        Optional<Tag> optTag = tags.findById(tagId);

        if (optEmail.isEmpty() || optTag.isEmpty()) return ResponseEntity.notFound().build();
        EmailMessage e = optEmail.get();
        Tag t = optTag.get();

        if (user != null && !e.getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        e.getTags().add(t);
        emails.save(e);
        return ResponseEntity.ok(Map.of("success", true, "tags", e.getTags().stream().map(Tag::getName).toList()));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ResponseEntity<?> removeTag(@PathVariable Long id, @PathVariable Long tagId, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<EmailMessage> optEmail = emails.findById(id);
        Optional<Tag> optTag = tags.findById(tagId);

        if (optEmail.isEmpty() || optTag.isEmpty()) return ResponseEntity.notFound().build();
        EmailMessage e = optEmail.get();
        Tag t = optTag.get();

        if (user != null && !e.getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        e.getTags().remove(t);
        emails.save(e);
        return ResponseEntity.ok(Map.of("success", true, "tags", e.getTags().stream().map(Tag::getName).toList()));
    }

    private Map<String, Object> toMap(EmailMessage e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("subject", e.getSubject() == null ? "(No subject)" : e.getSubject());
        m.put("sender", e.getSender() == null ? "Unknown" : e.getSender());
        m.put("account", e.getAccount().getEmailAddress());
        m.put("accountProvider", e.getAccount().getProvider().name());
        m.put("receivedDate", e.getReceivedDate() == null ? "" : e.getReceivedDate().toString());
        m.put("hasDeadline", e.isHasDeadline());
        m.put("deadlineId", e.getDeadline() == null ? null : e.getDeadline().getId());
        m.put("deadline", e.getDeadline() == null ? null : e.getDeadline().getDueDate().toString());
        m.put("deadlineText", e.getDeadline() == null ? null : e.getDeadline().getSourceText());
        m.put("deadlineDescription", e.getDeadline() == null ? null : e.getDeadline().getDescription());
        m.put("deadlineConfidence", e.getDeadline() == null ? 0.0 : e.getDeadline().getConfidenceScore());
        m.put("deadlineResolved", e.getDeadline() != null && e.getDeadline().isResolved());
        m.put("attachmentCount", e.getAttachments() != null ? e.getAttachments().size() : 0);
        m.put("organization", e.getOrganization() == null ? null : e.getOrganization().getName());
        m.put("organizationDomain", e.getOrganization() == null ? null : e.getOrganization().getDomain());
        m.put("tags", e.getTags().stream().map(t -> Map.of(
            "id", t.getId(),
            "name", t.getName(),
            "color", t.getColor() == null ? "#3b82f6" : t.getColor()
        )).toList());
        m.put("snippet", e.getSnippet() == null ? "" : e.getSnippet());
        return m;
    }

    private User resolveCurrentUser(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return users.findByEmail(email).orElse(null);
            }
        }
        return users.findAll().stream().findFirst().orElse(null);
    }
}
