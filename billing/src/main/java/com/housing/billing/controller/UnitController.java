package com.housing.billing.controller;

import com.housing.billing.dto.request.CreateUnitRequest;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.UpdateUnitRequest;
import com.housing.billing.model.Unit;
import com.housing.billing.service.UnitService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/units")
@RequiredArgsConstructor
public class UnitController {
    private final UnitService unitService;

    @GetMapping
    public ResponseEntity<List<Unit>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression. Example: unitNumber==\"A-102\" && active==true",
                    example = "unitNumber==\"A-102\" && active==true"
            )
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(unitService.list(tenantId, filter));
    }

    @PostMapping
    public ResponseEntity<Unit> create(@PathVariable String tenantId,
                                       @Valid @RequestBody CreateUnitRequest req) {
        return ResponseEntity.status(201).body(unitService.create(tenantId, req));
    }

    @GetMapping("/{unitId}")
    public ResponseEntity<Unit> get(@PathVariable String tenantId,
                                    @PathVariable String unitId) {
        return ResponseEntity.ok(unitService.get(tenantId, unitId));
    }

    @PatchMapping("/{unitId}")
    public ResponseEntity<Unit> update(@PathVariable String tenantId,
                                       @PathVariable String unitId,
                                       @Valid @RequestBody UpdateUnitRequest req) {
        return ResponseEntity.ok(unitService.update(tenantId, unitId, req));
    }

    @DeleteMapping("/{unitId}")
    public ResponseEntity<Void> delete(@PathVariable String tenantId,
                                       @PathVariable String unitId) {
        unitService.deactivate(tenantId, unitId);
        return ResponseEntity.noContent().build();
    }

    // Link owner to unit — body contains ownerId
    @PostMapping("/{unitId}:link-owner")
    public ResponseEntity<Unit> linkOwner(@PathVariable String tenantId,
                                          @PathVariable String unitId,
                                          @Valid @RequestBody LinkOwnerRequest req) {
        return ResponseEntity.ok(unitService.linkOwner(tenantId, unitId, req));
    }

    // Unlink owner from unit — no body needed
    @PostMapping("/{unitId}:unlink-owner")
    public ResponseEntity<Unit> unlinkOwner(@PathVariable String tenantId,
                                            @PathVariable String unitId) {
        return ResponseEntity.ok(unitService.unlinkOwner(tenantId, unitId));
    }
}