package com.housing.billing.service;

import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantInactiveException;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantStatusService {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    private final TenantRepository tenantRepository;

    public void requireActive(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (!isActive(tenant)) {
            throw new TenantInactiveException("Tenant is no longer active");
        }
    }

    public static boolean isActive(Tenant tenant) {
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus().isBlank()) {
            return true;
        }
        return ACTIVE.equalsIgnoreCase(tenant.getStatus());
    }
}
