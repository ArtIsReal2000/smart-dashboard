package com.smartdashboard.config;

import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.MailAccountRepository;
import com.smartdashboard.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository users;
    private final MailAccountRepository accounts;
    private final AesEncryptor encryptor;
    private final OAuth2AuthorizedClientRepository authorizedClients;

    public OAuthSuccessHandler(UserRepository users,
                               MailAccountRepository accounts,
                               AesEncryptor encryptor,
                               OAuth2AuthorizedClientRepository authorizedClients) {
        this.users = users;
        this.accounts = accounts;
        this.encryptor = encryptor;
        this.authorizedClients = authorizedClients;
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

        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                "google", auth, req);
        if (client != null && client.getRefreshToken() != null) {
            MailAccount account = accounts.findByUserAndEmailAddress(user, email)
                .orElseGet(() -> {
                    MailAccount a = new MailAccount();
                    a.setUser(user);
                    a.setProvider(MailAccount.Provider.GMAIL);
                    a.setEmailAddress(email);
                    return a;
                });
            account.setEncryptedRefreshToken(encryptor.encrypt(client.getRefreshToken().getTokenValue()));
            if (client.getAccessToken() != null) {
                account.setEncryptedAccessToken(encryptor.encrypt(client.getAccessToken().getTokenValue()));
                account.setAccessTokenExpiry(client.getAccessToken().getExpiresAt());
            }
            accounts.save(account);
        }

        res.sendRedirect("/dashboard");
    }
}
