package com.smartdashboard.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Configuration
public class AppConfig {

    @Bean
    public NetHttpTransport httpTransport() throws GeneralSecurityException, IOException {
        return com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public GsonFactory gsonFactory() {
        return GsonFactory.getDefaultInstance();
    }
}
