package com.smartdashboard.repository;

import com.smartdashboard.domain.EmailAttachment;
import com.smartdashboard.domain.EmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, Long> {
    List<EmailAttachment> findByEmail(EmailMessage email);
    List<EmailAttachment> findByEmail_Id(Long emailId);
}
