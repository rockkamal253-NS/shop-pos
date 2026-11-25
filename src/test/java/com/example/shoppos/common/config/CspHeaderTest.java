package com.example.shoppos.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CspHeaderTest {

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC_ATTR = Pattern.compile("\\b(?:th:)?src\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_HANDLER = Pattern.compile(
            "\\b(onclick|onsubmit|onchange|oninput|onload|onkeydown|onkeyup|onerror)\\s*=",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URI = Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);

    @Autowired
    private MockMvc mvc;

    @Test
    void loginPageIncludesStrictCspHeader() throws Exception {
        MvcResult result = mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        assertStrictCsp(result.getResponse().getHeader("Content-Security-Policy"));
    }

    @Test
    void authenticatedPosPageIncludesStrictCspHeader() throws Exception {
        MvcResult result = mvc.perform(get("/pos").with(user("cashier1").roles("CASHIER")))
                .andExpect(status().isOk())
                .andReturn();

        assertStrictCsp(result.getResponse().getHeader("Content-Security-Policy"));
    }

    @Test
    void templatesHaveNoInlineScriptsOrEventHandlers() throws IOException {
        Path root = Path.of("src/main/resources/templates");
        List<Path> templates;
        try (Stream<Path> walk = Files.walk(root)) {
            templates = walk.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
        assertFalse(templates.isEmpty(), "Expected HTML templates under " + root);

        for (Path file : templates) {
            String content = HTML_COMMENT.matcher(Files.readString(file)).replaceAll("");

            Matcher scriptMatcher = SCRIPT_TAG.matcher(content);
            while (scriptMatcher.find()) {
                String tag = scriptMatcher.group();
                assertTrue(SRC_ATTR.matcher(tag).find(),
                        "Inline <script> without src= found in " + file + ": " + tag);
            }

            Matcher handlerMatcher = INLINE_HANDLER.matcher(content);
            assertFalse(handlerMatcher.find(),
                    () -> "Inline event handler '" + handlerMatcher.group() + "' found in " + file);

            assertFalse(JAVASCRIPT_URI.matcher(content).find(),
                    () -> "javascript: URI found in " + file);
        }
    }

    private static void assertStrictCsp(String header) {
        assertNotNull(header, "Content-Security-Policy header must be present");
        assertTrue(header.contains("script-src 'self'"), () -> "Missing script-src 'self' in: " + header);
        assertTrue(header.contains("object-src 'none'"), () -> "Missing object-src 'none' in: " + header);
        assertTrue(header.contains("frame-ancestors 'none'"), () -> "Missing frame-ancestors 'none' in: " + header);
        assertFalse(header.contains("unsafe-eval"), () -> "Unexpected unsafe-eval in: " + header);

        String scriptSrc = extractDirective(header, "script-src");
        assertNotNull(scriptSrc, "script-src directive must be present");
        assertFalse(scriptSrc.contains("unsafe-inline"),
                () -> "script-src must not allow unsafe-inline: " + scriptSrc);
    }

    private static String extractDirective(String header, String name) {
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            if (trimmed.equals(name) || trimmed.startsWith(name + " ")) {
                return trimmed;
            }
        }
        return null;
    }
}
