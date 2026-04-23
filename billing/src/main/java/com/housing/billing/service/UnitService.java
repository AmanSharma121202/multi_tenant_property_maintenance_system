package com.housing.billing.service;

import com.housing.billing.dto.request.CreateUnitRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.UpdateUnitRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
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
public class UnitService {

    private final UnitRepository unitRepository;
    private final OwnerRepository ownerRepository;
    private final InvoiceService invoiceService;
    private final DynamicFilterEngine dynamicFilterEngine;
    private final ModelValidationService modelValidationService;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "unitNumber", "profileCode", "active"
    );


    private static final Map<String, String> FILTER_VALUE_NOT_FOUND_MESSAGES = Map.of(
            "unitNumber", "Unit not found for unitNumber '%s'",
            "profileCode", "Profile not found for profileCode '%s'",
            "active", "Unit not found for active '%s'"
    );

    public List<Unit> list(String tenantId, String filter) {
        List<Unit> tenantScopedUnits = unitRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(
                tenantScopedUnits,
                filter,
                Unit.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
    }

    public Unit create(String tenantId, CreateUnitRequest req) {
        String normalizedUnitNumber = req.getUnitNumber().trim();
        unitRepository.findByTenantIdAndUnitNumberIgnoreCase(tenantId, normalizedUnitNumber).ifPresent(existing -> {
            throw new IllegalStateException("Unit already exists");
        });

        Unit unit = new Unit();
        unit.setId("unit::" + UUID.randomUUID());
        unit.setTenantId(tenantId);
        unit.setUnitNumber(normalizedUnitNumber);
        unit.setProfileCode(req.getProfileCode().trim());
        unit.setActive(req.isActive());
        unit.setType("unit");
        unit.setCreatedAt(Instant.now());
        modelValidationService.validate(unit);
        return unitRepository.save(unit);
    }

    public Unit get(String tenantId, String unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        return unit;
    }

    public Unit update(String tenantId, String unitId, UpdateUnitRequest req) {
        Unit unit = get(tenantId, unitId);
        if (req.getUnitNumber()  != null) unit.setUnitNumber(req.getUnitNumber());
        if (req.getProfileCode() != null) unit.setProfileCode(req.getProfileCode());
        if (req.getActive()      != null) unit.setActive(req.getActive());
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        return unitRepository.save(unit);
    }

    public void deactivate(String tenantId, String unitId) {
        validateDeactivateRequest(tenantId, unitId);
        Unit unit = get(tenantId, unitId);

        if (!unit.isActive()) {
            throw new IllegalStateException("Unit is already inactive");
        }

        unit.setActive(false);
        unit.setUpdatedAt(Instant.now());
        unitRepository.save(unit);
    }

    private void validateDeactivateRequest(String tenantId, String unitId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("unitId is required");
        }
        if (!unitId.startsWith("unit::")) {
            throw new IllegalArgumentException("Invalid unitId format");
        }
    }


    public Unit linkOwner(String tenantId, String unitId, LinkOwnerRequest req) {
        // 0) Load the unit (already checks existence in your 'get')
        Unit unit = get(tenantId, unitId);

        // 1) If the unit is already linked to THIS owner → idempotent no-op
        if (req.getOwnerId() != null && req.getOwnerId().equals(unit.getOwnerId())) {
            invoiceService.backfillOwnerForUnitInvoices(tenantId, unitId, req.getOwnerId());
            return unit;
        }

        // 2) If the unit is already linked to ANOTHER owner → reject (or auto-unlink by policy)
        if (unit.getOwnerId() != null && !unit.getOwnerId().equals(req.getOwnerId())) {
            // Recommended: reject with 409 Conflict (mapped by GlobalExceptionHandler)
            throw new IllegalStateException("Unit already linked to another owner. Unlink first.");
        }

        // 3) Load the target owner
        Owner owner = ownerRepository.findById(req.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        if (!tenantId.equals(owner.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        // 4) Update both sides
        unit.setOwnerId(req.getOwnerId());
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        unitRepository.save(unit);

        if (owner.getUnitIds() == null) {
            owner.setUnitIds(new ArrayList<>());
        }
        if (!owner.getUnitIds().contains(unitId)) {
            owner.getUnitIds().add(unitId);
        }
        owner.setUpdatedAt(Instant.now());
        ownerRepository.save(owner);

        invoiceService.backfillOwnerForUnitInvoices(tenantId, unitId, req.getOwnerId());

        return unit;
    }


    public Unit unlinkOwner(String tenantId, String unitId) {
        // Load the unit
        Unit unit = get(tenantId, unitId);

        // Remember previous owner
        String prevOwnerId = unit.getOwnerId();

        // If no owner was linked, just return the current unit (idempotent behaviour)
        if (prevOwnerId == null || prevOwnerId.isBlank()) {
            return unit;
        }

        // 1) Clear the ownerId on the UNIT
        unit.setOwnerId(null);
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        unitRepository.save(unit);

        // 2) Remove the unitId from the OWNER.unitIds (if that owner still exists)
        Owner owner = ownerRepository.findById(prevOwnerId).orElse(null);
        if (owner != null && tenantId.equals(owner.getTenantId()) && owner.getUnitIds() != null) {
            owner.getUnitIds().remove(unitId);
            owner.setUpdatedAt(Instant.now());
            ownerRepository.save(owner);
        }

        return unit;
    }
}