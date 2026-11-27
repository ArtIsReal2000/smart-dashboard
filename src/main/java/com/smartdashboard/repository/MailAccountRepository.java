package com.smartdashboard.repository;

import com.smartdashboard.domain.MailAccount;
import com.smartdashboard.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MailAccountRepository extends JpaRepository<MailAccount, Long> {
    List<MailAccount> findByUser(User user);
    Optional<MailAccount> findByUserAndEmailAddress(User user, String emailAddress);
}
