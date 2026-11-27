package com.smartdashboard.repository;

import com.smartdashboard.domain.Organization;
import com.smartdashboard.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findByUser(User user);
    Optional<Organization> findByUserAndDomain(User user, String domain);
    long countByUser(User user);
}
