package com.smartdashboard.service;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.repository.EmailMessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmailQueryService {

    private final EmailMessageRepository emails;
    private final EntityManager em;

    public EmailQueryService(EmailMessageRepository emails, EntityManager em) {
        this.emails = emails;
        this.em = em;
    }

    public List<EmailMessage> search(Long userId, Long tagId, Long orgId, Boolean hasDeadline,
                                     String q, Instant from, Instant to, String sortBy) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<EmailMessage> cq = cb.createQuery(EmailMessage.class);
        Root<EmailMessage> root = cq.from(EmailMessage.class);
        List<Predicate> ps = new ArrayList<>();

        if (userId != null) {
            ps.add(cb.equal(root.get("account").get("user").get("id"), userId));
        }

        if (tagId != null) {
            Join<Object, Object> tags = root.join("tags");
            ps.add(cb.equal(tags.get("id"), tagId));
        }

        if (orgId != null) {
            ps.add(cb.equal(root.get("organization").get("id"), orgId));
        }

        if (Boolean.TRUE.equals(hasDeadline)) {
            ps.add(cb.isTrue(root.get("hasDeadline")));
        }

        if (q != null && !q.trim().isEmpty()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            Predicate subjectLike = cb.like(cb.lower(root.get("subject")), pattern);
            Predicate snippetLike = cb.like(cb.lower(root.get("snippet")), pattern);
            Predicate senderLike = cb.like(cb.lower(root.get("sender")), pattern);
            ps.add(cb.or(subjectLike, snippetLike, senderLike));
        }

        if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("receivedDate"), from));
        if (to != null)   ps.add(cb.lessThanOrEqualTo(root.get("receivedDate"), to));

        cq.where(ps.toArray(new Predicate[0]));

        if ("deadline".equalsIgnoreCase(sortBy)) {
            cq.orderBy(cb.asc(root.get("deadline").get("dueDate")));
        } else if ("sender".equalsIgnoreCase(sortBy)) {
            cq.orderBy(cb.asc(root.get("sender")));
        } else {
            cq.orderBy(cb.desc(root.get("receivedDate")));
        }

        return em.createQuery(cq).setMaxResults(200).getResultList();
    }
}
