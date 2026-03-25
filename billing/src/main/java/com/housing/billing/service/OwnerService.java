package com.housing.billing.service;

import com.housing.billing.dto.request.CreateOwnerRequest;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.UpdateOwnerRequest;
import com.housing.billing.exception.ResourceNotFoundException;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final UnitRepository  unitRepository;
    private final DynamicFilterEngine dynamicFilterEngine;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "name", "email", "phone", "status"
    );

    public List<Owner> list(String tenantId, String filter) {
        List<Owner> tenantScopedOwners = ownerRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(tenantScopedOwners, filter, Owner.class, FILTERABLE_FIELDS);
    }

    public Owner create(String tenantId, CreateOwnerRequest req) {
        Owner owner = new Owner();
        owner.setId("owner::" + UUID.randomUUID());
        owner.setTenantId(tenantId);
        owner.setName(req.getName());
        owner.setEmail(req.getEmail());
        owner.setPhone(req.getPhone());
        owner.setStatus(req.getStatus() != null ? req.getStatus() : "ACTIVE");
        owner.setUnitIds(new ArrayList<>());
        owner.setType("owner");
        owner.setCreatedAt(Instant.now());
        return ownerRepository.save(owner);
    }

    public Owner get(String tenantId, String ownerId) {
        return ownerRepository.findById(ownerId)
                .filter(owner -> tenantId.equals(owner.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
    }

    public Owner update(String tenantId, String ownerId, UpdateOwnerRequest req) {
        Owner owner = get(tenantId, ownerId);
        if (req.getName()   != null) owner.setName(req.getName());
        if (req.getEmail()  != null) owner.setEmail(req.getEmail());
        if (req.getPhone()  != null) owner.setPhone(req.getPhone());
        if (req.getStatus() != null) owner.setStatus(req.getStatus());
        owner.setUpdatedAt(Instant.now());
        return ownerRepository.save(owner);
    }

    // Link a unit to this owner AND set ownerId on the unit document
    public Owner linkUnit(String tenantId, String ownerId, String unitId) {
        // Load the owner
        Owner owner = get(tenantId, ownerId);

        // Load the unit
        Unit unit = unitRepository.findById(unitId)
                .filter(foundUnit -> tenantId.equals(foundUnit.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        // If unit already linked to THIS owner -> idempotent
        if (ownerId.equals(unit.getOwnerId())) {
            // ensure owner's list contains unitId
            if (owner.getUnitIds() == null) owner.setUnitIds(new ArrayList<>());
            if (!owner.getUnitIds().contains(unitId)) {
                owner.getUnitIds().add(unitId);
                owner.setUpdatedAt(Instant.now());
                ownerRepository.save(owner);
            }
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
        ownerRepository.save(owner);

        // 2) Update unit
        unit.setOwnerId(ownerId);
        unit.setUpdatedAt(Instant.now());
        unitRepository.save(unit);

        return owner;
    }


    // Unlink a unit from this owner AND clear ownerId on the unit document
    public Owner unlinkUnit(String tenantId, String ownerId, String unitId) {
        Owner owner = get(tenantId, ownerId);
        Unit unit = unitRepository.findById(unitId)
                .filter(foundUnit -> tenantId.equals(foundUnit.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        // Remove unitId from owner's list
        if (owner.getUnitIds() != null) {
            owner.getUnitIds().remove(unitId);
        }

        // Clear ownerId on the unit document
        unit.setOwnerId(null);
        unit.setUpdatedAt(Instant.now());
        unitRepository.save(unit);

        owner.setUpdatedAt(Instant.now());
        return ownerRepository.save(owner);
    }

}