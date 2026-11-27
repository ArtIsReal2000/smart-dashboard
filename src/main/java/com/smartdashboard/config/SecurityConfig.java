package com.smartdashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           OAuthSuccessHandler successHandler,
                                           ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/**", "/dev-login"))
            .headers(headers -> headers.addHeaderWriter(
                new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/dev-login", "/css/**", "/js/**", "/error", "/h2-console/**", "/health").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(o -> o
                .loginPage("/login")
                .authorizationEndpoint(a -> a
                    .authorizationRequestResolver(authorizationRequestResolver(clientRegistrationRepository)))
                .successHandler(successHandler))
            .logout(l -> l.logoutSuccessUrl("/login?logout=true"));
        return http.build();
    }

    /**
     * Adds access_type=offline + prompt=consent so Google returns a refresh token every time.
     */
    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository repo) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                repo, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(b -> b.additionalParameters(p -> {
            p.put("access_type", "offline");
            p.put("prompt", "consent");
        }));
        return resolver;
    }
}
