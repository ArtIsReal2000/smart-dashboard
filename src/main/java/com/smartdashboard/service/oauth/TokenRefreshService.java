package com.smartdashboard.service.oauth;

import com.google.auth.oauth2.UserCredentials;
import com.smartdashboard.crypto.AesEncryptor;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.repository.MailAccountRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenRefreshService {

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
        if (account.getEncryptedRefreshToken() == null) {
            if (account.getEncryptedAccessToken() != null) {
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
            creds.refreshIfExpired();
            String token = creds.getAccessToken().getTokenValue();
            account.setEncryptedAccessToken(encryptor.encrypt(token));
            if (creds.getAccessToken().getExpirationTime() != null) {
                account.setAccessTokenExpiry(creds.getAccessToken().getExpirationTime().toInstant());
            }
            accounts.save(account);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("Token refresh failed for " + account.getEmailAddress(), e);
        }
    }
}
