package com.example.shoppos.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionServiceTest {

    private SessionRegistryImpl sessionRegistry;
    private UserSessionService userSessionService;

    @BeforeEach
    void setUp() {
        sessionRegistry = new SessionRegistryImpl();
        userSessionService = new UserSessionService(sessionRegistry);
    }

    @Test
    void expireSessions_expiresMatchingUserSessionsAndIgnoresOthers() {
        User user1a = new User("cashier1", "hash", "Cashier 1A", Role.CASHIER);
        User user1b = new User("cashier1", "hash", "Cashier 1B", Role.CASHIER);
        UserPrincipal principal1a = new UserPrincipal(user1a);
        UserPrincipal principal1b = new UserPrincipal(user1b);

        User user2 = new User("cashier2", "hash", "Cashier 2", Role.CASHIER);
        UserPrincipal principal2 = new UserPrincipal(user2);

        sessionRegistry.registerNewSession("session-1a", principal1a);
        sessionRegistry.registerNewSession("session-1b", principal1b);
        sessionRegistry.registerNewSession("session-2", principal2);
        sessionRegistry.registerNewSession("session-anon", "anon");

        int count = userSessionService.expireSessions("Cashier1 ");

        assertThat(count).isEqualTo(2);
        assertThat(sessionRegistry.getSessionInformation("session-1a").isExpired()).isTrue();
        assertThat(sessionRegistry.getSessionInformation("session-1b").isExpired()).isTrue();
        assertThat(sessionRegistry.getSessionInformation("session-2").isExpired()).isFalse();
        assertThat(sessionRegistry.getSessionInformation("session-anon").isExpired()).isFalse();
    }

    @Test
    void expireSessions_unknownUserReturnsZero() {
        User user1 = new User("cashier1", "hash", "Cashier 1", Role.CASHIER);
        sessionRegistry.registerNewSession("session-1", new UserPrincipal(user1));

        int count = userSessionService.expireSessions("unknown");

        assertThat(count).isZero();
        assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isFalse();
    }

    @Test
    void expireSessions_nullOrBlankReturnsZero() {
        User user1 = new User("cashier1", "hash", "Cashier 1", Role.CASHIER);
        sessionRegistry.registerNewSession("session-1", new UserPrincipal(user1));

        assertThat(userSessionService.expireSessions(null)).isZero();
        assertThat(userSessionService.expireSessions("")).isZero();
        assertThat(userSessionService.expireSessions("   ")).isZero();
        assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isFalse();
    }

    @Test
    void expireSessionsAfterCommit_withoutActiveTransactionExpiresImmediately() {
        User user1 = new User("cashier1", "hash", "Cashier 1", Role.CASHIER);
        sessionRegistry.registerNewSession("session-1", new UserPrincipal(user1));

        userSessionService.expireSessionsAfterCommit("cashier1");

        assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isTrue();
    }

    @Test
    void expireSessionsAfterCommit_withActiveTransactionExpiresAfterCommitOnly() {
        User user1 = new User("cashier1", "hash", "Cashier 1", Role.CASHIER);
        sessionRegistry.registerNewSession("session-1", new UserPrincipal(user1));

        TransactionSynchronizationManager.initSynchronization();
        try {
            userSessionService.expireSessionsAfterCommit("cashier1");
            assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isFalse();

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
