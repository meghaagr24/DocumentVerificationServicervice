package com.mb.ocrservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Disable CSRF for API endpoints
        http.csrf(csrf -> csrf.disable());
        
        // Allow all requests without authentication
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        
        return http.build();
    }
}