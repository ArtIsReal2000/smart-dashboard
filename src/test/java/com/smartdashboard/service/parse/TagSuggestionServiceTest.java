package com.smartdashboard.service.parse;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.Tag;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagSuggestionServiceTest {

    @Mock
    private TagRepository tags;

    private TagSuggestionService service;
    private User testUser;
    private MailAccount testAccount;

    @BeforeEach
    void setUp() {
        service = new TagSuggestionService(tags);
        testUser = new User();
        testUser.setId(1L);
        testAccount = new MailAccount();
        testAccount.setUser(testUser);
    }

    @Test
    void testSuggestFinanceAndUrgentTags() {
        EmailMessage email = new EmailMessage();
        email.setAccount(testAccount);
        email.setSubject("Urgent: Invoice for cloud subscription");
        email.setSnippet("Please review the attached invoice payment receipt.");

        when(tags.findByUserAndName(testUser, "Finance")).thenReturn(Optional.empty());
        when(tags.findByUserAndName(testUser, "Urgent")).thenReturn(Optional.empty());
        when(tags.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Set<Tag> suggested = service.suggest(email);

        assertEquals(2, suggested.size());
        assertTrue(suggested.stream().anyMatch(t -> "Finance".equals(t.getName())));
        assertTrue(suggested.stream().anyMatch(t -> "Urgent".equals(t.getName())));
    }

    @Test
    void testNoKeywordsMatch() {
        EmailMessage email = new EmailMessage();
        email.setAccount(testAccount);
        email.setSubject("Lunch tomorrow?");
        email.setSnippet("Hey, want to grab some tacos around noon?");

        Set<Tag> suggested = service.suggest(email);
        assertTrue(suggested.isEmpty());
        verify(tags, never()).save(any());
    }
}
