package com.example.shoppos.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;

    @Value("${app.admin.username}")
    private String username;

    @Value("${app.admin.password:${APP_ADMIN_PASSWORD:}}")
    private String password;

    @Value("${app.admin.display-name}")
    private String displayName;

    public AdminSeedRunner(UserRepository users, PasswordEncoder passwordEncoder, Environment environment) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByUsername(username)) {
            return;
        }

        boolean isDevOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod"));

        if (!isDevOrTest && (password == null || password.isBlank())) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be set");
        }
        if (isProd && "admin123".equals(password)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must not be the default value");
        }

        users.save(new User(username, passwordEncoder.encode(password), displayName, Role.ADMIN));
        log.info("Seeded admin user '{}'", username);
    }
}
