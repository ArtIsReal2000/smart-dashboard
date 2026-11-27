package com.smartdashboard.service.parse;

import com.smartdashboard.domain.EmailMessage;
import com.smartdashboard.domain.Organization;
import com.smartdashboard.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrganizationInferenceService {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@([\\w.-]+)");

    private final OrganizationRepository orgs;

    public OrganizationInferenceService(OrganizationRepository orgs) {
        this.orgs = orgs;
    }

    public Optional<Organization> infer(EmailMessage email) {
        if (email.getSender() == null) return Optional.empty();
        Matcher m = EMAIL.matcher(email.getSender());
        if (!m.find()) return Optional.empty();
        String domain = m.group(1).toLowerCase();

        // Common free-mail domains → no organization
        if (domain.endsWith("gmail.com") || domain.endsWith("yahoo.com")
            || domain.endsWith("outlook.com") || domain.endsWith("hotmail.com")
            || domain.endsWith("icloud.com") || domain.endsWith("proton.me")
            || domain.endsWith("protonmail.com")) {
            return Optional.empty();
        }

        return orgs.findByUserAndDomain(email.getAccount().getUser(), domain)
            .or(() -> {
                Organization o = new Organization();
                o.setUser(email.getAccount().getUser());
                o.setDomain(domain);
                // Clean and format organization name
                String base = domain.split("\\.")[0];
                String name = Character.toUpperCase(base.charAt(0)) + (base.length() > 1 ? base.substring(1) : "");
                o.setName(name);
                return Optional.of(orgs.save(o));
            });
    }
}
