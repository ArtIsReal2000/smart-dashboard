package com.smartdashboard.service.parse;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.smartdashboard.domain.Deadline;
import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.repository.DeadlineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class DeadlineParserService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineParserService.class);

    private static final Set<String> HIGH_INTENT_TRIGGERS = Set.of(
        "deadline", "due date", "due by", "expires on", "must submit", "action required", "rsvp by", "no later than"
    );

    private static final Set<String> MEDIUM_INTENT_TRIGGERS = Set.of(
        "due", "submit", "expires", "rsvp", "before", "by end of day", "scheduled for"
    );

    private final Parser natty = new Parser();
    private final DeadlineRepository deadlines;

    public DeadlineParserService(DeadlineRepository deadlines) {
        this.deadlines = deadlines;
    }

    public void extract(EmailMessage email, String text) {
        if (text == null || text.isBlank()) return;

        String lower = text.toLowerCase();
        boolean hasHighIntent = HIGH_INTENT_TRIGGERS.stream().anyMatch(lower::contains);
        boolean hasMediumIntent = MEDIUM_INTENT_TRIGGERS.stream().anyMatch(lower::contains);

        if (!hasHighIntent && !hasMediumIntent) return;

        try {
            List<DateGroup> groups = natty.parse(text);
            if (groups == null || groups.isEmpty()) return;

            DateGroup g = groups.get(0);
            if (g.getDates() == null || g.getDates().isEmpty()) return;

            Date d = g.getDates().get(0);
            LocalDateTime due = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

            // Ignore deadlines that are already over 24 hours in the past
            if (due.isBefore(LocalDateTime.now().minusHours(24))) return;

            String matchedDateText = g.getText();
            String contextSentence = extractSurroundingSentence(text, matchedDateText);

            Deadline dl = email.getDeadline();
            if (dl == null) {
                dl = new Deadline();
                dl.setEmail(email);
            }

            dl.setDueDate(due);
            dl.setSourceText(matchedDateText != null ? matchedDateText.trim() : "");
            dl.setDescription(contextSentence != null && !contextSentence.isBlank()
                ? contextSentence
                : "Action required by " + matchedDateText);

            double confidence = hasHighIntent ? 0.85 : 0.65;
            if (contextSentence != null && (contextSentence.toLowerCase().contains("invoice") || contextSentence.toLowerCase().contains("payment"))) {
                confidence = Math.min(1.0, confidence + 0.1);
            }
            dl.setConfidenceScore(confidence);
            dl.setManual(false);

            if (confidence >= 0.55) {
                deadlines.save(dl);
                email.setHasDeadline(true);
                email.setDeadline(dl);
            }
        } catch (Exception e) {
            log.warn("Natty parsing error: ", e);
        }
    }

    private String extractSurroundingSentence(String fullText, String target) {
        if (target == null || target.isBlank() || fullText == null) return null;
        try {
            String trimmedTarget = target.trim();
            int idx = fullText.toLowerCase().indexOf(trimmedTarget.toLowerCase());
            if (idx == -1) return trimmedTarget;

            int start = Math.max(0, idx - 60);
            int end = Math.min(fullText.length(), idx + trimmedTarget.length() + 60);

            if (start < end) {
                return fullText.substring(start, end).trim();
            }
            return trimmedTarget;
        } catch (Exception e) {
            return target.trim();
        }
    }
}
