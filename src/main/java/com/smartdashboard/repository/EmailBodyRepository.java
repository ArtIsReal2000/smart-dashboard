package com.smartdashboard.repository;

import com.smartdashboard.domain.EmailBody;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailBodyRepository extends JpaRepository<EmailBody, Long> {
    Optional<EmailBody> findByEmail_Id(Long emailId);
}
