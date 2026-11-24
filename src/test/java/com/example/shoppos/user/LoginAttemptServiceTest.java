package com.example.shoppos.user;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptServiceTest {

    private MutableClock clock;
    private LoginAttemptService service;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logbackLogger;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-22T10:00:00Z"), ZoneId.of("UTC"));
        service = new LoginAttemptService(clock, 5, 10);
    }

    @AfterEach
    void tearDown() {
        if (logbackLogger != null && listAppender != null) {
            logbackLogger.detachAppender(listAppender);
        }
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

    @Test
    void floodStaysBounded() {
        LoginAttemptService bounded = new LoginAttemptService(clock, 5, 10, 100);
        for (int i = 0; i < 1000; i++) {
            bounded.loginFailed("user_" + i);
        }
        assertThat(bounded.trackedCount()).isLessThanOrEqualTo(100);
    }

    @Test
    void lockedAccountSurvivesFlood() {
        LoginAttemptService bounded = new LoginAttemptService(clock, 5, 10, 50);
        for (int i = 0; i < 5; i++) {
            bounded.loginFailed("victim");
        }
        assertThat(bounded.isLocked("victim")).isTrue();

        for (int i = 0; i < 500; i++) {
            bounded.loginFailed("flooder_" + i);
        }
        assertThat(bounded.isLocked("victim")).isTrue();
        assertThat(bounded.trackedCount()).isLessThanOrEqualTo(50);
    }

    @Test
    void oldestUnlockedEntryGoesFirst() {
        LoginAttemptService bounded = new LoginAttemptService(clock, 5, 10, 3);
        bounded.loginFailed("a");
        bounded.loginFailed("b");
        bounded.loginFailed("c");
        bounded.loginFailed("d");

        assertThat(bounded.trackedCount()).isEqualTo(3);

        for (int i = 0; i < 4; i++) {
            bounded.loginFailed("a");
        }
        assertThat(bounded.isLocked("a")).isFalse();

        bounded.loginFailed("a");
        assertThat(bounded.isLocked("a")).isTrue();
    }

    @Test
    void allLockedAtCap() {
        LoginAttemptService bounded = new LoginAttemptService(clock, 5, 10, 3);
        for (int i = 0; i < 5; i++) {
            bounded.loginFailed("u1");
            bounded.loginFailed("u2");
            bounded.loginFailed("u3");
        }
        assertThat(bounded.isLocked("u1")).isTrue();
        assertThat(bounded.isLocked("u2")).isTrue();
        assertThat(bounded.isLocked("u3")).isTrue();

        bounded.loginFailed("u4");
        assertThat(bounded.trackedCount()).isEqualTo(3);
        assertThat(bounded.isLocked("u4")).isFalse();

        for (int i = 0; i < 4; i++) {
            bounded.loginFailed("u4");
        }
        assertThat(bounded.isLocked("u4")).isFalse();
        assertThat(bounded.isLocked("u1")).isTrue();
        assertThat(bounded.isLocked("u2")).isTrue();
        assertThat(bounded.isLocked("u3")).isTrue();
    }

    @Test
    void lockLogging() {
        logbackLogger = (Logger) LoggerFactory.getLogger(LoginAttemptService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);

        for (int i = 0; i < 4; i++) {
            service.loginFailed("user1");
        }
        long warnCount = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .count();
        assertThat(warnCount).isZero();

        service.loginFailed("user1");
        List<ILoggingEvent> warnEvents = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnEvents).hasSize(1);
        assertThat(warnEvents.get(0).getFormattedMessage())
                .contains("user1")
                .containsIgnoringCase("lock");

        service.loginFailed("user1");
        warnEvents = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnEvents).hasSize(1);
    }

    @Test
    void logSanitising() {
        logbackLogger = (Logger) LoggerFactory.getLogger(LoginAttemptService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);

        String badName = "bad\nname x";
        for (int i = 0; i < 5; i++) {
            service.loginFailed(badName);
        }
        List<ILoggingEvent> warnEvents = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnEvents).hasSize(1);
        String message = warnEvents.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("\n");
        assertThat(message).contains("user '");
        int start = message.indexOf("user '") + "user '".length();
        int end = message.indexOf("'", start);
        assertThat(end).isGreaterThan(start);
        String sanitizedName = message.substring(start, end);
        assertThat(sanitizedName).doesNotContain(" ").doesNotContain("\n");
    }

    @Test
    void constructorGuardThrowsOnInvalidArgs() {
        assertThrows(IllegalArgumentException.class, () ->
                new LoginAttemptService(clock, 0, 10));
        assertThrows(IllegalArgumentException.class, () ->
                new LoginAttemptService(clock, 5, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new LoginAttemptService(clock, 5, 10, 0));
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
