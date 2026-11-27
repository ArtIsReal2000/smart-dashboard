package com.smartdashboard.repository;

import com.smartdashboard.domain.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {
    List<Deadline> findByDueDateBetweenAndNotifiedFalse(LocalDateTime from, LocalDateTime to);
    List<Deadline> findByEmail_Account_User_IdOrderByDueDateAsc(Long userId);
    List<Deadline> findByEmail_Account_User_IdAndResolvedFalseOrderByDueDateAsc(Long userId);
    List<Deadline> findByEmail_Account_User_IdAndResolvedTrueOrderByDueDateDesc(Long userId);
    Optional<Deadline> findByEmail_Id(Long emailId);

    long countByEmail_Account_User_IdAndResolvedFalse(Long userId);
    long countByEmail_Account_User_IdAndResolvedFalseAndDueDateBefore(Long userId, LocalDateTime now);
    long countByEmail_Account_User_IdAndResolvedFalseAndDueDateBetween(Long userId, LocalDateTime from, LocalDateTime to);
    long countByEmail_Account_User_IdAndResolvedTrue(Long userId);
}
