package com.smartdashboard.repository;

import com.smartdashboard.domain.Tag;
import com.smartdashboard.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByUser(User user);
    Optional<Tag> findByUserAndName(User user, String name);
    long countByUser(User user);
}
