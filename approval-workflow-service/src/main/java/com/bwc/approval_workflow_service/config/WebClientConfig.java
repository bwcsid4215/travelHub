package com.bwc.approval_workflow_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${services.tasklist.url}")
    private String tasklistUrl;

    @Value("${services.tasklist.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${services.tasklist.read-timeout:30000}")
    private int readTimeout;

    @Value("${services.tasklist.username:demo}")
    private String username;

    @Value("${services.tasklist.password:demo}")
    private String password;

    @Value("${services.tasklist.auth-enabled:false}")
    private boolean authEnabled;

    @Bean
    public WebClient tasklistClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(readTimeout));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(tasklistUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .filter(logResponse());

        // Add authentication if enabled
        if (authEnabled) {
            builder.filter(authenticationFilter());
        }

        return builder.build();
    }

    /**
     * Filter to add authentication cookie or basic auth
     */
    private ExchangeFilterFunction authenticationFilter() {
        return (request, next) -> {
            // For self-managed Tasklist, you might need to:
            // 1. Use cookie-based authentication
            // 2. Or use basic authentication
            // 3. Or use JWT tokens
            
            // Option 1: Basic Authentication (if your Tasklist supports it)
            // String auth = username + ":" + password;
            // String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            // request = request.mutate()
            //         .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
            //         .build();
            
            // Option 2: For cookie-based auth, you'd first need to login
            // and store the cookie, then add it to each request
            
            return next.exchange(request);
        };
    }

    /**
     * Log request details
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            if (log.isDebugEnabled()) {
                log.debug("🌐 WebClient Request: {} {}", request.method(), request.url());
                request.headers().forEach((name, values) -> 
                    log.debug("   Header: {}={}", name, values));
            }
            return Mono.just(request);
        });
    }

    /**
     * Log response details
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (log.isDebugEnabled()) {
                log.debug("📥 WebClient Response: Status {}", response.statusCode());
            }
            if (response.statusCode().isError()) {
                log.error("❌ WebClient Error Response: Status {}", response.statusCode());
            }
            return Mono.just(response);
        });
    }
}