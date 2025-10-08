package com.bwc.approval_workflow_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final GatewaySecurityFilter gatewaySecurityFilter;
    private final GatewayAuthHeaderVerifier gatewayAuthHeaderVerifier;

    public SecurityConfig(GatewaySecurityFilter gatewaySecurityFilter,
                          GatewayAuthHeaderVerifier gatewayAuthHeaderVerifier) {
        this.gatewaySecurityFilter = gatewaySecurityFilter;
        this.gatewayAuthHeaderVerifier = gatewayAuthHeaderVerifier;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 🟩 MANAGER endpoints are role protected
                .requestMatchers("/api/manager/**").hasRole("MANAGER")
                // 🟩 Everything else allowed for testing
                .anyRequest().permitAll()
            )
            .addFilterBefore(gatewaySecurityFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(gatewayAuthHeaderVerifier, GatewaySecurityFilter.class)
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}
