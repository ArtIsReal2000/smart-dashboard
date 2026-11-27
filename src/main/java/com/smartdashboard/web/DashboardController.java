package com.smartdashboard.web;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.*;
import com.smartdashboard.service.sync.SyncOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final UserRepository users;
    private final EmailMessageRepository emails;
    private final TagRepository tags;
    private final OrganizationRepository orgs;
    private final MailAccountRepository accounts;
    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final AesEncryptor encryptor;
    private final SyncOrchestrator syncOrchestrator;

    public DashboardController(UserRepository users, EmailMessageRepository emails,
                               TagRepository tags, OrganizationRepository orgs,
                               MailAccountRepository accounts,
                               OAuth2AuthorizedClientRepository authorizedClients,
                               AesEncryptor encryptor,
                               SyncOrchestrator syncOrchestrator) {
        this.users = users;
        this.emails = emails;
        this.tags = tags;
        this.orgs = orgs;
        this.accounts = accounts;
        this.authorizedClients = authorizedClients;
        this.encryptor = encryptor;
        this.syncOrchestrator = syncOrchestrator;
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        if (error != null) model.addAttribute("error", "Authentication failed. Please check credentials.");
        if (logout != null) model.addAttribute("message", "You have been signed out safely.");
        return "login";
    }

    /**
     * Fast development login button to preview the dashboard immediately
     */
    @GetMapping("/dev-login")
    public String devLogin(HttpServletRequest request) {
        User user = users.findAll().stream().findFirst().orElseGet(() -> {
            User u = new User();
            u.setEmail("demo.user@smartdash.io");
            u.setDisplayName("Alex Mercer");
            return users.save(u);
        });

        OAuth2User oauthUser = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("email", user.getEmail(), "name", user.getDisplayName()),
            "email"
        );

        Authentication auth = new UsernamePasswordAuthenticationToken(
            oauthUser, null, oauthUser.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return "redirect:/dashboard";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Authentication authentication, HttpServletRequest request, Model model) {
        User user = resolveCurrentUser(authentication);
        if (user == null) {
            return "redirect:/login";
        }

        // Auto-heal: If user has no connected accounts yet, check if OAuth client is available in session
        if (accounts.findByUser(user).isEmpty() && authentication != null) {
            try {
                OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient("google", authentication, request);
                if (client != null) {
                    log.info("Auto-healing MailAccount for user {} via active OAuth session", user.getEmail());
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
                    CompletableFuture.runAsync(() -> {
                        try {
                            syncOrchestrator.syncAccount(a);
                        } catch (Exception ex) {
                            log.warn("Auto-sync failed for {}: {}", a.getEmailAddress(), ex.getMessage());
                        }
                    });
                }
            } catch (Exception ex) {
                log.debug("Auto-heal check skipped: {}", ex.getMessage());
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("totalEmails", emails.countByAccount_User_Id(user.getId()));
        model.addAttribute("deadlineEmails", emails.countByAccount_User_IdAndHasDeadlineTrue(user.getId()));
        model.addAttribute("totalTags", tags.countByUser(user));
        model.addAttribute("totalOrgs", orgs.countByUser(user));
        model.addAttribute("accounts", accounts.findByUser(user));
        return "dashboard";
    }

    private User resolveCurrentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return users.findAll().stream().findFirst().orElse(null);
        }

        if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return users.findByEmail(email).orElse(null);
            }
        }
        return users.findAll().stream().findFirst().orElse(null);
    }
}
