package com.bwc.gateway.filter;

import com.bwc.gateway.security.JwtGatewayUtil;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtGatewayUtil jwtUtil;

    @Value("${gateway.internal.secret:bwc-secure-gateway}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // allow public endpoints (auth service etc.)
        if (auth == null || !auth.startsWith("Bearer ")) {
            System.out.println("⚪ [Gateway] No Bearer token, skipping filter for path: " + path);
            return chain.filter(exchange);
        }

        String token = auth.substring(7);
        try {
            JWTClaimsSet claims = jwtUtil.parseToken(token);

            String userId = claims.getSubject();
            List<String> roles = claims.getStringListClaim("roles");
            String email = claims.getStringClaim("email");

            // 🟢 DEBUG LOGS
            System.out.println("🟢 [Gateway] Path: " + path);
            System.out.println("🟢 [Gateway] Token Roles: " + roles);
            System.out.println("🟢 [Gateway] Email: " + email);
            System.out.println("🟢 [Gateway] Internal Secret Added: " + internalSecret);

            if (path.startsWith("/api/manager/") && (roles == null || !roles.contains("MANAGER"))) {
                System.out.println("🔴 [Gateway] Access denied for non-manager request");
                return chain.filter(exchange);
            }

            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.add("X-User-Id", userId);
                        if (email != null) headers.add("X-User-Email", email);
                        if (roles != null && !roles.isEmpty()) {
                            headers.add("X-User-Roles", String.join(",", roles));
                            System.out.println("🟩 [Gateway] Added Header X-User-Roles: " + String.join(",", roles));
                        }
                        headers.add("X-Internal-Gateway-Secret", internalSecret);
                    }))
                    .build();

            return chain.filter(mutated);

        } catch (Exception e) {
            System.out.println("❌ [Gateway] Token parse failed: " + e.getMessage());
            return chain.filter(exchange);
        }
    }


    @Override
    public int getOrder() {
        return -1; // run early
    }
}
