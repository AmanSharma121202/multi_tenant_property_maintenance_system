package com.housing.billing.controller;

import com.housing.billing.dto.request.CreateProfileRequest;
import com.housing.billing.dto.request.UpdateProfileRequest;
import com.housing.billing.model.Profile;
import com.housing.billing.service.ProfileService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants/{tenantId}/profiles")
@RequiredArgsConstructor
@Validated
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<List<Profile>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression. Example: code==\"1BHK\" && active==true",
                    example = "code==\"1BHK\" && active==true"
            )
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(profileService.list(tenantId, filter));
    }

    @PostMapping
    public ResponseEntity<Profile> create(@PathVariable String tenantId,
                                          @Valid @RequestBody CreateProfileRequest req) {
        return ResponseEntity.status(201).body(profileService.create(tenantId, req));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<Profile> get(@PathVariable String tenantId,
                                       @PathVariable
                                       @NotBlank(message = "profileId is required")
                                       @Pattern(regexp = "^profile::.+$", message = "Invalid profileId format") String profileId) {
        return ResponseEntity.ok(profileService.get(tenantId, profileId));
    }

    @PatchMapping("/{profileId}")
    public ResponseEntity<Profile> update(@PathVariable String tenantId,
                                          @PathVariable
                                          @NotBlank(message = "profileId is required")
                                          @Pattern(regexp = "^profile::.+$", message = "Invalid profileId format") String profileId,
                                          @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(profileService.update(tenantId, profileId, req));
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(
            @PathVariable @NotBlank(message = "tenantId is required") String tenantId,
            @PathVariable
            @NotBlank(message = "profileId is required") String profileId) {
        profileService.delete(tenantId, profileId);
        return ResponseEntity.noContent().build();
    }
}
