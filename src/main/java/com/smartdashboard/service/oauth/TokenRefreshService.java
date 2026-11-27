package com.smartdashboard.service.oauth;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.repository.MailAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);

    private final ClientRegistrationRepository clients;
    private final MailAccountRepository accounts;
    private final AesEncryptor encryptor;

    public TokenRefreshService(ClientRegistrationRepository clients,
                               MailAccountRepository accounts,
                               AesEncryptor encryptor) {
        this.clients = clients;
        this.accounts = accounts;
        this.encryptor = encryptor;
    }

    public String getValidAccessToken(MailAccount account) {
        // If we already have a valid access token that expires at least 2 minutes from now, reuse it directly
        if (account.getEncryptedAccessToken() != null && account.getAccessTokenExpiry() != null) {
            if (account.getAccessTokenExpiry().isAfter(Instant.now().plusSeconds(120))) {
                log.debug("Reusing existing valid access token for {}", account.getEmailAddress());
                return encryptor.decrypt(account.getEncryptedAccessToken());
            }
        }

        // If no refresh token is stored, return the access token if available
        if (account.getEncryptedRefreshToken() == null) {
            if (account.getEncryptedAccessToken() != null) {
                log.warn("No refresh token stored for {}; using access token directly", account.getEmailAddress());
                return encryptor.decrypt(account.getEncryptedAccessToken());
            }
            throw new IllegalStateException("No refresh token or access token available for account: " + account.getEmailAddress());
        }

        try {
            ClientRegistration reg = clients.findByRegistrationId("google");
            UserCredentials creds = UserCredentials.newBuilder()
                .setClientId(reg.getClientId())
                .setClientSecret(reg.getClientSecret())
                .setRefreshToken(encryptor.decrypt(account.getEncryptedRefreshToken()))
                .build();

            AccessToken at = creds.refreshAccessToken();
            String token = at.getTokenValue();
            account.setEncryptedAccessToken(encryptor.encrypt(token));
            if (at.getExpirationTime() != null) {
                account.setAccessTokenExpiry(at.getExpirationTime().toInstant());
            }
            accounts.save(account);
            log.info("Refreshed access token successfully for {}", account.getEmailAddress());
            return token;
        } catch (Exception e) {
            log.error("Token refresh failed for {}: {}", account.getEmailAddress(), e.getMessage());
            // Fallback to existing access token if present
            if (account.getEncryptedAccessToken() != null) {
                log.warn("Falling back to existing access token for {}", account.getEmailAddress());
                return encryptor.decrypt(account.getEncryptedAccessToken());
            }
            throw new RuntimeException("Token refresh failed for " + account.getEmailAddress(), e);
        }
    }
}
