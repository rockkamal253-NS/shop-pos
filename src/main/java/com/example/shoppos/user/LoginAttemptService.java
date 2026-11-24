package com.example.shoppos.user;

import com.example.shoppos.common.web.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final Clock clock;
    private final int maxAttempts;
    private final int lockMinutes;
    private final int maxTrackedUsernames;
    private final Map<String, AttemptState> attempts = new LinkedHashMap<>(16, 0.75f, true);
    private Instant lastCapWarningAt;

    public LoginAttemptService(Clock clock,
                               int maxAttempts,
                               int lockMinutes) {
        this(clock, maxAttempts, lockMinutes, 10000);
    }

    @Autowired
    public LoginAttemptService(Clock clock,
                               @Value("${app.security.max-login-attempts:5}") int maxAttempts,
                               @Value("${app.security.lock-minutes:10}") int lockMinutes,
                               @Value("${app.security.max-tracked-usernames:10000}") int maxTrackedUsernames) {
        if (maxAttempts < 1 || lockMinutes < 1 || maxTrackedUsernames < 1) {
            throw new IllegalArgumentException("maxAttempts, lockMinutes, and maxTrackedUsernames must be >= 1");
        }
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
        this.maxTrackedUsernames = maxTrackedUsernames;
    }

    public synchronized boolean isLocked(String rawUsername) {
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
            attempts.remove(key);
            return false;
        }
        return false;
    }

    public synchronized void loginFailed(String rawUsername) {
        if (rawUsername == null) {
            return;
        }
        String key = normalize(rawUsername);
        Instant now = clock.instant();
        AttemptState state = attempts.get(key);
        if (state == null) {
            if (attempts.size() >= maxTrackedUsernames) {
                boolean evicted = false;
                for (Iterator<Map.Entry<String, AttemptState>> it = attempts.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, AttemptState> entry = it.next();
                    AttemptState s = entry.getValue();
                    boolean locked = s.lockedUntil != null && now.isBefore(s.lockedUntil);
                    if (!locked) {
                        it.remove();
                        evicted = true;
                        break;
                    }
                }
                if (!evicted) {
                    if (lastCapWarningAt == null || !now.isBefore(lastCapWarningAt.plus(1, ChronoUnit.MINUTES))) {
                        log.warn("Login attempt tracking capacity ({}) reached and all entries are locked; dropping failure for new username", maxTrackedUsernames);
                        lastCapWarningAt = now;
                    }
                    return;
                }
            }
            state = new AttemptState();
            attempts.put(key, state);
        }

        if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
            state.failureCount = 0;
            state.lockedUntil = null;
        }
        state.failureCount++;
        state.lastModified = now;
        if (state.failureCount >= maxAttempts && state.lockedUntil == null) {
            state.lockedUntil = now.plus(lockMinutes, ChronoUnit.MINUTES);
            log.warn("Login lock started for user '{}' until {} after {} failed attempts",
                    LogSanitizer.safe(key), state.lockedUntil, state.failureCount);
        }
    }

    public synchronized void loginSucceeded(String rawUsername) {
        if (rawUsername == null) {
            return;
        }
        attempts.remove(normalize(rawUsername));
    }

    synchronized void reset() {
        attempts.clear();
    }

    synchronized int trackedCount() {
        return attempts.size();
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
