package com.example.shoppos.user;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginThrottlingIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Test
    void fiveFailedLoginsLockUserAndCorrectPasswordFails() throws Exception {
        String username = "throttle_admin_" + COUNTER.incrementAndGet();
        String password = "SecretPassword123";
        User user = users.save(new User(username, encoder.encode(password), "Test Admin", Role.ADMIN));
        users.flush();
        String originalHash = user.getPassword();

        // 5 failed logins
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "WrongPass123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        assertThat(loginAttemptService.isLocked(username)).isTrue();

        // 6th attempt with CORRECT password still fails
        mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        users.flush();
        User reloaded = users.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isEqualTo(originalHash);
    }

    @Test
    void successfulLoginResetsFailureCount() throws Exception {
        String username = "throttle_reset_" + COUNTER.incrementAndGet();
        String password = "SecretPassword123";
        users.save(new User(username, encoder.encode(password), "Reset User", Role.CASHIER));

        // 4 failed logins
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "WrongPass123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        assertThat(loginAttemptService.isLocked(username)).isFalse();

        // 1 successful login resets counter
        mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(loginAttemptService.isLocked(username)).isFalse();
    }

    @Test
    void failedCurrentPasswordAttemptsOnAccountPasswordCountTowardLock() throws Exception {
        String username = "throttle_acct_" + COUNTER.incrementAndGet();
        String password = "SecretPassword123";
        User user = users.save(new User(username, encoder.encode(password), "Account User", Role.CASHIER));

        // 5 wrong current password submissions on /account/password
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/account/password")
                            .with(user(new UserPrincipal(user)))
                            .with(csrf())
                            .param("currentPassword", "WrongCurrent123")
                            .param("newPassword", "NewValidPass123")
                            .param("confirmPassword", "NewValidPass123"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "currentPassword"));
        }

        assertThat(loginAttemptService.isLocked(username)).isTrue();

        // While locked, submitting correct password on /account/password is rejected as locked
        mvc.perform(post("/account/password")
                        .with(user(new UserPrincipal(user)))
                        .with(csrf())
                        .param("currentPassword", password)
                        .param("newPassword", "NewValidPass123")
                        .param("confirmPassword", "NewValidPass123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("form", "currentPassword", "account.locked"));

        // Logging in is also locked
        mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void loginPageShowsGenericErrorMessage() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password, or too many attempts. Try again later.")));
    }
}
