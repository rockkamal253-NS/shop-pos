package com.example.shoppos.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeedRunnerTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        when(users.existsByUsername("admin")).thenReturn(false);
    }

    private AdminSeedRunner createRunner(MockEnvironment env, String password) {
        AdminSeedRunner runner = new AdminSeedRunner(users, passwordEncoder, env);
        ReflectionTestUtils.setField(runner, "username", "admin");
        ReflectionTestUtils.setField(runner, "password", password);
        ReflectionTestUtils.setField(runner, "displayName", "Store Admin");
        return runner;
    }

    @Test
    void prodProfileWithBlankPasswordThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        AdminSeedRunner runner = createRunner(env, "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));
        assertEquals("APP_ADMIN_PASSWORD must be set", ex.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void prodProfileWithNullPasswordThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        AdminSeedRunner runner = createRunner(env, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));
        assertEquals("APP_ADMIN_PASSWORD must be set", ex.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void prodProfileWithDefaultPasswordThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        AdminSeedRunner runner = createRunner(env, "admin123");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments()));
        assertEquals("APP_ADMIN_PASSWORD must not be the default value", ex.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void prodProfileWithStrongPasswordSavesUser() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        when(passwordEncoder.encode("StrongP@ssw0rd!")).thenReturn("encodedHash");

        AdminSeedRunner runner = createRunner(env, "StrongP@ssw0rd!");
        runner.run(new DefaultApplicationArguments());

        verify(users).save(any(User.class));
    }

    @Test
    void devProfileWithDefaultPasswordSavesUser() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        when(passwordEncoder.encode("admin123")).thenReturn("encodedHash");

        AdminSeedRunner runner = createRunner(env, "admin123");
        runner.run(new DefaultApplicationArguments());

        verify(users).save(any(User.class));
    }

    @Test
    void existingAdminSkipsSeeding() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        when(users.existsByUsername("admin")).thenReturn(true);

        AdminSeedRunner runner = createRunner(env, "");
        runner.run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
    }
}
