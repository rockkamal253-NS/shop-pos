package com.example.shoppos.common.web;

import com.example.shoppos.sale.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorPagesTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaleService saleService;

    @Test
    void missingStaticResourceRendersStyled404Page() throws Exception {
        mvc.perform(get("/no-such-file.png").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("Page not found")));
    }

    @Test
    void unexpectedExceptionRendersStyled500Page() throws Exception {
        when(saleService.search(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        mvc.perform(get("/sales").with(user("admin").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"))
                .andExpect(content().string(containsString("An unexpected error occurred. The problem has been logged.")));
    }
}
