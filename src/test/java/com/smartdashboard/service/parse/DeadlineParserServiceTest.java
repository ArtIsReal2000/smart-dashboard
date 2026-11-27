package com.smartdashboard.service.parse;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.repository.DeadlineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadlineParserServiceTest {

    @Mock
    private DeadlineRepository deadlines;

    private DeadlineParserService service;

    @BeforeEach
    void setUp() {
        service = new DeadlineParserService(deadlines);
    }

    @Test
    void testExtractDeadlineFromHighIntentText() {
        EmailMessage email = new EmailMessage();
        String text = "Important: The project proposal has a strict deadline due tomorrow at 5pm. Please submit on time.";

        service.extract(email, text);

        assertTrue(email.isHasDeadline());
        assertNotNull(email.getDeadline());
        assertNotNull(email.getDeadline().getDueDate());
        assertTrue(email.getDeadline().getConfidenceScore() >= 0.7);
        verify(deadlines, times(1)).save(any());
    }

    @Test
    void testIgnoreTextWithoutTriggers() {
        EmailMessage email = new EmailMessage();
        String text = "I saw John yesterday and we had coffee.";

        service.extract(email, text);

        assertFalse(email.isHasDeadline());
        assertNull(email.getDeadline());
        verify(deadlines, never()).save(any());
    }

    @Test
    void testBlankTextHandling() {
        EmailMessage email = new EmailMessage();
        service.extract(email, "");
        service.extract(email, null);

        assertFalse(email.isHasDeadline());
        verify(deadlines, never()).save(any());
    }
}
