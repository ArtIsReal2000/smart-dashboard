package com.smartdashboard.service.parse;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.Organization;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationInferenceServiceTest {

    @Mock
    private OrganizationRepository orgs;

    private OrganizationInferenceService service;
    private User testUser;
    private MailAccount testAccount;

    @BeforeEach
    void setUp() {
        service = new OrganizationInferenceService(orgs);
        testUser = new User();
        testUser.setId(1L);
        testAccount = new MailAccount();
        testAccount.setUser(testUser);
    }

    @Test
    void testInferCorporateDomain() {
        EmailMessage email = new EmailMessage();
        email.setAccount(testAccount);
        email.setSender("billing@stripe.com");

        when(orgs.findByUserAndDomain(testUser, "stripe.com")).thenReturn(Optional.empty());
        when(orgs.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Organization> result = service.infer(email);

        assertTrue(result.isPresent());
        assertEquals("Stripe", result.get().getName());
        assertEquals("stripe.com", result.get().getDomain());
    }

    @Test
    void testFilterOutPublicWebmail() {
        EmailMessage email = new EmailMessage();
        email.setAccount(testAccount);
        email.setSender("john.doe@gmail.com");

        Optional<Organization> result = service.infer(email);
        assertTrue(result.isEmpty());
        verify(orgs, never()).save(any());

        email.setSender("user@yahoo.com");
        assertTrue(service.infer(email).isEmpty());

        email.setSender("user@outlook.com");
        assertTrue(service.infer(email).isEmpty());

        email.setSender("user@proton.me");
        assertTrue(service.infer(email).isEmpty());
    }

    @Test
    void testNullSender() {
        EmailMessage email = new EmailMessage();
        email.setAccount(testAccount);
        email.setSender(null);

        Optional<Organization> result = service.infer(email);
        assertTrue(result.isEmpty());
    }
}
