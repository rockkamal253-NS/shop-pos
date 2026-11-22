package com.example.shoppos.common.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.timezone=Asia/Tokyo", webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TimezoneConfigTest {

    private static TimeZone originalDefault = TimeZone.getDefault();

    @BeforeAll
    static void saveOriginalDefault() {
        if (originalDefault == null) {
            originalDefault = TimeZone.getDefault();
        }
    }

    @AfterAll
    static void restoreOriginalDefault() {
        if (originalDefault != null) {
            TimeZone.setDefault(originalDefault);
        }
    }

    @Test
    void defaultTimezoneIsConfiguredOneAfterContextStarts() {
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }
}
