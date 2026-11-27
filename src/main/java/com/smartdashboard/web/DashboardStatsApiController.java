package com.smartdashboard.web;

import com.smartdashboard.domain.Deadline;
import com.smartdashboard.domain.User;
import com.smartdashboard.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardStatsApiController {

    private final UserRepository users;
    private final EmailMessageRepository emails;
    private final DeadlineRepository deadlines;
    private final OrganizationRepository orgs;
    private final TagRepository tags;
    private final MailAccountRepository accounts;

    public DashboardStatsApiController(UserRepository users, EmailMessageRepository emails,
                                       DeadlineRepository deadlines, OrganizationRepository orgs,
                                       TagRepository tags, MailAccountRepository accounts) {
        this.users = users;
        this.emails = emails;
        this.deadlines = deadlines;
        this.orgs = orgs;
        this.tags = tags;
        this.accounts = accounts;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(Authentication auth) {
        User user = resolveCurrentUser(auth);
        if (user == null) return Map.of();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfToday = now.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusNanos(1);
        LocalDateTime endOfWeek = now.plusDays(7);

        long totalEmails = emails.countByAccount_User_Id(user.getId());
        long pendingDeadlines = deadlines.countByEmail_Account_User_IdAndResolvedFalse(user.getId());
        long overdueDeadlines = deadlines.countByEmail_Account_User_IdAndResolvedFalseAndDueDateBefore(user.getId(), now);
        long dueTodayDeadlines = deadlines.countByEmail_Account_User_IdAndResolvedFalseAndDueDateBetween(user.getId(), now, endOfToday);
        long dueThisWeekDeadlines = deadlines.countByEmail_Account_User_IdAndResolvedFalseAndDueDateBetween(user.getId(), now, endOfWeek);
        long resolvedDeadlines = deadlines.countByEmail_Account_User_IdAndResolvedTrue(user.getId());

        List<Deadline> upcoming = deadlines.findByEmail_Account_User_IdAndResolvedFalseOrderByDueDateAsc(user.getId())
            .stream().limit(5).toList();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEmails", totalEmails);
        stats.put("totalAccounts", accounts.findByUser(user).size());
        stats.put("totalTags", tags.countByUser(user));
        stats.put("totalOrganizations", orgs.countByUser(user));

        Map<String, Object> deadlineStats = new HashMap<>();
        deadlineStats.put("pending", pendingDeadlines);
        deadlineStats.put("overdue", overdueDeadlines);
        deadlineStats.put("dueToday", dueTodayDeadlines);
        deadlineStats.put("dueThisWeek", dueThisWeekDeadlines);
        deadlineStats.put("resolved", resolvedDeadlines);
        stats.put("deadlines", deadlineStats);

        stats.put("upcomingDeadlines", upcoming.stream().map(d -> Map.of(
            "id", d.getId(),
            "emailId", d.getEmail().getId(),
            "subject", d.getEmail().getSubject() != null ? d.getEmail().getSubject() : "(No subject)",
            "sender", d.getEmail().getSender() != null ? d.getEmail().getSender() : "Unknown",
            "dueDate", d.getDueDate().toString(),
            "description", d.getDescription() != null ? d.getDescription() : "",
            "confidenceScore", d.getConfidenceScore()
        )).toList());

        return stats;
    }

    private User resolveCurrentUser(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return users.findByEmail(email).orElse(null);
            }
        }
        return users.findAll().stream().findFirst().orElse(null);
    }
}
