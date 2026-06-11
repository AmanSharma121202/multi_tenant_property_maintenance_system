package com.housing.billing.service;

import com.housing.billing.dto.request.CreateTenantRequest;
import com.housing.billing.dto.request.UpdateTenantRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.InvoiceRepository;
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
    private final InvoiceRepository invoiceRepository;
    private final ModelValidationService modelValidationService;


    public List<Tenant> list() {
        return tenantRepository.findAllTenants();
    }

    public Tenant create(CreateTenantRequest req) {
        String normalizedName = req.getName().trim();
        tenantRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
            if (TenantStatusService.isActive(existing)) {
                throw new IllegalStateException("Tenant already exists");
            }
        });

        Tenant tenant = new Tenant();
        tenant.setId("tenant::" + UUID.randomUUID());
        tenant.setTenantId(tenant.getId());
        tenant.setName(normalizedName);
        tenant.setCurrency(req.getCurrency().trim());
        tenant.setBillingDay(req.getBillingDay());
        tenant.setLateFeeType(req.getLateFeeType().trim());
        tenant.setLateFeeValue(req.getLateFeeValue());
        tenant.setAddress(req.getAddress() == null ? null : req.getAddress().trim());
        tenant.setStatus(Tenant.ACTIVE);
        tenant.setType("tenant");
        tenant.setCreatedAt(Instant.now());
        modelValidationService.validate(tenant);
        return tenantRepository.save(tenant);
    }

    public Tenant get(String tenantId) {
        return normalizeLegacyStatus(tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found")));
    }

    public Tenant update(String tenantId, UpdateTenantRequest req) {
        Tenant tenant = get(tenantId);
        if (req.getName()         != null) tenant.setName(req.getName());
        if (req.getCurrency()     != null) tenant.setCurrency(req.getCurrency());
        if (req.getBillingDay()  != null) tenant.setBillingDay(req.getBillingDay());
        if (req.getLateFeeType()  != null) tenant.setLateFeeType(req.getLateFeeType());
        if (req.getLateFeeValue() != null) tenant.setLateFeeValue(req.getLateFeeValue());
        if (req.getAddress()      != null) tenant.setAddress(req.getAddress());
        tenant.setUpdatedAt(Instant.now());
        modelValidationService.validate(tenant);
        return tenantRepository.save(tenant);
    }

    public void delete(String tenantId) {
        Tenant tenant = get(tenantId);
        if (!TenantStatusService.isActive(tenant)) {
            throw new IllegalStateException("Tenant is already inactive");
        }
        long unpaidCount = invoiceRepository.findUnpaidByTenantId(tenantId).size();
        if (unpaidCount > 0) {
            throw new IllegalStateException(
                    "Cannot deactivate tenant: " + unpaidCount + " unpaid invoice(s) must be settled first"
            );
        }
        tenant.setStatus(Tenant.INACTIVE);
        tenant.setUpdatedAt(Instant.now());
        modelValidationService.validate(tenant);
        tenantRepository.save(tenant);
    }

    public Tenant reactivate(String tenantId) {
        Tenant tenant = get(tenantId);
        if (TenantStatusService.isActive(tenant)) {
            throw new IllegalStateException("Tenant is already active");
        }
        tenantRepository.findByNameIgnoreCase(tenant.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(tenant.getId()) && TenantStatusService.isActive(existing)) {
                throw new IllegalStateException("Another active tenant already uses this name");
            }
        });
        tenant.setStatus(Tenant.ACTIVE);
        tenant.setUpdatedAt(Instant.now());
        modelValidationService.validate(tenant);
        return tenantRepository.save(tenant);
    }

    private Tenant normalizeLegacyStatus(Tenant tenant) {
        if (tenant.getStatus() == null || tenant.getStatus().isBlank()) {
            tenant.setStatus(Tenant.ACTIVE);
        }
        return tenant;
    }
}
