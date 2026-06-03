package com.housing.billing.controller;

import com.housing.billing.dto.request.LoginRequest;
import com.housing.billing.dto.request.SignupRequest;
import com.housing.billing.dto.response.SignupResponse;
import com.housing.billing.dto.response.TokenResponse;
import com.housing.billing.dto.response.UserResponse;
import com.housing.billing.model.User;
import com.housing.billing.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(201).body(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication auth) {
        return ResponseEntity.ok(authService.getMe(auth.getName()));
    }
}
