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
            // 🚫 Disable CSRF and sessions (stateless microservice)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ✅ Authorization rules
            .authorizeHttpRequests(auth -> auth
                // 🟢 Public and documentation endpoints
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/api-docs/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/swagger-resources/**",
                    "/management/**",
                    // ✅ Workflow initiation (from internal Kafka or services)
                    "/api/workflows/initiate",
                    "/api/workflows/initiate-with-travel-request"
                ).permitAll()

                // 🟣 Role-based access with INTERNAL override
                .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "INTERNAL")
                .requestMatchers("/api/finance/**").hasAnyRole("FINANCE", "INTERNAL")
                .requestMatchers("/api/hr/**").hasAnyRole("HR", "INTERNAL")
                .requestMatchers("/api/travel-desk/**").hasAnyRole("TRAVEL_DESK", "INTERNAL")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "INTERNAL")

                // 🟡 Internal Workflow Signals (Pre/Post Travel)
                .requestMatchers("/api/pre/**", "/api/post/**").hasAnyRole(
                    "MANAGER", "FINANCE", "HR", "TRAVEL_DESK", "INTERNAL"
                )

                // 🟢 Authenticated workflow service routes
                .requestMatchers("/api/workflows/**").authenticated()

                // 🔒 Any other request still requires authentication
                .anyRequest().authenticated()
            )

            // 🧱 Filter chain order: Gateway Secret → Auth Header → Rest
            .addFilterBefore(gatewaySecurityFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(gatewayAuthHeaderVerifier, GatewaySecurityFilter.class)

            // 🚫 Disable default form and basic auth
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}
