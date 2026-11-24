package com.example.shoppos.user;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserControllerTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private User admin;
    private User cashier;
    private ListAppender<ILoggingEvent> auditAppender;
    private Logger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger("shoppos.audit");
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);

        admin = users.findByUsername("admin").orElseGet(() ->
                users.save(new User("admin", encoder.encode("admin123"), "Test Admin", Role.ADMIN)));
        String cashierUsername = "test_cashier_" + COUNTER.incrementAndGet();
        cashier = users.save(new User(cashierUsername, encoder.encode("CashierPass123"), "Test Cashier", Role.CASHIER));
    }

    @AfterEach
    void tearDown() {
        if (auditLogger != null && auditAppender != null) {
            auditLogger.detachAppender(auditAppender);
            auditAppender.stop();
        }
    }

    @Test
    void anonymousGetAdminUsers_redirectedToLogin() throws Exception {
        mvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void cashierGetAdminUsers_returnsForbidden() throws Exception {
        mvc.perform(get("/admin/users").with(user(new UserPrincipal(cashier))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGetAdminUsers_returns200AndContainsSeededUsernames() throws Exception {
        mvc.perform(get("/admin/users").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeExists("users", "roles", "currentUserId"))
                .andExpect(content().string(containsString("admin")))
                .andExpect(content().string(containsString(cashier.getUsername())));
    }

    @Test
    void adminPostCreate_validCashierRedirectsAndStoresMatchingHash() throws Exception {
        String newUsername = "new_cashier_" + COUNTER.incrementAndGet();
        mvc.perform(post("/admin/users")
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("username", newUsername)
                        .param("displayName", "New Cashier")
                        .param("password", "ValidPass123")
                        .param("confirmPassword", "ValidPass123")
                        .param("role", "CASHIER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("toastSuccess", "User created."));

        User created = users.findByUsername(newUsername).orElseThrow();
        assertTrue(encoder.matches("ValidPass123", created.getPassword()));
        assertThat(created.getRole()).isEqualTo(Role.CASHIER);
        assertThat(created.getDisplayName()).isEqualTo("New Cashier");
    }

    @Test
    void cashierPostCreate_returnsForbiddenAndNoUserCreated() throws Exception {
        String newUsername = "unauthorized_" + COUNTER.incrementAndGet();
        mvc.perform(post("/admin/users")
                        .with(user(new UserPrincipal(cashier)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("username", newUsername)
                        .param("displayName", "Unauthorized")
                        .param("password", "ValidPass123")
                        .param("confirmPassword", "ValidPass123")
                        .param("role", "CASHIER"))
                .andExpect(status().isForbidden());

        assertFalse(users.existsByUsername(newUsername));
    }

    @Test
    void adminPostCreate_mismatchingPasswordsRedirectsWithErrorFlash() throws Exception {
        String newUsername = "mismatch_" + COUNTER.incrementAndGet();
        mvc.perform(post("/admin/users")
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("username", newUsername)
                        .param("displayName", "Mismatch User")
                        .param("password", "ValidPass123")
                        .param("confirmPassword", "DifferentPass456")
                        .param("role", "CASHIER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("toastError", "Passwords do not match."));

        assertFalse(users.existsByUsername(newUsername));
    }

    @Test
    void adminPostPassword_forCashierUpdatesStoredHash() throws Exception {
        mvc.perform(post("/admin/users/{id}/password", cashier.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("newPassword", "NewSecretPass789")
                        .param("confirmPassword", "NewSecretPass789"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("toastSuccess", "Password updated."));

        users.flush();
        User updated = users.findById(cashier.getId()).orElseThrow();
        assertTrue(encoder.matches("NewSecretPass789", updated.getPassword()));
    }

    @Test
    void cashierPostPassword_returnsForbiddenAndHashUnchanged() throws Exception {
        String originalHash = cashier.getPassword();
        mvc.perform(post("/admin/users/{id}/password", cashier.getId())
                        .with(user(new UserPrincipal(cashier)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("newPassword", "NewSecretPass789")
                        .param("confirmPassword", "NewSecretPass789"))
                .andExpect(status().isForbidden());

        users.flush();
        User unchanged = users.findById(cashier.getId()).orElseThrow();
        assertEquals(originalHash, unchanged.getPassword());
    }

    @Test
    void adminPostEnabledFalse_disablesCashierAndCashierCannotDisable() throws Exception {
        mvc.perform(post("/admin/users/{id}/enabled", cashier.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("toastSuccess", "User disabled."));

        users.flush();
        User disabled = users.findById(cashier.getId()).orElseThrow();
        assertFalse(disabled.isEnabled());

        mvc.perform(post("/admin/users/{id}/enabled", cashier.getId())
                        .with(user(new UserPrincipal(cashier)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("enabled", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTryingToResetOrDisableOwnId_redirectsWithErrorFlashAndNothingChanged() throws Exception {
        String originalAdminHash = admin.getPassword();
        mvc.perform(post("/admin/users/{id}/password", admin.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("newPassword", "NewAdminSecretPass123")
                        .param("confirmPassword", "NewAdminSecretPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("toastError", "Use Change password for your own account."));

        users.flush();
        User reloadedAdmin = users.findById(admin.getId()).orElseThrow();
        assertEquals(originalAdminHash, reloadedAdmin.getPassword());

        mvc.perform(post("/admin/users/{id}/enabled", admin.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("toastError", "You cannot disable your own account."));

        reloadedAdmin = users.findById(admin.getId()).orElseThrow();
        assertTrue(reloadedAdmin.isEnabled());
    }

    @Test
    void auditLogOnSuccessfulCreate() throws Exception {
        String newUsername = "audit_cashier_" + COUNTER.incrementAndGet();
        mvc.perform(post("/admin/users")
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("username", newUsername)
                        .param("displayName", "Audit Cashier")
                        .param("password", "SecretAuditPass123")
                        .param("confirmPassword", "SecretAuditPass123")
                        .param("role", "CASHIER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(auditAppender.list).hasSize(1);
        ILoggingEvent event = auditAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains(admin.getUsername());
        assertThat(msg).contains(newUsername);
        assertThat(msg).contains("CASHIER");
        assertThat(msg).doesNotContain("SecretAuditPass123");
    }

    @Test
    void auditLogOnSuccessfulPasswordReset() throws Exception {
        mvc.perform(post("/admin/users/{id}/password", cashier.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("newPassword", "NewSecretPassAudit789")
                        .param("confirmPassword", "NewSecretPassAudit789"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(auditAppender.list).hasSize(1);
        ILoggingEvent event = auditAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("reset the password of user id " + cashier.getId());
        assertThat(msg).doesNotContain("NewSecretPassAudit789");
    }

    @Test
    void auditLogOnDisableAndEnable() throws Exception {
        mvc.perform(post("/admin/users/{id}/enabled", cashier.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        assertThat(auditAppender.list).hasSize(1);
        assertThat(auditAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(auditAppender.list.get(0).getFormattedMessage())
                .contains("disabled user id " + cashier.getId());

        auditAppender.list.clear();

        mvc.perform(post("/admin/users/{id}/enabled", cashier.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(auditAppender.list).hasSize(1);
        assertThat(auditAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(auditAppender.list.get(0).getFormattedMessage())
                .contains("enabled user id " + cashier.getId());
    }

    @Test
    void auditLogOnFailingRequestsZeroEvents() throws Exception {
        // passwords do not match on create
        mvc.perform(post("/admin/users")
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("username", "fail_" + COUNTER.incrementAndGet())
                        .param("displayName", "Fail User")
                        .param("password", "Pass123456")
                        .param("confirmPassword", "Pass654321")
                        .param("role", "CASHIER"))
                .andExpect(status().is3xxRedirection());

        // passwords do not match on reset
        mvc.perform(post("/admin/users/{id}/password", cashier.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("newPassword", "Pass123456")
                        .param("confirmPassword", "Pass654321"))
                .andExpect(status().is3xxRedirection());

        // disabling own account
        mvc.perform(post("/admin/users/{id}/enabled", admin.getId())
                        .with(user(new UserPrincipal(admin)))
                        .with(csrf())
                        .header("Referer", "/admin/users")
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        assertThat(auditAppender.list).isEmpty();
    }
}
