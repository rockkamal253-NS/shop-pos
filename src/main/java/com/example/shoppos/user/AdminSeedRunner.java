package com.example.shoppos.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the admin account from application properties (never hardcoded credentials).
 * Only creates the account when the username does not exist yet, so the password
 * can be changed in the database without this runner overwriting it.
 */
@Component
@org.springframework.core.annotation.Order(1)
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String username;

    @Value("${app.admin.password}")
    private String password;

    @Value("${app.admin.display-name}")
    private String displayName;

    public AdminSeedRunner(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new User(username, passwordEncoder.encode(password), displayName, Role.ADMIN));
        log.info("Seeded admin user '{}'", username);
    }
}
