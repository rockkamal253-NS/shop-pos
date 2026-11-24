package com.example.shoppos.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void nullReturnsLiteralNull() {
        assertThat(LogSanitizer.safe(null)).isEqualTo("null");
    }

    @Test
    void adminUnchanged() {
        assertThat(LogSanitizer.safe("admin")).isEqualTo("admin");
    }

    @Test
    void badNameHasNoNewlineAndNoSpace() {
        String sanitized = LogSanitizer.safe("bad\nname x");
        assertThat(sanitized).doesNotContain("\n").doesNotContain(" ");
        assertThat(sanitized).isEqualTo("bad_name_x");
    }

    @Test
    void hundredCharacterStringCutTo64() {
        String hundred = "a".repeat(100);
        String sanitized = LogSanitizer.safe(hundred);
        assertThat(sanitized).hasSize(64);
        assertThat(sanitized).isEqualTo("a".repeat(64));
    }

    @Test
    void allowedCharactersUnchanged() {
        assertThat(LogSanitizer.safe("a.b_c-d@e")).isEqualTo("a.b_c-d@e");
    }
}
