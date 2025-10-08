package com.bwc.employee_management_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Allow CORS for all endpoints including SpringDoc
                registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
                
                // Specifically allow CORS for SpringDoc endpoints
                registry.addMapping("/v3/api-docs/**")
                    .allowedOrigins("*") // Allow all for API docs
                    .allowedMethods("GET")
                    .allowedHeaders("*")
                    .maxAge(3600);
                    
                registry.addMapping("/swagger-ui/**")
                    .allowedOrigins("*") // Allow all for Swagger UI resources
                    .allowedMethods("GET")
                    .allowedHeaders("*")
                    .maxAge(3600);
            }
        };
    }
}