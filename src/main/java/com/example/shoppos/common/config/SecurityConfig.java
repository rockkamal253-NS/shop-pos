package com.example.shoppos.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock(@Value("${app.timezone:Africa/Cairo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/vendor/**", "/fonts/**", "/login", "/error").permitAll()
                        // Cashiers run the register and can look at the product list only.
                        .requestMatchers("/pos/**").hasAnyRole("ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.GET, "/products").hasAnyRole("ADMIN", "CASHIER")
                        .requestMatchers("/sales/*/receipt").hasAnyRole("ADMIN", "CASHIER")
                        // Everything else is admin territory.
                        .requestMatchers("/products/**", "/categories/**", "/inventory/**", "/sales/**", "/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(s -> s
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }
}
