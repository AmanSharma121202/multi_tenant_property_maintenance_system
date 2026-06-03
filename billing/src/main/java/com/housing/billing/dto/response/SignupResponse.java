package com.housing.billing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {
    private UserResponse user;
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
}
