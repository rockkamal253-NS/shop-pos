package com.example.shoppos.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.Clock;

@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    @Autowired
    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder) {
        this(users, passwordEncoder, new LoginAttemptService(Clock.systemDefaultZone(), 5, 10));
    }

    @Transactional
    public boolean changePassword(User user, PasswordChangeForm form, BindingResult binding) {
        if (loginAttemptService.isLocked(user.getUsername())) {
            binding.rejectValue("currentPassword", "account.locked", "Account is temporarily locked. Try again later.");
            return false;
        }

        String currentPassword = form.getCurrentPassword() != null ? form.getCurrentPassword() : "";
        String newPassword = form.getNewPassword() != null ? form.getNewPassword() : "";
        String confirmPassword = form.getConfirmPassword() != null ? form.getConfirmPassword() : "";

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            loginAttemptService.loginFailed(user.getUsername());
            binding.rejectValue("currentPassword", "currentPassword.incorrect", "Current password is incorrect.");
        }

        if (newPassword.length() < 8 || newPassword.length() > 72) {
            if (!binding.hasFieldErrors("newPassword")) {
                binding.rejectValue("newPassword", "newPassword.length", "New password must be between 8 and 72 characters.");
            }
        } else if (passwordEncoder.matches(newPassword, user.getPassword())) {
            binding.rejectValue("newPassword", "newPassword.sameAsCurrent", "New password must differ from current password.");
        }

        if (!newPassword.equals(confirmPassword)) {
            if (!binding.hasFieldErrors("confirmPassword")) {
                binding.rejectValue("confirmPassword", "confirmPassword.mismatch", "New passwords do not match.");
            }
        }

        if (binding.hasErrors()) {
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        loginAttemptService.loginSucceeded(user.getUsername());
        return true;
    }
}
