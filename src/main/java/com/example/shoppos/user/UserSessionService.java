package com.example.shoppos.user;

import com.example.shoppos.common.web.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;

@Service
public class UserSessionService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionService.class);

    private final SessionRegistry sessionRegistry;

    public UserSessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public int expireSessions(String username) {
        return expireOtherSessions(username, null);
    }

    public int expireOtherSessions(String username, String keepSessionId) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        int expiredCount = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserPrincipal userPrincipal) {
                if (userPrincipal.getUsername() != null && normalized.equalsIgnoreCase(userPrincipal.getUsername().trim())) {
                    for (SessionInformation sessionInfo : sessionRegistry.getAllSessions(principal, false)) {
                        if (keepSessionId != null && keepSessionId.equals(sessionInfo.getSessionId())) {
                            continue;
                        }
                        sessionInfo.expireNow();
                        expiredCount++;
                    }
                }
            }
        }
        if (expiredCount > 0) {
            log.info("Expired {} session(s) for user '{}'", expiredCount, LogSanitizer.safe(normalized));
        }
        return expiredCount;
    }

    public void expireSessionsAfterCommit(String username) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    expireSessions(username);
                }
            });
        } else {
            expireSessions(username);
        }
    }
}
