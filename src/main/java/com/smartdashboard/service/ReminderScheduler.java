package com.smartdashboard.service;

import com.smartdashboard.domain.Deadline;
import com.smartdashboard.repository.DeadlineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private final DeadlineRepository deadlines;

    public ReminderScheduler(DeadlineRepository deadlines) {
        this.deadlines = deadlines;
    }

    @Scheduled(cron = "${app.reminders.cron}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        List<Deadline> due = deadlines.findByDueDateBetweenAndNotifiedFalse(
            now, now.plusDays(2));
        for (Deadline d : due) {
            log.info("⏰ Reminder Alert: '{}' due at {} (email: {})",
                d.getEmail().getSubject(), d.getDueDate(), d.getEmail().getAccount().getEmailAddress());
            d.setNotified(true);
            deadlines.save(d);
        }
    }
}
