package com.smartdashboard.config;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.MailAccountRepository;
import com.smartdashboard.repository.UserRepository;
import com.smartdashboard.service.sync.SyncOrchestrator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthSuccessHandler.class);

    private final UserRepository users;
    private final MailAccountRepository accounts;
    private final AesEncryptor encryptor;
    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final SyncOrchestrator syncOrchestrator;

    public OAuthSuccessHandler(UserRepository users,
                               MailAccountRepository accounts,
                               AesEncryptor encryptor,
                               OAuth2AuthorizedClientRepository authorizedClients,
                               SyncOrchestrator syncOrchestrator) {
        this.users = users;
        this.accounts = accounts;
        this.encryptor = encryptor;
        this.authorizedClients = authorizedClients;
        this.syncOrchestrator = syncOrchestrator;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                        Authentication auth) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) auth.getPrincipal();
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        String picture = principal.getAttribute("picture");

        User user = users.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setDisplayName(name != null ? name : email);
            u.setAvatarUrl(picture);
            return users.save(u);
        });

        if (picture != null && user.getAvatarUrl() == null) {
            user.setAvatarUrl(picture);
            users.save(user);
        }

        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient("google", auth, req);
        log.info("OAuth success for {}: client={}", email, client != null ? "found" : "null");

        if (client != null) {
            MailAccount account = accounts.findByUserAndEmailAddress(user, email)
                .orElseGet(() -> {
                    MailAccount a = new MailAccount();
                    a.setUser(user);
                    a.setProvider(MailAccount.Provider.GMAIL);
                    a.setEmailAddress(email);
                    return a;
                });

            if (client.getRefreshToken() != null) {
                log.info("Saving encrypted refresh token for {}", email);
                account.setEncryptedRefreshToken(encryptor.encrypt(client.getRefreshToken().getTokenValue()));
            } else {
                log.warn("No refresh token received for {} (consent prompt was not triggered)", email);
            }

            if (client.getAccessToken() != null) {
                log.info("Saving encrypted access token for {}", email);
                account.setEncryptedAccessToken(encryptor.encrypt(client.getAccessToken().getTokenValue()));
                account.setAccessTokenExpiry(client.getAccessToken().getExpiresAt());
            }

            accounts.save(account);

            // Trigger sync in background immediately
            final MailAccount toSync = account;
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting background initial sync for {}", toSync.getEmailAddress());
                    syncOrchestrator.syncAccount(toSync);
                    log.info("Initial sync completed for {}", toSync.getEmailAddress());
                } catch (Exception ex) {
                    log.error("Initial sync error for {}: {}", toSync.getEmailAddress(), ex.getMessage());
                }
            });
        }

        res.sendRedirect("/dashboard");
    }
}
