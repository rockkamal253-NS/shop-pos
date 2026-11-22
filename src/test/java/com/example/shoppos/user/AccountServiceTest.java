package com.example.shoppos.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneId.of("UTC"));
        LoginAttemptService loginAttemptService = new LoginAttemptService(clock, 5, 10);
        accountService = new AccountService(userRepository, passwordEncoder, loginAttemptService);
    }

    private User createTestUser(String rawPassword) {
        return new User("testuser", passwordEncoder.encode(rawPassword), "Test User", Role.CASHIER);
    }

    @Test
    void wrongCurrentPasswordRejectedAndHashUnchanged() {
        User user = createTestUser("currentPass123");
        String originalHash = user.getPassword();
        PasswordChangeForm form = new PasswordChangeForm("wrongPass123", "newPassword123", "newPassword123");
        BindingResult binding = new BeanPropertyBindingResult(form, "form");

        boolean result = accountService.changePassword(user, form, binding);

        assertThat(result).isFalse();
        assertThat(binding.hasFieldErrors("currentPassword")).isTrue();
        assertThat(binding.getFieldError("currentPassword").getDefaultMessage()).isEqualTo("Current password is incorrect.");
        assertThat(user.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(user);
    }

    @Test
    void sevenCharNewPasswordRejected() {
        User user = createTestUser("currentPass123");
        String originalHash = user.getPassword();
        PasswordChangeForm form = new PasswordChangeForm("currentPass123", "1234567", "1234567");
        BindingResult binding = new BeanPropertyBindingResult(form, "form");

        boolean result = accountService.changePassword(user, form, binding);

        assertThat(result).isFalse();
        assertThat(binding.hasFieldErrors("newPassword")).isTrue();
        assertThat(binding.getFieldError("newPassword").getDefaultMessage()).isEqualTo("New password must be between 8 and 72 characters.");
        assertThat(user.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(user);
    }

    @Test
    void seventyThreeCharNewPasswordRejected() {
        User user = createTestUser("currentPass123");
        String originalHash = user.getPassword();
        String pass73 = "a".repeat(73);
        PasswordChangeForm form = new PasswordChangeForm("currentPass123", pass73, pass73);
        BindingResult binding = new BeanPropertyBindingResult(form, "form");

        boolean result = accountService.changePassword(user, form, binding);

        assertThat(result).isFalse();
        assertThat(binding.hasFieldErrors("newPassword")).isTrue();
        assertThat(binding.getFieldError("newPassword").getDefaultMessage()).isEqualTo("New password must be between 8 and 72 characters.");
        assertThat(user.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(user);
    }

    @Test
    void sameAsCurrentRejected() {
        User user = createTestUser("currentPass123");
        String originalHash = user.getPassword();
        PasswordChangeForm form = new PasswordChangeForm("currentPass123", "currentPass123", "currentPass123");
        BindingResult binding = new BeanPropertyBindingResult(form, "form");

        boolean result = accountService.changePassword(user, form, binding);

        assertThat(result).isFalse();
        assertThat(binding.hasFieldErrors("newPassword")).isTrue();
        assertThat(binding.getFieldError("newPassword").getDefaultMessage()).isEqualTo("New password must differ from current password.");
        assertThat(user.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(user);
    }

    @Test
    void mismatchRejected() {
        User user = createTestUser("currentPass123");
        String originalHash = user.getPassword();
        PasswordChangeForm form = new PasswordChangeForm("currentPass123", "newPassword123", "differentPassword123");
        BindingResult binding = new BeanPropertyBindingResult(form, "form");

        boolean result = accountService.changePassword(user, form, binding);

        assertThat(result).isFalse();
        assertThat(binding.hasFieldErrors("confirmPassword")).isTrue();
        assertThat(binding.getFieldError("confirmPassword").getDefaultMessage()).isEqualTo("New passwords do not match.");
        assertThat(user.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(user);
    }

    @Test
    void successChangesHashAndNewPasswordMatches() {
        User user = createTestUser("currentPass123");
        String originalHash = user.getPassword();
        PasswordChangeForm form = new PasswordChangeForm("currentPass123", "newPassword123", "newPassword123");
        BindingResult binding = new BeanPropertyBindingResult(form, "form");

        boolean result = accountService.changePassword(user, form, binding);

        assertThat(result).isTrue();
        assertThat(binding.hasErrors()).isFalse();
        assertThat(user.getPassword()).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches("newPassword123", user.getPassword())).isTrue();
        verify(userRepository).save(user);
    }
}
