package com.housing.billing.service;

import com.housing.billing.dto.request.*;
import com.housing.billing.dto.response.*;
import com.housing.billing.exception.AuthenticationFailedException;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.User;
import com.housing.billing.repository.UserRepository;
import com.housing.billing.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final JwtUtil         jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final ModelValidationService modelValidationService;
    private final TenantStatusService tenantStatusService;

    public SignupResponse signup(SignupRequest req) {
        String normalizedEmail = req.getEmail().trim();
        userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
            throw new IllegalStateException("Email already registered");
        });

        String tenantId = req.getTenantId().trim();
        if (!"superadmin".equals(tenantId)) {
            tenantStatusService.requireActive(tenantId);
        }

        User user = authMapper.toNewUser(req, passwordEncoder.encode(req.getPassword()));
        modelValidationService.validate(user);

        User saved = userRepository.save(user);
        return new SignupResponse(
                authMapper.toUserResponse(saved),
                issueToken(saved),
                "Bearer",
                86400000L);
    }

    public TokenResponse login(LoginRequest req) {
        User user = authenticate(req.getEmail().trim(), req.getPassword());
        return new TokenResponse(issueToken(user), "Bearer", 86400000L);
    }

    private User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }
        if (!"superadmin".equals(user.getTenantId())) {
            tenantStatusService.requireActive(user.getTenantId());
        }
        return user;
    }

    private String issueToken(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getTenantId(), user.getRoles());
    }

    public UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return authMapper.toUserResponse(user);
    }
}
