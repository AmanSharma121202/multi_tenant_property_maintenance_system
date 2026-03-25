package com.housing.billing.service;

import com.housing.billing.dto.request.CreateUnitRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.UpdateUnitRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.Owner;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.OwnerRepository;
import com.housing.billing.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final OwnerRepository ownerRepository;
    private final DynamicFilterEngine dynamicFilterEngine;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "unitNumber", "profileCode", "active", "ownerId"
    );

    public List<Unit> list(String tenantId, String filter) {
        List<Unit> tenantScopedUnits = unitRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(tenantScopedUnits, filter, Unit.class, FILTERABLE_FIELDS);
    }

    public Unit create(String tenantId, CreateUnitRequest req) {
        Unit unit = new Unit();
        unit.setId("unit::" + UUID.randomUUID());
        unit.setTenantId(tenantId);
        unit.setUnitNumber(req.getUnitNumber());
        unit.setProfileCode(req.getProfileCode());
        unit.setActive(req.isActive());
        unit.setType("unit");
        unit.setCreatedAt(Instant.now());
        return unitRepository.save(unit);
    }

    public Unit get(String tenantId, String unitId) {
        return unitRepository.findById(unitId)
                .filter(unit -> tenantId.equals(unit.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
    }

    public Unit update(String tenantId, String unitId, UpdateUnitRequest req) {
        Unit unit = get(tenantId, unitId);
        if (req.getProfileCode() != null) unit.setProfileCode(req.getProfileCode());
        if (req.getActive()      != null) unit.setActive(req.getActive());
        unit.setUpdatedAt(Instant.now());
        return unitRepository.save(unit);
    }

    public void deactivate(String tenantId, String unitId) {
        Unit unit = get(tenantId, unitId);
        unit.setActive(false);
        unit.setUpdatedAt(Instant.now());
        unitRepository.save(unit);
    }


    public Unit linkOwner(String tenantId, String unitId, LinkOwnerRequest req) {
        // 0) Load the unit (already checks existence in your 'get')
        Unit unit = get(tenantId, unitId);

        // 1) If the unit is already linked to THIS owner → idempotent no-op
        if (req.getOwnerId() != null && req.getOwnerId().equals(unit.getOwnerId())) {
            return unit;
        }

        // 2) If the unit is already linked to ANOTHER owner → reject (or auto-unlink by policy)
        if (unit.getOwnerId() != null && !unit.getOwnerId().equals(req.getOwnerId())) {
            // Recommended: reject with 409 Conflict (mapped by GlobalExceptionHandler)
            throw new IllegalStateException("Unit already linked to another owner. Unlink first.");
        }

        // 3) Load the target owner
        Owner owner = ownerRepository.findById(req.getOwnerId())
                .filter(foundOwner -> tenantId.equals(foundOwner.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        // 4) Update both sides
        unit.setOwnerId(req.getOwnerId());
        unit.setUpdatedAt(Instant.now());
        unitRepository.save(unit);

        if (owner.getUnitIds() == null) {
            owner.setUnitIds(new ArrayList<>());
        }
        if (!owner.getUnitIds().contains(unitId)) {
            owner.getUnitIds().add(unitId);
        }
        owner.setUpdatedAt(Instant.now());
        ownerRepository.save(owner);

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