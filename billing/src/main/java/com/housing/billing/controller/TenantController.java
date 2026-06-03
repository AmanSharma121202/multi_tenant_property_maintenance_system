package com.housing.billing.controller;

import com.housing.billing.dto.request.CreateTenantRequest;
import com.housing.billing.dto.request.UpdateTenantRequest;
import com.housing.billing.model.Tenant;
import com.housing.billing.security.TenantIdNormalizer;
import com.housing.billing.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<List<Tenant>> list() {
        return ResponseEntity.ok(tenantService.list());
    }

    @PostMapping
    public ResponseEntity<Tenant> create(@Valid @RequestBody CreateTenantRequest req) {
        return ResponseEntity.status(201).body(tenantService.create(req));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<Tenant> get(@PathVariable String tenantId) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(tenantService.get(tenantId));
    }

    @PatchMapping("/{tenantId}")
    public ResponseEntity<Tenant> update(@PathVariable String tenantId,
                                         @Valid @RequestBody UpdateTenantRequest req) {
        tenantId = TenantIdNormalizer.normalize(tenantId);
        return ResponseEntity.ok(tenantService.update(tenantId, req));
    }
}
