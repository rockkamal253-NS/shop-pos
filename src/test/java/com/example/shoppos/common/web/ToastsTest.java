package com.example.shoppos.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void arabicMessageProducesOnlyAsciiHeaderAndSurvivesJsonParse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String arabicMessage = "تمت إضافة قهوة إلى السلة";

        Toasts.send(response, arabicMessage, "info");

        String header = response.getHeader("HX-Trigger");
        assertNotNull(header);

        // Header must contain strictly ASCII characters
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            assertTrue(c <= 127, "Header contains non-ASCII character at index " + i + ": " + c);
        }

        // Verify JSON parse recovers the exact Arabic message
        JsonNode root = objectMapper.readTree(header);
        assertEquals(arabicMessage, root.path("toast").path("message").asText());
        assertEquals("info", root.path("toast").path("type").asText());
    }

    @Test
    void quotesAndControlCharactersAreEscaped() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String complexMessage = "Line 1\nLine 2\r\twith \"quotes\" and \\backslash\\";

        Toasts.send(response, complexMessage, "error");

        String header = response.getHeader("HX-Trigger");
        assertNotNull(header);

        // Header must contain escaped newline, quote, tab, etc. and remain strictly ASCII
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            assertTrue(c >= 0x20 && c <= 127, "Header contains unescaped or non-printable character: " + (int) c);
        }

        // Verify JSON parse recovers exact text
        JsonNode root = objectMapper.readTree(header);
        assertEquals(complexMessage, root.path("toast").path("message").asText());
        assertEquals("error", root.path("toast").path("type").asText());
    }
}
