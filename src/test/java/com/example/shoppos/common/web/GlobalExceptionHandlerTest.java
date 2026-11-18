package com.example.shoppos.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ExceptionTriggeringController {

        @GetMapping("/unexpected")
        public void throwUnexpected() {
            throw new RuntimeException("Unexpected crash");
        }

        @GetMapping("/data-integrity")
        public void throwDataIntegrity() {
            throw new DataIntegrityViolationException("foreign key violation");
        }

        @GetMapping("/business-rule")
        public void throwBusinessRule() {
            throw new BusinessRuleException("Rule broken");
        }

        @GetMapping("/optimistic-lock")
        public void throwOptimisticLock() {
            throw new OptimisticLockingFailureException("concurrent edit");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ExceptionTriggeringController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handleUnexpectedHasResponseStatus500Annotation() throws Exception {
        Method method = GlobalExceptionHandler.class.getMethod("handleUnexpected", Exception.class);
        ResponseStatus annotation = method.getAnnotation(ResponseStatus.class);
        assertNotNull(annotation, "handleUnexpected must be annotated with @ResponseStatus");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, annotation.value());
    }

    @Test
    void handleUnexpectedReturnsStatus500() throws Exception {
        mvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void handleDataIntegrityHtmxReturns200WithNoSwapAndToast() throws Exception {
        mvc.perform(get("/data-integrity").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Reswap", "none"))
                .andExpect(header().string("HX-Trigger", containsString("This record is referenced by other data")))
                .andExpect(content().string(""));
    }

    @Test
    void handleDataIntegrityNonHtmxRedirectsWithFlash() throws Exception {
        mvc.perform(get("/data-integrity"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("toastError", "This record is referenced by other data and cannot be changed or removed."));
    }

    @Test
    void handleBusinessRuleHtmxReturns200WithNoSwapAndToast() throws Exception {
        mvc.perform(get("/business-rule").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Reswap", "none"))
                .andExpect(header().string("HX-Trigger", containsString("Rule broken")))
                .andExpect(content().string(""));
    }

    @Test
    void handleBusinessRuleNonHtmxRedirectsWithFlash() throws Exception {
        mvc.perform(get("/business-rule"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("toastError", "Rule broken"));
    }

    @Test
    void handleOptimisticLockHtmxReturns200WithNoSwapAndToast() throws Exception {
        mvc.perform(get("/optimistic-lock").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Reswap", "none"))
                .andExpect(header().string("HX-Trigger", containsString("Someone else updated this record just now")))
                .andExpect(content().string(""));
    }

    @Test
    void handleOptimisticLockNonHtmxRedirectsWithFlash() throws Exception {
        mvc.perform(get("/optimistic-lock"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("toastError", "Someone else updated this record just now. Please try again."));
    }
}
