package com.housing.billing.service;

import com.housing.billing.dto.request.CreateTenantRequest;
import com.housing.billing.dto.request.UpdateTenantRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public List<Tenant> list() {
        return tenantRepository.findAllTenants();
    }

    public Tenant create(CreateTenantRequest req) {
        Tenant tenant = new Tenant();
        tenant.setId("tenant::" + UUID.randomUUID());
        tenant.setTenantId(tenant.getId());
        tenant.setName(req.getName());
        tenant.setCurrency(req.getCurrency());
        tenant.setBillingDay(req.getBillingDay());
        tenant.setLateFeeType(req.getLateFeeType());
        tenant.setLateFeeValue(req.getLateFeeValue());
        tenant.setAddress(req.getAddress());
        tenant.setType("tenant");
        tenant.setCreatedAt(Instant.now());
        return tenantRepository.save(tenant);
    }

    public Tenant get(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    public Tenant update(String tenantId, UpdateTenantRequest req) {
        Tenant tenant = get(tenantId);
        if (req.getName()         != null) tenant.setName(req.getName());
        if (req.getCurrency()     != null) tenant.setCurrency(req.getCurrency());
        if (req.getBillingDay()   != null) tenant.setBillingDay(req.getBillingDay());
        if (req.getLateFeeType()  != null) tenant.setLateFeeType(req.getLateFeeType());
        if (req.getLateFeeValue() != null) tenant.setLateFeeValue(req.getLateFeeValue());
        if (req.getAddress()      != null) tenant.setAddress(req.getAddress());
        tenant.setUpdatedAt(Instant.now());
        return tenantRepository.save(tenant);
    }
}