package com.example.shoppos.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() {
        admin = users.findByUsername("admin").orElseGet(() ->
                users.save(new User("admin", encoder.encode("admin123"), "Test Admin", Role.ADMIN)));
        String cashierUsername = "test_cashier_" + COUNTER.incrementAndGet();
        cashier = users.save(new User(cashierUsername, encoder.encode("CashierPass123"), "Test Cashier", Role.CASHIER));
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
}
