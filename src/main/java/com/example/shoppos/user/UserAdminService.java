package com.example.shoppos.user;

import com.example.shoppos.common.web.BusinessRuleException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class UserAdminService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final UserSessionService sessions;

    public UserAdminService(UserRepository users,
                            PasswordEncoder passwordEncoder,
                            LoginAttemptService loginAttemptService,
                            UserSessionService sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return users.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional
    public User createUser(String username, String displayName, String rawPassword, Role role) {
        String trimmedUsername = username != null ? username.trim() : "";
        if (!trimmedUsername.matches("^[a-z0-9._-]{3,50}$")) {
            throw new BusinessRuleException("Username must be 3-50 characters: lowercase letters, digits, dot, dash or underscore.");
        }
        if (users.existsByUsername(trimmedUsername)) {
            throw new BusinessRuleException("That username is already taken.");
        }
        String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        if (trimmedDisplayName.isEmpty()) {
            throw new BusinessRuleException("Display name is required.");
        }
        if (trimmedDisplayName.length() > 100) {
            throw new BusinessRuleException("Display name must not exceed 100 characters.");
        }
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 72) {
            throw new BusinessRuleException("Password must be between 8 and 72 characters.");
        }
        if (role == null) {
            throw new BusinessRuleException("Role is required.");
        }

        User user = new User(trimmedUsername, passwordEncoder.encode(rawPassword), trimmedDisplayName, role);
        return users.save(user);
    }

    @Transactional
    public void resetPassword(Long actingUserId, Long targetUserId, String newPassword) {
        if (targetUserId == null) {
            throw new BusinessRuleException("User not found.");
        }
        User target = users.findById(targetUserId)
                .orElseThrow(() -> new BusinessRuleException("User not found."));

        if (actingUserId != null && actingUserId.equals(targetUserId)) {
            throw new BusinessRuleException("Use Change password for your own account.");
        }

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new BusinessRuleException("Password must be between 8 and 72 characters.");
        }

        target.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(target);
        loginAttemptService.loginSucceeded(target.getUsername());
        sessions.expireSessionsAfterCommit(target.getUsername());
    }

    @Transactional
    public void setEnabled(Long actingUserId, Long targetUserId, boolean enabled) {
        if (targetUserId == null) {
            throw new BusinessRuleException("User not found.");
        }
        User target = users.findById(targetUserId)
                .orElseThrow(() -> new BusinessRuleException("User not found."));

        if (actingUserId != null && actingUserId.equals(targetUserId)) {
            throw new BusinessRuleException("You cannot disable your own account.");
        }

        if (!enabled && target.getRole() == Role.ADMIN) {
            long otherEnabledAdmins = users.findByRoleOrderByIdAsc(Role.ADMIN).stream()
                    .filter(u -> !isSameUser(u, target))
                    .filter(User::isEnabled)
                    .count();
            if (otherEnabledAdmins < 1) {
                throw new BusinessRuleException("At least one enabled admin is required.");
            }
        }

        target.setEnabled(enabled);
        users.save(target);
        if (!enabled) {
            sessions.expireSessionsAfterCommit(target.getUsername());
        }
    }

    private boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        if (a.getId() != null && b.getId() != null) {
            return Objects.equals(a.getId(), b.getId());
        }
        return Objects.equals(a.getUsername(), b.getUsername());
    }
}
