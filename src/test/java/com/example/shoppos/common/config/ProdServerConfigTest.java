package com.example.shoppos.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProdServerConfigTest {

    @Test
    void prodServerConfigurationKeysArePresent() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties props = yaml.getObject();
        assertThat(props).isNotNull();

        assertThat(props.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(props.getProperty("server.servlet.session.timeout")).isEqualTo("30m");
        assertThat(props.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(props.getProperty("server.servlet.session.cookie.secure")).isEqualTo("${SESSION_COOKIE_SECURE:true}");
        assertThat(props.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(props.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(props.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(props.getProperty("server.error.include-binding-errors")).isEqualTo("never");

        assertThat(props.getProperty("spring.datasource.url")).isNotBlank();
    }
}
