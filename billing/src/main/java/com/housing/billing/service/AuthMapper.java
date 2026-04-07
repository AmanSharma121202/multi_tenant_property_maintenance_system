package com.housing.billing.service;

import com.housing.billing.dto.request.SignupRequest;
import com.housing.billing.dto.response.UserResponse;
import com.housing.billing.model.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuthMapper {

    public User toNewUser(SignupRequest request, String encodedPassword) {
        User user = new User();
        user.setId("user::" + UUID.randomUUID());
        user.setEmail(request.getEmail().trim());
        user.setName(request.getName().trim());
        user.setPasswordHash(encodedPassword);
        user.setRoles(request.getRoles());
        user.setTenantId(request.getTenantId().trim());
        user.setActive(true);
        user.setType("user");
        user.setCreatedAt(Instant.now());
        return user;
    }

    public UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setTenantId(user.getTenantId());
        response.setRoles(user.getRoles());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}

