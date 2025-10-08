package com.bwc.approval_workflow_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(2)
public class GatewayAuthHeaderVerifier extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String rolesHeader = request.getHeader("X-User-Roles");
        String userId = request.getHeader("X-User-Id");

        // 🟢 DEBUG LOGS
        System.out.println("🟩 [Workflow] Received X-User-Id: " + userId);
        System.out.println("🟩 [Workflow] Received X-User-Roles: " + rolesHeader);

        if (userId != null && rolesHeader != null) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(auth);
            System.out.println("✅ [Workflow] Security context set with authorities: " + authorities);
        } else {
            System.out.println("⚠️ [Workflow] Missing userId or roles header — no authentication set.");
        }

        filterChain.doFilter(request, response);
    }
}
