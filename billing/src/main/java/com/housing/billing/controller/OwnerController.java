package com.housing.billing.controller;

import com.housing.billing.dto.request.CreateOwnerRequest;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.UpdateOwnerRequest;
import com.housing.billing.model.Owner;
import com.housing.billing.service.OwnerService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/owners")
@RequiredArgsConstructor
public class OwnerController {
    private final OwnerService ownerService;

    @GetMapping
    public ResponseEntity<List<Owner>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression. Example: status==\"ACTIVE\" && name==\"John\"",
                    example = "status==\"ACTIVE\" && name==\"John\""
            )
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(ownerService.list(tenantId, filter));
    }

    @PostMapping
    public ResponseEntity<Owner> create(@PathVariable String tenantId,
                                        @Valid @RequestBody CreateOwnerRequest req) {
        return ResponseEntity.status(201).body(ownerService.create(tenantId, req));
    }

    @GetMapping("/{ownerId}")
    public ResponseEntity<Owner> get(@PathVariable String tenantId,
                                     @PathVariable String ownerId) {
        return ResponseEntity.ok(ownerService.get(tenantId, ownerId));
    }

    @PatchMapping("/{ownerId}")
    public ResponseEntity<Owner> update(@PathVariable String tenantId,
                                        @PathVariable String ownerId,
                                        @Valid @RequestBody UpdateOwnerRequest req) {
        return ResponseEntity.ok(ownerService.update(tenantId, ownerId, req));
    }

    // Link a unit to this owner — body contains unitId
    @PostMapping("/{ownerId}:link-unit")
    public ResponseEntity<Owner> linkUnit(@PathVariable String tenantId,
                                          @PathVariable String ownerId,
                                          @RequestBody java.util.Map<String, String> body) {
        String unitId = body.get("unitId");
        return ResponseEntity.ok(ownerService.linkUnit(tenantId, ownerId, unitId));
    }

    // Unlink a unit from this owner — body contains unitId
    @PostMapping("/{ownerId}:unlink-unit")
    public ResponseEntity<Owner> unlinkUnit(@PathVariable String tenantId,
                                            @PathVariable String ownerId,
                                            @RequestBody java.util.Map<String, String> body) {
        String unitId = body.get("unitId");
        return ResponseEntity.ok(ownerService.unlinkUnit(tenantId, ownerId, unitId));
    }
}