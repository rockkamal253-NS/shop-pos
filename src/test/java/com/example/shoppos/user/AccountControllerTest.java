package com.example.shoppos.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Runs the real security filter chain, so these tests check who may reach
 * the change-password page and that the form is protected by CSRF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerTest {

    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass4567";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private SessionRegistry sessionRegistry;

    private User cashier;

    @BeforeEach
    void createCashier() {
        String name = "acct_cashier_" + COUNTER.incrementAndGet();
        cashier = users.save(new User(name, encoder.encode(OLD_PASSWORD), "Account Cashier", Role.CASHIER));
    }

    private MockHttpServletRequestBuilder submitAsCashier(String current, String next, String confirm) {
        return post("/account/password")
                .with(user(new UserPrincipal(cashier)))
                .with(csrf())
                .param("currentPassword", current)
                .param("newPassword", next)
                .param("confirmPassword", confirm);
    }

    private boolean storedHashMatches(String rawPassword) {
        users.flush();
        String hash = users.findById(cashier.getId()).orElseThrow().getPassword();
        return encoder.matches(rawPassword, hash);
    }

    @Test
    void anonymousVisitorIsSentToLogin() throws Exception {
        mvc.perform(get("/account/password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void cashierCanOpenThePage() throws Exception {
        mvc.perform(get("/account/password").with(user(new UserPrincipal(cashier))))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/account/password")
                        .with(user(new UserPrincipal(cashier)))
                        .param("currentPassword", OLD_PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isForbidden());
        assertTrue(storedHashMatches(OLD_PASSWORD));
    }

    @Test
    void validChangeUpdatesTheStoredHash() throws Exception {
        mvc.perform(submitAsCashier(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("toastSuccess", "Password changed."));
        assertTrue(storedHashMatches(NEW_PASSWORD));
    }

    @Test
    void wrongCurrentPasswordLeavesTheHashAlone() throws Exception {
        mvc.perform(submitAsCashier("not-the-password", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"))
                .andExpect(model().attributeHasFieldErrors("form", "currentPassword"));
        assertTrue(storedHashMatches(OLD_PASSWORD));
    }

    @Test
    void mismatchedConfirmationLeavesTheHashAlone() throws Exception {
        mvc.perform(submitAsCashier(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
        assertTrue(storedHashMatches(OLD_PASSWORD));
    }

    @Test
    void validChangeExpiresOtherSessionsAndKeepsCurrentSession() throws Exception {
        UserPrincipal principal = new UserPrincipal(cashier);
        MockHttpSession keptSession = new MockHttpSession();
        String otherSessionId = "other-" + cashier.getUsername();
        sessionRegistry.registerNewSession(keptSession.getId(), principal);
        sessionRegistry.registerNewSession(otherSessionId, principal);

        mvc.perform(submitAsCashier(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).session(keptSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("toastSuccess", "Password changed."));

        assertFalse(sessionRegistry.getSessionInformation(keptSession.getId()).isExpired());
        assertTrue(sessionRegistry.getSessionInformation(otherSessionId).isExpired());
    }

    @Test
    void wrongCurrentPasswordDoesNotExpireAnySession() throws Exception {
        UserPrincipal principal = new UserPrincipal(cashier);
        MockHttpSession keptSession = new MockHttpSession();
        String otherSessionId = "other-wrong-" + cashier.getUsername();
        sessionRegistry.registerNewSession(keptSession.getId(), principal);
        sessionRegistry.registerNewSession(otherSessionId, principal);

        mvc.perform(submitAsCashier("not-the-password", NEW_PASSWORD, NEW_PASSWORD).session(keptSession))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"))
                .andExpect(model().attributeHasFieldErrors("form", "currentPassword"));

        assertFalse(sessionRegistry.getSessionInformation(keptSession.getId()).isExpired());
        assertFalse(sessionRegistry.getSessionInformation(otherSessionId).isExpired());
    }
}
