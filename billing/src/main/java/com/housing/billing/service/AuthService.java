package com.housing.billing.service;

import com.housing.billing.dto.request.*;
import com.housing.billing.dto.response.*;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.User;
import com.housing.billing.repository.UserRepository;
import com.housing.billing.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final JwtUtil         jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public UserResponse signup(SignupRequest req) {
        userRepository.findByEmail(req.getEmail()).ifPresent(u -> {
            throw new RuntimeException("Email already registered");
        });
        User user = new User();
        user.setId("user::" + UUID.randomUUID());
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRoles(req.getRoles());
        user.setTenantId(req.getTenantId());
        user.setActive(true);
        user.setType("user");
        user.setCreatedAt(Instant.now());
        return toResponse(userRepository.save(user));
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(
                user.getEmail(), user.getTenantId(), user.getRoles());

        return new TokenResponse(token, "Bearer", 86400000L);
    }

    public UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setTenantId(user.getTenantId());
        res.setRoles(user.getRoles());
        res.setActive(user.isActive());
        res.setCreatedAt(user.getCreatedAt());
        res.setUpdatedAt(user.getUpdatedAt());
        return res;
    }
}
