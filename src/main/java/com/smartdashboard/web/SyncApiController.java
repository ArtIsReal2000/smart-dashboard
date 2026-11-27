package com.smartdashboard.web;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.MailAccountRepository;
import com.smartdashboard.repository.OrganizationRepository;
import com.smartdashboard.repository.UserRepository;
import com.smartdashboard.service.sync.SyncOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SyncApiController {

    private static final Logger log = LoggerFactory.getLogger(SyncApiController.class);

    private final SyncOrchestrator orchestrator;
    private final OrganizationRepository orgs;
    private final UserRepository users;
    private final MailAccountRepository accounts;
    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final AesEncryptor encryptor;

    public SyncApiController(SyncOrchestrator orchestrator,
                             OrganizationRepository orgs,
                             UserRepository users,
                             MailAccountRepository accounts,
                             OAuth2AuthorizedClientRepository authorizedClients,
                             AesEncryptor encryptor) {
        this.orchestrator = orchestrator;
        this.orgs = orgs;
        this.users = users;
        this.accounts = accounts;
        this.authorizedClients = authorizedClients;
        this.encryptor = encryptor;
    }

    @PostMapping("/api/sync/now")
    public ResponseEntity<?> syncNow(Authentication auth, HttpServletRequest req) {
        try {
            User user = resolveCurrentUser(auth);
            if (user != null) {
                List<MailAccount> userAccounts = accounts.findByUser(user);
                // Attempt auto-heal if no mail account exists
                if (userAccounts.isEmpty() && auth != null) {
                    try {
                        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient("google", auth, req);
                        if (client != null) {
                            MailAccount a = new MailAccount();
                            a.setUser(user);
                            a.setProvider(MailAccount.Provider.GMAIL);
                            a.setEmailAddress(user.getEmail());
                            if (client.getRefreshToken() != null) {
                                a.setEncryptedRefreshToken(encryptor.encrypt(client.getRefreshToken().getTokenValue()));
                            }
                            if (client.getAccessToken() != null) {
                                a.setEncryptedAccessToken(encryptor.encrypt(client.getAccessToken().getTokenValue()));
                                a.setAccessTokenExpiry(client.getAccessToken().getExpiresAt());
                            }
                            accounts.save(a);
                            userAccounts = List.of(a);
                            log.info("Auto-healed mail account for {} in /api/sync/now", user.getEmail());
                        }
                    } catch (Exception ex) {
                        log.warn("Auto-heal failed in syncNow: {}", ex.getMessage());
                    }
                }

                if (userAccounts.isEmpty()) {
                    return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "No connected mail accounts found. Please click 'Add Account' -> 'Connect Gmail' to link your inbox."
                    ));
                }

                for (MailAccount a : userAccounts) {
                    orchestrator.syncAccount(a);
                }
            } else {
                orchestrator.syncAll();
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Sync completed successfully"));
        } catch (Exception e) {
            log.error("Manual sync failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Sync failed"));
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
