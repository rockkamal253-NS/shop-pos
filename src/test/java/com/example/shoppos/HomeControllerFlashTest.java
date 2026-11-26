package com.example.shoppos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeControllerFlashTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void cashierRedirectPreservesFlashAttributes() throws Exception {
        mvc.perform(get("/")
                        .with(user("cashier").roles("CASHIER"))
                        .flashAttr("toastSuccess", "Done"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pos"))
                .andExpect(flash().attribute("toastSuccess", "Done"));
    }

    @Test
    void adminRedirectPreservesFlashAttributes() throws Exception {
        mvc.perform(get("/")
                        .with(user("admin").roles("ADMIN"))
                        .flashAttr("toastSuccess", "Done"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(flash().attribute("toastSuccess", "Done"));
    }

    @Test
    void redirectsWithoutFlashAttributes() throws Exception {
        mvc.perform(get("/").with(user("cashier").roles("CASHIER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pos"));

        mvc.perform(get("/").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }
}
