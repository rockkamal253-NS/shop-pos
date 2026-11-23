package com.example.shoppos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
public class ShopPosApplication {

    public ShopPosApplication(@Value("${app.timezone:Africa/Cairo}") String appTimezone) {
        // Preferred approach: configure JVM default timezone to match app.timezone so that
        // all LocalDateTime.now() calls in JPA @PrePersist/@PreUpdate hooks and services
        // automatically use the single application timezone without rewriting entities.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(appTimezone)));
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ShopPosApplication.class);
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event ->
                requireKnownProfile(event.getEnvironment()));
        app.run(args);
    }

    static void requireKnownProfile(Environment env) {
        if (!env.acceptsProfiles(Profiles.of("dev", "test", "prod"))) {
            throw new IllegalStateException("No Spring profile is active. Set SPRING_PROFILES_ACTIVE=prod for production (or dev for local development).");
        }
    }
}
