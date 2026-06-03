package com.housing.billing.service;

import com.housing.billing.dto.request.CreateUnitRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.UpdateUnitRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.messaging.OwnerUnitLinkedEvent;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Owner;
import com.housing.billing.model.Profile;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.OwnerRepository;
import com.housing.billing.repository.ProfileRepository;
import com.housing.billing.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final OwnerRepository ownerRepository;
    private final ProfileRepository profileRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final InvoiceFlowEventPublisher invoiceFlowEventPublisher;
    private final DynamicFilterEngine dynamicFilterEngine;
    private final ModelValidationService modelValidationService;

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

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
        List<Unit> filteredUnits = dynamicFilterEngine.apply(
                tenantScopedUnits,
                filter,
                Unit.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
        filteredUnits.forEach(unit -> enrichUnitBalances(tenantId, unit));
        return filteredUnits;
    }

    public Unit create(String tenantId, CreateUnitRequest req) {
        String normalizedUnitNumber = req.getUnitNumber().trim();
        String normalizedProfileCode = req.getProfileCode().trim();
        validateProfileCodeExists(tenantId, normalizedProfileCode);

        Optional<Unit> existing = unitRepository.findByTenantIdAndUnitNumberIgnoreCase(
                tenantId, normalizedUnitNumber);
        if (existing.isPresent()) {
            Unit unit = existing.get();
            if (unit.isActive()) {
                throw new IllegalStateException("Unit already exists");
            }
            return reactivateUnit(tenantId, unit, normalizedProfileCode, req.isActive());
        }

        Unit unit = new Unit();
        unit.setId("unit::" + UUID.randomUUID());
        unit.setTenantId(tenantId);
        unit.setUnitNumber(normalizedUnitNumber);
        unit.setProfileCode(normalizedProfileCode);
        unit.setActive(req.isActive());
        unit.setDueAmount(BigDecimal.ZERO);
        unit.setUnitBalance(BigDecimal.ZERO);
        unit.setTotalBalance(BigDecimal.ZERO);
        unit.setType("unit");
        unit.setCreatedAt(Instant.now());
        modelValidationService.validate(unit);
        Unit saved = unitRepository.save(unit);
        return enrichUnitBalances(tenantId, saved);
    }

    public Unit get(String tenantId, String unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        if (!tenantId.equals(unit.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        return enrichUnitBalances(tenantId, unit);
    }

    public Unit update(String tenantId, String unitId, UpdateUnitRequest req) {
        Unit unit = get(tenantId, unitId);
        if (req.getUnitNumber()  != null) unit.setUnitNumber(req.getUnitNumber());
        if (req.getProfileCode() != null) {
            String normalizedProfileCode = req.getProfileCode().trim();
            validateProfileCodeExists(tenantId, normalizedProfileCode);
            unit.setProfileCode(normalizedProfileCode);
        }
        if (req.getActive()      != null) unit.setActive(req.getActive());
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        Unit saved = unitRepository.save(unit);
        return enrichUnitBalances(tenantId, saved);
    }

    private Unit enrichUnitBalances(String tenantId, Unit unit) {
        List<Invoice> invoices = invoiceRepository.findByTenantIdAndUnitId(tenantId, unit.getId());
        BigDecimal totalUnpaid = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            BigDecimal closing = invoice.getClosingBalance() == null ? BigDecimal.ZERO : invoice.getClosingBalance();
            if (closing.signum() > 0 && invoice.getStatus() != null && !"PAID".equals(invoice.getStatus())) {
                totalUnpaid = totalUnpaid.add(closing);
            }
        }
        BigDecimal unitBalance = unit.getUnitBalance() == null ? BigDecimal.ZERO : unit.getUnitBalance();
        BigDecimal dueAmount = totalUnpaid.subtract(unitBalance);
        if (dueAmount.signum() < 0) {
            dueAmount = BigDecimal.ZERO;
        }
        BigDecimal totalBalance = unit.getTotalBalance() == null ? BigDecimal.ZERO : unit.getTotalBalance();
        boolean changed = !sameAmount(unit.getDueAmount(), dueAmount)
                || !sameAmount(unit.getUnitBalance(), unitBalance)
                || !sameAmount(unit.getTotalBalance(), totalBalance);
        unit.setDueAmount(dueAmount);
        unit.setUnitBalance(unitBalance);
        unit.setTotalBalance(totalBalance);
        if (changed) {
            unit.setUpdatedAt(Instant.now());
            unitRepository.save(unit);
        }
        return unit;
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private void validateProfileCodeExists(String tenantId, String profileCode) {
        profileRepository.findByTenantIdAndCode(tenantId, profileCode)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    public void delete(String tenantId, String unitId) {
        validateDeleteRequest(tenantId, unitId);
        Unit unit = get(tenantId, unitId);

        if (!unit.isActive()) {
            throw new IllegalStateException("Unit is already inactive");
        }

        if (unit.getOwnerId() != null && !unit.getOwnerId().isBlank()) {
            unlinkOwner(tenantId, unitId);
        }

        unit.setActive(false);
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        unitRepository.save(unit);
    }

    private Unit reactivateUnit(String tenantId, Unit unit, String profileCode, boolean active) {
        unit.setProfileCode(profileCode);
        unit.setActive(active);
        unit.setUpdatedAt(Instant.now());
        modelValidationService.validate(unit);
        Unit saved = unitRepository.save(unit);
        return enrichUnitBalances(tenantId, saved);
    }

    private void validateDeleteRequest(String tenantId, String unitId) {
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
            triggerOwnerBackfill(tenantId, unitId, req.getOwnerId());
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

        triggerOwnerBackfill(tenantId, unitId, req.getOwnerId());

        return unit;
    }

    private void triggerOwnerBackfill(String tenantId, String unitId, String ownerId) {
        if (kafkaEnabled) {
            OwnerUnitLinkedEvent event = OwnerUnitLinkedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .unitId(unitId)
                    .ownerId(ownerId)
                    .occurredAt(Instant.now())
                    .build();
            invoiceFlowEventPublisher.publishOwnerUnitLinked(event);
            return;
        }
        invoiceService.backfillOwnerForUnitInvoices(tenantId, unitId, ownerId);
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
