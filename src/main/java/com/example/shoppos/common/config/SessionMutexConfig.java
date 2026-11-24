package com.example.shoppos.common.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.HttpSessionMutexListener;

@Configuration
public class SessionMutexConfig {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionMutexListener> httpSessionMutexListener() {
        return new ServletListenerRegistrationBean<>(new HttpSessionMutexListener());
    }
}
