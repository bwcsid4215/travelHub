// src/main/java/com/bwc/authservice/controller/AuthController.java
package com.bwc.authservice.controller;

import com.bwc.authservice.dto.AuthRequest;
import com.bwc.authservice.dto.AuthResponse;
import com.bwc.authservice.dto.UserRegistrationDTO;
import com.bwc.authservice.entity.AuthUser;
import com.bwc.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest req) {
        AuthResponse resp = service.login(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUser> register(@RequestBody UserRegistrationDTO dto) {
        AuthUser user = service.register(dto);
        return ResponseEntity.ok(user);
    }
}
