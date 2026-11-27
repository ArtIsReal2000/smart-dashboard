package com.smartdashboard.web;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.MailAccountRepository;
import com.smartdashboard.repository.UserRepository;
import com.smartdashboard.service.sync.SyncOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class AccountController {

    private final MailAccountRepository accounts;
    private final UserRepository users;
    private final AesEncryptor encryptor;
    private final SyncOrchestrator syncOrchestrator;

    public AccountController(MailAccountRepository accounts, UserRepository users,
                             AesEncryptor encryptor, SyncOrchestrator syncOrchestrator) {
        this.accounts = accounts;
        this.users = users;
        this.encryptor = encryptor;
        this.syncOrchestrator = syncOrchestrator;
    }

    @GetMapping("/accounts/add-google")
    public String addGoogle() {
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "smart-dashboard", "timestamp", System.currentTimeMillis());
    }

    @GetMapping("/api/accounts")
    @ResponseBody
    public List<Map<String, Object>> listAccounts(Authentication auth) {
        User user = resolveCurrentUser(auth);
        if (user == null) return List.of();

        return accounts.findByUser(user).stream().map(a -> Map.<String, Object>of(
            "id", a.getId(),
            "provider", a.getProvider().name(),
            "emailAddress", a.getEmailAddress(),
            "imapHost", a.getImapHost() != null ? a.getImapHost() : "",
            "lastSyncAt", a.getLastSyncAt() != null ? a.getLastSyncAt().toString() : "Never"
        )).toList();
    }

    @PostMapping("/api/accounts/imap")
    @ResponseBody
    public ResponseEntity<?> addImapAccount(Authentication auth, @RequestBody Map<String, String> body) {
        User user = resolveCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String email = body.get("email");
        String host = body.get("host");
        String portStr = body.get("port");
        String password = body.get("password");

        if (email == null || host == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and Host are required"));
        }

        MailAccount account = accounts.findByUserAndEmailAddress(user, email).orElseGet(() -> {
            MailAccount a = new MailAccount();
            a.setUser(user);
            a.setProvider(MailAccount.Provider.IMAP);
            a.setEmailAddress(email);
            return a;
        });

        account.setImapHost(host);
        account.setImapPort(portStr != null ? Integer.parseInt(portStr) : 993);
        if (password != null && !password.isBlank()) {
            account.setEncryptedAccessToken(encryptor.encrypt(password));
        }

        accounts.save(account);

        // Run initial sync asynchronously
        try {
            syncOrchestrator.syncAccount(account);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("success", true, "id", account.getId(), "email", account.getEmailAddress()));
    }

    @PostMapping("/api/accounts/{id}/sync")
    @ResponseBody
    public ResponseEntity<?> syncSingleAccount(@PathVariable Long id, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<MailAccount> opt = accounts.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        MailAccount a = opt.get();
        if (user != null && !a.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        syncOrchestrator.syncAccount(a);
        return ResponseEntity.ok(Map.of("success", true, "account", a.getEmailAddress()));
    }

    @DeleteMapping("/api/accounts/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteAccount(@PathVariable Long id, Authentication auth) {
        User user = resolveCurrentUser(auth);
        Optional<MailAccount> opt = accounts.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        MailAccount a = opt.get();
        if (user != null && !a.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        accounts.delete(a);
        return ResponseEntity.ok(Map.of("success", true, "message", "Account deleted successfully"));
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
