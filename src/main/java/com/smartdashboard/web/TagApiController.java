package com.smartdashboard.web;

import com.smartdashboard.domain.Tag;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.TagRepository;
import com.smartdashboard.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tags")
public class TagApiController {

    private final TagRepository tags;
    private final UserRepository users;

    public TagApiController(TagRepository tags, UserRepository users) {
        this.tags = tags;
        this.users = users;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        User user = resolveCurrentUser(auth);
        if (user == null) return List.of();
        return tags.findByUser(user).stream()
            .map(t -> Map.<String, Object>of(
                "id", t.getId(),
                "name", t.getName(),
                "color", t.getColor() == null ? "#3b82f6" : t.getColor()
            ))
            .toList();
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication auth, @RequestBody Map<String, String> body) {
        User user = resolveCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tag name is required"));
        }

        if (tags.findByUserAndName(user, name.trim()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tag already exists"));
        }

        Tag t = new Tag();
        t.setUser(user);
        t.setName(name.trim());
        t.setColor(body.getOrDefault("color", "#3b82f6"));
        tags.save(t);
        return ResponseEntity.ok(Map.of("id", t.getId(), "name", t.getName(), "color", t.getColor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(Authentication auth, @PathVariable Long id) {
        User user = resolveCurrentUser(auth);
        Optional<Tag> opt = tags.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Tag t = opt.get();
        if (user != null && !t.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        tags.delete(t);
        return ResponseEntity.ok(Map.of("success", true));
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
