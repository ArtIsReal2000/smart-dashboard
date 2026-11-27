package com.smartdashboard.service.sync;

import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.repository.MailAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncOrchestrator.class);

    private final MailAccountRepository accounts;
    private final GmailSyncService gmail;
    private final ImapSyncService imap;

    public SyncOrchestrator(MailAccountRepository accounts,
                            GmailSyncService gmail,
                            ImapSyncService imap) {
        this.accounts = accounts;
        this.gmail = gmail;
        this.imap = imap;
    }

    @Scheduled(cron = "${app.sync.gmail-cron}")
    public void scheduledSync() {
        syncAll();
    }

    public void syncAll() {
        List<MailAccount> all = accounts.findAll();
        for (MailAccount a : all) {
            syncAccount(a);
        }
    }

    @Async
    public CompletableFuture<Void> syncAllAsync() {
        syncAll();
        return CompletableFuture.completedFuture(null);
    }

    public void syncAccount(MailAccount a) {
        try {
            if (a.getProvider() == MailAccount.Provider.GMAIL) {
                gmail.sync(a);
            } else {
                imap.sync(a);
            }
            accounts.save(a);
        } catch (Exception e) {
            log.warn("Sync error for [{}]: {}", a.getEmailAddress(), e.getMessage());
        }
    }
}
