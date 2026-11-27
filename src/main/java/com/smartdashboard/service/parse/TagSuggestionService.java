package com.smartdashboard.service.parse;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.Tag;
import com.smartdashboard.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class TagSuggestionService {

    // Keyword to tag name mapping
    private static final Map<String, String> KEYWORDS = Map.of(
        "invoice", "Finance",
        "receipt", "Finance",
        "payment", "Finance",
        "meeting", "Meetings",
        "calendar", "Meetings",
        "zoom", "Meetings",
        "urgent", "Urgent",
        "action required", "Urgent",
        "newsletter", "Newsletter",
        "security", "Security"
    );

    private static final Map<String, String> TAG_COLORS = Map.of(
        "Finance", "#10b981",
        "Meetings", "#8b5cf6",
        "Urgent", "#ef4444",
        "Newsletter", "#f59e0b",
        "Security", "#6366f1"
    );

    private final TagRepository tags;

    public TagSuggestionService(TagRepository tags) {
        this.tags = tags;
    }

    public Set<Tag> suggest(EmailMessage email) {
        Set<Tag> result = new HashSet<>();
        String haystack = ((email.getSubject() == null ? "" : email.getSubject())
            + " " + (email.getSnippet() == null ? "" : email.getSnippet())).toLowerCase();

        for (var entry : KEYWORDS.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                String tagName = entry.getValue();
                tags.findByUserAndName(email.getAccount().getUser(), tagName)
                    .ifPresentOrElse(result::add, () -> {
                        Tag t = new Tag();
                        t.setUser(email.getAccount().getUser());
                        t.setName(tagName);
                        t.setColor(TAG_COLORS.getOrDefault(tagName, "#3b82f6"));
                        result.add(tags.save(t));
                    });
            }
        }
        return result;
    }
}
