package com.smartdashboard.repository;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.MailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    boolean existsByAccountAndProviderMessageId(MailAccount account, String providerMessageId);
    List<EmailMessage> findByAccount(MailAccount account);
    List<EmailMessage> findByAccount_User_Id(Long userId);
    long countByAccount_User_Id(Long userId);
    long countByAccount_User_IdAndHasDeadlineTrue(Long userId);
}
