package com.smartdashboard.web;

import com.smartdashboard.domain.Deadline;
import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.DeadlineRepository;
import com.smartdashboard.repository.EmailMessageRepository;
import com.smartdashboard.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/deadlines")
public class DeadlineApiController {

    private final DeadlineRepository deadlines;
    private final EmailMessageRepository emails;
    private final UserRepository users;

    public DeadlineApiController(DeadlineRepository deadlines, EmailMessageRepository emails, UserRepository users) {
        this.deadlines = deadlines;
        this.emails = emails;
        this.users = users;
    }

    @GetMapping
    public List<Map<String, Object>> listDeadlines(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "false") Boolean includeResolved) {
        User user = resolveCurrentUser(auth);
        if (user == null) return List.of();

        List<Deadline> list = Boolean.TRUE.equals(includeResolved)
            ? deadlines.findByEmail_Account_User_IdOrderByDueDateAsc(user.getId())
            : deadlines.findByEmail_Account_User_IdAndResolvedFalseOrderByDueDateAsc(user.getId());

        return list.stream().map(this::toMap).toList();
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveDeadline(@PathVariable Long id, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<Deadline> opt = deadlines.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Deadline d = opt.get();
        if (user != null && !d.getEmail().getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        d.setResolved(true);
        deadlines.save(d);
        return ResponseEntity.ok(Map.of("success", true, "id", d.getId(), "resolved", true));
    }

    @PostMapping("/{id}/unresolve")
    public ResponseEntity<?> unresolveDeadline(@PathVariable Long id, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<Deadline> opt = deadlines.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Deadline d = opt.get();
        if (user != null && !d.getEmail().getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        d.setResolved(false);
        deadlines.save(d);
        return ResponseEntity.ok(Map.of("success", true, "id", d.getId(), "resolved", false));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDeadline(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<Deadline> opt = deadlines.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Deadline d = opt.get();
        if (user != null && !d.getEmail().getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        if (body.containsKey("dueDate")) {
            d.setDueDate(LocalDateTime.parse((String) body.get("dueDate")));
        }
        if (body.containsKey("description")) {
            d.setDescription((String) body.get("description"));
        }
        d.setManual(true);
        d.setConfidenceScore(1.0);
        deadlines.save(d);

        return ResponseEntity.ok(toMap(d));
    }

    @PostMapping("/email/{emailId}")
    public ResponseEntity<?> createManualDeadline(
            @PathVariable Long emailId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<EmailMessage> optEmail = emails.findById(emailId);
        if (optEmail.isEmpty()) return ResponseEntity.notFound().build();

        EmailMessage email = optEmail.get();
        if (user != null && !email.getAccount().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        String dueDateStr = (String) body.get("dueDate");
        if (dueDateStr == null || dueDateStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "dueDate is required"));
        }

        Deadline dl = email.getDeadline();
        if (dl == null) {
            dl = new Deadline();
            dl.setEmail(email);
        }

        dl.setDueDate(LocalDateTime.parse(dueDateStr));
        dl.setDescription((String) body.getOrDefault("description", "Manually assigned deadline"));
        dl.setSourceText((String) body.getOrDefault("sourceText", "Manual"));
        dl.setConfidenceScore(1.0);
        dl.setManual(true);
        dl.setResolved(false);

        deadlines.save(dl);
        email.setHasDeadline(true);
        email.setDeadline(dl);
        emails.save(email);

        return ResponseEntity.ok(toMap(dl));
    }

    private Map<String, Object> toMap(Deadline d) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", d.getId());
        map.put("emailId", d.getEmail().getId());
        map.put("emailSubject", d.getEmail().getSubject());
        map.put("emailSender", d.getEmail().getSender());
        map.put("dueDate", d.getDueDate() != null ? d.getDueDate().toString() : null);
        map.put("description", d.getDescription());
        map.put("sourceText", d.getSourceText());
        map.put("confidenceScore", d.getConfidenceScore());
        map.put("manual", d.isManual());
        map.put("resolved", d.isResolved());
        return map;
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
