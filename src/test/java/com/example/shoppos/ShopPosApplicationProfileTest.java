package com.example.shoppos;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ShopPosApplicationProfileTest {

    @Test
    void noActiveProfile_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        assertThatThrownBy(() -> ShopPosApplication.requireKnownProfile(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }

    @Test
    void devProfile_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertDoesNotThrow(() -> ShopPosApplication.requireKnownProfile(env));
    }

    @Test
    void testProfile_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        assertDoesNotThrow(() -> ShopPosApplication.requireKnownProfile(env));
    }

    @Test
    void prodProfile_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertDoesNotThrow(() -> ShopPosApplication.requireKnownProfile(env));
    }

    @Test
    void unknownProfile_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        assertThatThrownBy(() -> ShopPosApplication.requireKnownProfile(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }
}
