package com.example.shoppos.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-22T10:00:00Z"), ZoneId.of("UTC"));
        service = new LoginAttemptService(clock, 5, 10);
    }

    @Test
    void fourFailuresNotLocked() {
        for (int i = 0; i < 4; i++) {
            service.loginFailed("user1");
        }
        assertThat(service.isLocked("user1")).isFalse();
    }

    @Test
    void fifthFailureLocks() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();
    }

    @Test
    void lockedJustBeforeLockEndsAndUnlockedAfter() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertThat(service.isLocked("user1")).isTrue();

        clock.advance(Duration.ofSeconds(2));
        assertThat(service.isLocked("user1")).isFalse();
    }

    @Test
    void successResetsCount() {
        for (int i = 0; i < 4; i++) {
            service.loginFailed("user1");
        }
        service.loginSucceeded("user1");

        service.loginFailed("user1");
        assertThat(service.isLocked("user1")).isFalse();
    }

    @Test
    void caseAndWhitespaceNormalized() {
        service.loginFailed("Admin");
        service.loginFailed("admin ");
        service.loginFailed(" ADMIN");
        service.loginFailed("  admin  ");
        service.loginFailed("admin");

        assertThat(service.isLocked("admin")).isTrue();
        assertThat(service.isLocked("ADMIN")).isTrue();
        assertThat(service.isLocked(" Admin ")).isTrue();
    }

    @Test
    void unknownUsernameLocksTheSameWay() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("unknown_user_123");
        }
        assertThat(service.isLocked("unknown_user_123")).isTrue();
    }

    @Test
    void resetClearsAllLocks() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();

        service.reset();
        assertThat(service.isLocked("user1")).isFalse();
    }

    private static class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        MutableClock(Instant start, ZoneId zone) {
            this.now = start;
            this.zone = zone;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
