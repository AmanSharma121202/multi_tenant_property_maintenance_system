package com.housing.billing.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TenantAuthDetails {
    private final String tenantId;
}