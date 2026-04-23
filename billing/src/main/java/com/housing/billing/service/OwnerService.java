package com.housing.billing.service;

import com.housing.billing.dto.request.CreateOwnerRequest;
import com.housing.billing.dto.request.UpdateOwnerRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Owner;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.OwnerRepository;
import com.housing.billing.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final UnitRepository  unitRepository;
    private final InvoiceService invoiceService;
    private final DynamicFilterEngine dynamicFilterEngine;
    private final ModelValidationService modelValidationService;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "name", "email", "phone", "status"
    );


    private static final Map<String, String> FILTER_VALUE_NOT_FOUND_MESSAGES = Map.of(
            "name", "Owner not found for name '%s'",
            "email", "Owner not found for email '%s'",
            "phone", "Owner not found for phone '%s'",
            "status", "Owner not found for status '%s'"
    );

    public List<Owner> list(String tenantId, String filter) {
        List<Owner> tenantScopedOwners = ownerRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(
                tenantScopedOwners,
                filter,
                Owner.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
    }

    public Owner create(String tenantId, CreateOwnerRequest req) {
        String normalizedEmail = req.getEmail().trim();
        ownerRepository.findByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail).ifPresent(existing -> {
            throw new IllegalStateException("Owner already exists");
        });

        Owner owner = new Owner();
        owner.setId("owner::" + UUID.randomUUID());
        owner.setTenantId(tenantId);
        owner.setName(req.getName().trim());
        owner.setEmail(normalizedEmail);
        owner.setPhone(req.getPhone() == null ? null : req.getPhone().trim());
        owner.setStatus(req.getStatus() != null ? req.getStatus().trim() : "ACTIVE");
        owner.setUnitIds(new ArrayList<>());
        owner.setType("owner");
        owner.setCreatedAt(Instant.now());
        modelValidationService.validate(owner);
        return ownerRepository.save(owner);
    }

    public Owner get(String tenantId, String ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        if (!tenantId.equals(owner.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        return owner;
    }

    public Owner update(String tenantId, String ownerId, UpdateOwnerRequest req) {
        Owner owner = get(tenantId, ownerId);
        if (req.getName()   != null) owner.setName(req.getName());
        if (req.getEmail()  != null) owner.setEmail(req.getEmail());
        if (req.getPhone()  != null) owner.setPhone(req.getPhone());
        if (req.getStatus() != null) owner.setStatus(req.getStatus());
        owner.setUpdatedAt(Instant.now());
        modelValidationService.validate(owner);
        return ownerRepository.save(owner);
    }

    // Link a unit to this owner AND set ownerId on the unit document
    public Owner linkUnit(String tenantId, String ownerId, String unitId) {
        // Load the owner
        Owner owner = get(tenantId, ownerId);

        // Load the unit
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        // If unit already linked to THIS owner -> idempotent
        if (ownerId.equals(unit.getOwnerId())) {
            // ensure owner's list contains unitId
            if (owner.getUnitIds() == null) owner.setUnitIds(new ArrayList<>());
            if (!owner.getUnitIds().contains(unitId)) {
                owner.getUnitIds().add(unitId);
                owner.setUpdatedAt(Instant.now());
                ownerRepository.save(owner);
            }
            invoiceService.backfillOwnerForUnitInvoices(tenantId, unitId, ownerId);
            return owner; // unit already correctly linked
        }

        // If unit linked to ANOTHER owner -> reject (same rule as UnitService)
        if (unit.getOwnerId() != null && !unit.getOwnerId().equals(ownerId)) {
            throw new IllegalStateException(
                    "Unit already linked to another owner. Unlink first."
            );
        }

        // 1) Add unitId to owner's list
        if (owner.getUnitIds() == null) {
            owner.setUnitIds(new ArrayList<>());
        }
        if (!owner.getUnitIds().contains(unitId)) {
            owner.getUnitIds().add(unitId);
        }
        owner.setUpdatedAt(Instant.now());
        modelValidationService.validate(owner);
        ownerRepository.save(owner);

        // 2) Update unit
        unit.setOwnerId(ownerId);
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        unitRepository.save(unit);

        invoiceService.backfillOwnerForUnitInvoices(tenantId, unitId, ownerId);

        return owner;
    }


    // Unlink a unit from this owner AND clear ownerId on the unit document
    public Owner unlinkUnit(String tenantId, String ownerId, String unitId) {
        Owner owner = get(tenantId, ownerId);
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        // Remove unitId from owner's list
        if (owner.getUnitIds() != null) {
            owner.getUnitIds().remove(unitId);
        }

        // Clear ownerId on the unit document
        unit.setOwnerId(null);
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        unitRepository.save(unit);

        owner.setUpdatedAt(Instant.now());
        modelValidationService.validate(owner);
        return ownerRepository.save(owner);
    }

}