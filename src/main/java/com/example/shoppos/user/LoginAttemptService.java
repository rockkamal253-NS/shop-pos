package com.example.shoppos.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginAttemptService {

    private final Clock clock;
    private final int maxAttempts;
    private final int lockMinutes;
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock,
                               @Value("${app.security.max-login-attempts:5}") int maxAttempts,
                               @Value("${app.security.lock-minutes:10}") int lockMinutes) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    public boolean isLocked(String rawUsername) {
        if (rawUsername == null) {
            return false;
        }
        String key = normalize(rawUsername);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return false;
        }
        Instant now = clock.instant();
        if (state.lockedUntil != null) {
            if (now.isBefore(state.lockedUntil)) {
                return true;
            }
            attempts.remove(key, state);
            return false;
        }
        return false;
    }

    public void loginFailed(String rawUsername) {
        if (rawUsername == null) {
            return;
        }
        String key = normalize(rawUsername);
        Instant now = clock.instant();
        purgeExpired(now);
        attempts.compute(key, (k, state) -> {
            if (state == null) {
                state = new AttemptState();
            }
            if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
                state.failureCount = 0;
                state.lockedUntil = null;
            }
            state.failureCount++;
            state.lastModified = now;
            if (state.failureCount >= maxAttempts && state.lockedUntil == null) {
                state.lockedUntil = now.plus(lockMinutes, ChronoUnit.MINUTES);
            }
            return state;
        });
    }

    public void loginSucceeded(String rawUsername) {
        if (rawUsername == null) {
            return;
        }
        attempts.remove(normalize(rawUsername));
    }

    public void reset() {
        attempts.clear();
    }

    private void purgeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            if (state.lockedUntil != null) {
                return !now.isBefore(state.lockedUntil);
            }
            return state.lastModified != null && now.isAfter(state.lastModified.plus(lockMinutes, ChronoUnit.MINUTES));
        });
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptState {
        int failureCount;
        Instant lockedUntil;
        Instant lastModified;
    }
}
