package com.smartdashboard.web;

import com.smartdashboard.domain.User;
import com.smartdashboard.repository.OrganizationRepository;
import com.smartdashboard.repository.UserRepository;
import com.smartdashboard.service.sync.SyncOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SyncApiController {

    private final SyncOrchestrator orchestrator;
    private final OrganizationRepository orgs;
    private final UserRepository users;

    public SyncApiController(SyncOrchestrator orchestrator, OrganizationRepository orgs, UserRepository users) {
        this.orchestrator = orchestrator;
        this.orgs = orgs;
        this.users = users;
    }

    @PostMapping("/api/sync/now")
    public ResponseEntity<?> syncNow() {
        try {
            orchestrator.syncAll();
            return ResponseEntity.ok(Map.of("success", true, "message", "Sync cycle triggered successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/orgs")
    public List<Map<String, Object>> listOrgs(Authentication auth) {
        User user = resolveCurrentUser(auth);
        if (user == null) return List.of();

        return orgs.findByUser(user).stream().map(o -> Map.<String, Object>of(
            "id", o.getId(),
            "name", o.getName(),
            "domain", o.getDomain()
        )).toList();
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
