package com.housing.billing.dto.response;

import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String tenantId;
    private List<String> roles;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}