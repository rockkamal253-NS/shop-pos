package com.example.shoppos.user;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionExpiryIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private UserAdminService userAdminService;

    private final List<User> createdUsers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (User u : createdUsers) {
            users.deleteById(u.getId());
        }
        createdUsers.clear();
    }

    private User createTestUser(String usernamePrefix, Role role, String rawPassword) {
        String username = usernamePrefix + "_" + COUNTER.incrementAndGet();
        User user = users.save(new User(username, encoder.encode(rawPassword), "Test " + role.name(), role));
        createdUsers.add(user);
        return user;
    }

    @Test
    void disablingCashierExpiresExistingSession() throws Exception {
        String password = "KnownPassword123";
        User cashier = createTestUser("exp_cashier", Role.CASHIER, password);
        User admin = createTestUser("exp_admin", Role.ADMIN, password);

        MvcResult loginResult = mvc.perform(formLogin().user(cashier.getUsername()).password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/pos").session(session))
                .andExpect(status().isOk());

        userAdminService.setEnabled(admin.getId(), cashier.getId(), false);

        mvc.perform(get("/pos").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.containsString("/login")));
    }

    @Test
    void resetPasswordExpiresExistingSession() throws Exception {
        String password = "KnownPassword123";
        User cashier = createTestUser("exp_cashier", Role.CASHIER, password);
        User admin = createTestUser("exp_admin", Role.ADMIN, password);

        MvcResult loginResult = mvc.perform(formLogin().user(cashier.getUsername()).password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/pos").session(session))
                .andExpect(status().isOk());

        userAdminService.resetPassword(admin.getId(), cashier.getId(), "NewPassword123");

        mvc.perform(get("/pos").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.containsString("/login")));
    }

    @Test
    void disablingOneCashierDoesNotExpireAnotherCashiersSession() throws Exception {
        String password = "KnownPassword123";
        User cashier1 = createTestUser("exp_cashier1", Role.CASHIER, password);
        User cashier2 = createTestUser("exp_cashier2", Role.CASHIER, password);
        User admin = createTestUser("exp_admin", Role.ADMIN, password);

        MvcResult loginResult1 = mvc.perform(formLogin().user(cashier1.getUsername()).password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session1 = (MockHttpSession) loginResult1.getRequest().getSession(false);
        assertThat(session1).isNotNull();

        MvcResult loginResult2 = mvc.perform(formLogin().user(cashier2.getUsername()).password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session2 = (MockHttpSession) loginResult2.getRequest().getSession(false);
        assertThat(session2).isNotNull();

        mvc.perform(get("/pos").session(session1))
                .andExpect(status().isOk());
        mvc.perform(get("/pos").session(session2))
                .andExpect(status().isOk());

        userAdminService.setEnabled(admin.getId(), cashier1.getId(), false);

        mvc.perform(get("/pos").session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.containsString("/login")));

        mvc.perform(get("/pos").session(session2))
                .andExpect(status().isOk());
    }
}
