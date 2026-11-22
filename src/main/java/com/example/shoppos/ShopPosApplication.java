package com.example.shoppos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
        SpringApplication.run(ShopPosApplication.class, args);
    }
}
