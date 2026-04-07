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

    public UserResponse signup(SignupRequest req) {
        String normalizedEmail = req.getEmail().trim();
        userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
            throw new IllegalStateException("Email already registered");
        });

        User user = authMapper.toNewUser(req, passwordEncoder.encode(req.getPassword()));
        modelValidationService.validate(user);

        return authMapper.toUserResponse(userRepository.save(user));
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail().trim())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(
                user.getEmail(), user.getTenantId(), user.getRoles());

        return new TokenResponse(token, "Bearer", 86400000L);
    }

    public UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return authMapper.toUserResponse(user);
    }
}
