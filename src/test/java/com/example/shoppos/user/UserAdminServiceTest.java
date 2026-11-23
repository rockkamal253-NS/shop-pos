package com.example.shoppos.user;

import com.example.shoppos.common.web.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneId.of("UTC"));
        loginAttemptService = new LoginAttemptService(clock, 5, 10);
        userAdminService = new UserAdminService(userRepository, passwordEncoder, loginAttemptService);
    }

    private User createUser(Long id, String username, String rawPassword, String displayName, Role role, boolean enabled) {
        User user = new User(username, passwordEncoder.encode(rawPassword), displayName, role);
        user.setEnabled(enabled);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void createUser_validCashierSavedWithMatchingBcryptHash() {
        when(userRepository.existsByUsername("cashier1")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = userAdminService.createUser("cashier1", "Cashier One", "password123", Role.CASHIER);

        assertThat(created.getUsername()).isEqualTo("cashier1");
        assertThat(created.getDisplayName()).isEqualTo("Cashier One");
        assertThat(created.getRole()).isEqualTo(Role.CASHIER);
        assertThat(created.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("password123", created.getPassword())).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_duplicateUsernameRejected() {
        when(userRepository.existsByUsername("cashier1")).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("cashier1", "Cashier One", "password123", Role.CASHIER));

        assertThat(ex.getMessage()).isEqualTo("That username is already taken.");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_usernameTooShortUppercaseAndWithSpaceRejected() {
        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("ab", "Cashier", "password123", Role.CASHIER));

        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("CASHIER", "Cashier", "password123", Role.CASHIER));

        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("cashier 1", "Cashier", "password123", Role.CASHIER));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_sevenCharAndSeventyThreeCharPasswordsRejected() {
        when(userRepository.existsByUsername("cashier1")).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("cashier1", "Cashier", "1234567", Role.CASHIER));

        String pass73 = "a".repeat(73);
        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("cashier1", "Cashier", pass73, Role.CASHIER));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_blankDisplayNameRejected() {
        when(userRepository.existsByUsername("cashier1")).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("cashier1", "   ", "password123", Role.CASHIER));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_nullRoleRejected() {
        when(userRepository.existsByUsername("cashier1")).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> userAdminService.createUser("cashier1", "Cashier", "password123", null));

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_successChangesHashAndNewPasswordMatches() {
        User target = createUser(2L, "cashier1", "oldPassword123", "Cashier One", Role.CASHIER, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userAdminService.resetPassword(1L, 2L, "newPassword123");

        assertThat(passwordEncoder.matches("newPassword123", target.getPassword())).isTrue();
        verify(userRepository).save(target);
    }

    @Test
    void resetPassword_unknownTargetRejected() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> userAdminService.resetPassword(1L, 99L, "newPassword123"));

        assertThat(ex.getMessage()).isEqualTo("User not found.");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_targetEqualsActingUserRejectedAndHashUnchanged() {
        User target = createUser(1L, "admin", "currentPassword123", "Admin User", Role.ADMIN, true);
        String originalHash = target.getPassword();
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> userAdminService.resetPassword(1L, 1L, "newPassword123"));

        assertThat(ex.getMessage()).isEqualTo("Use Change password for your own account.");
        assertThat(target.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_sevenCharPasswordRejected() {
        User target = createUser(2L, "cashier1", "oldPassword123", "Cashier One", Role.CASHIER, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThrows(BusinessRuleException.class,
                () -> userAdminService.resetPassword(1L, 2L, "1234567"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_lockedTargetUnlockedAfterReset() {
        User target = createUser(2L, "cashier1", "oldPassword123", "Cashier One", Role.CASHIER, true);
        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFailed("cashier1");
        }
        assertThat(loginAttemptService.isLocked("cashier1")).isTrue();

        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        userAdminService.resetPassword(1L, 2L, "newPassword123");

        assertThat(loginAttemptService.isLocked("cashier1")).isFalse();
        verify(userRepository).save(target);
    }

    @Test
    void setEnabled_disablingCashierWorks() {
        User cashier = createUser(2L, "cashier1", "password123", "Cashier One", Role.CASHIER, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(cashier));

        userAdminService.setEnabled(1L, 2L, false);

        assertThat(cashier.isEnabled()).isFalse();
        verify(userRepository).save(cashier);
    }

    @Test
    void setEnabled_disablingYourselfRejected() {
        User admin = createUser(1L, "admin", "password123", "Admin User", Role.ADMIN, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> userAdminService.setEnabled(1L, 1L, false));

        assertThat(ex.getMessage()).isEqualTo("You cannot disable your own account.");
        assertThat(admin.isEnabled()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_disablingOnlyEnabledAdminRejected() {
        User targetAdmin = createUser(2L, "admin2", "password123", "Admin Two", Role.ADMIN, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findByRoleOrderByIdAsc(Role.ADMIN)).thenReturn(List.of(targetAdmin));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> userAdminService.setEnabled(1L, 2L, false));

        assertThat(ex.getMessage()).isEqualTo("At least one enabled admin is required.");
        assertThat(targetAdmin.isEnabled()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_disablingAdminWhenAnotherEnabledAdminExistsWorks() {
        User otherAdmin = createUser(1L, "admin1", "password123", "Admin One", Role.ADMIN, true);
        User targetAdmin = createUser(2L, "admin2", "password123", "Admin Two", Role.ADMIN, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findByRoleOrderByIdAsc(Role.ADMIN)).thenReturn(List.of(otherAdmin, targetAdmin));

        userAdminService.setEnabled(1L, 2L, false);

        assertThat(targetAdmin.isEnabled()).isFalse();
        verify(userRepository).save(targetAdmin);
    }

    @Test
    void setEnabled_reEnablingWorks() {
        User targetCashier = createUser(2L, "cashier1", "password123", "Cashier One", Role.CASHIER, false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetCashier));

        userAdminService.setEnabled(1L, 2L, true);

        assertThat(targetCashier.isEnabled()).isTrue();
        verify(userRepository).save(targetCashier);
    }

    @Test
    void listUsers_returnsRepositoryResultOrderedById() {
        User u1 = createUser(1L, "admin", "password123", "Admin", Role.ADMIN, true);
        User u2 = createUser(2L, "cashier1", "password123", "Cashier", Role.CASHIER, true);
        when(userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(u1, u2));

        List<User> result = userAdminService.listUsers();

        assertThat(result).containsExactly(u1, u2);
        verify(userRepository).findAll(Sort.by(Sort.Direction.ASC, "id"));
    }
}
