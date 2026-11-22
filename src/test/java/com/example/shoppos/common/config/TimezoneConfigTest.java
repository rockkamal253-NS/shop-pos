package com.example.shoppos.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TimezoneConfigTest {

    @Value("${app.timezone:Africa/Cairo}")
    private String configuredTimezone;

    @Test
    void defaultTimezoneIsConfiguredOneAfterContextStarts() {
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of(configuredTimezone));
    }
}
