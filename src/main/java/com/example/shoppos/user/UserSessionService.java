package com.example.shoppos.user;

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
        if (username == null || username.isBlank()) {
            return 0;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        int expiredCount = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserPrincipal userPrincipal) {
                if (userPrincipal.getUsername() != null && normalized.equalsIgnoreCase(userPrincipal.getUsername().trim())) {
                    for (SessionInformation sessionInfo : sessionRegistry.getAllSessions(principal, false)) {
                        sessionInfo.expireNow();
                        expiredCount++;
                    }
                }
            }
        }
        if (expiredCount > 0) {
            log.info("Expired {} session(s) for user '{}'", expiredCount, sanitize(normalized));
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

    private String sanitize(String key) {
        String truncated = key.length() > 64 ? key.substring(0, 64) : key;
        return truncated.replaceAll("[^a-zA-Z0-9._@-]", "_");
    }
}
