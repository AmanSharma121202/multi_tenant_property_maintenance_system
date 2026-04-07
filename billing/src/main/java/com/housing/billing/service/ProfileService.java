package com.housing.billing.service;

import com.housing.billing.dto.request.CreateProfileRequest;
import com.housing.billing.dto.request.UpdateProfileRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Profile;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.ProfileRepository;
import com.housing.billing.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UnitRepository unitRepository;
    private final DynamicFilterEngine dynamicFilterEngine;
    private final ModelValidationService modelValidationService;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "code", "label", "monthlyAmount", "active"
    );


    private static final Map<String, String> FILTER_VALUE_NOT_FOUND_MESSAGES = Map.of(
            "code", "Profile not found for code '%s'",
            "label", "Profile not found for label '%s'",
            "monthlyAmount", "Profile not found for monthlyAmount '%s'",
            "active", "Profile not found for active '%s'"
    );

    public List<Profile> list(String tenantId, String filter) {
        List<Profile> tenantScopedProfiles = profileRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(
                tenantScopedProfiles,
                filter,
                Profile.class,
                FILTERABLE_FIELDS,
                FILTER_VALUE_NOT_FOUND_MESSAGES
        );
    }

    public Profile create(String tenantId, CreateProfileRequest req) {
        String normalizedCode = req.getCode().trim();
        profileRepository.findByTenantIdAndCode(tenantId, normalizedCode).ifPresent(existing -> {
            throw new IllegalStateException("Profile already exists");
        });

        Profile profile = new Profile();
        profile.setId("profile::" + UUID.randomUUID());
        profile.setTenantId(tenantId);
        profile.setCode(normalizedCode);
        profile.setLabel(req.getLabel().trim());
        profile.setMonthlyAmount(req.getMonthlyAmount());
        profile.setActive(req.isActive());
        profile.setType("profile");
        profile.setCreatedAt(Instant.now());
        modelValidationService.validate(profile);
        return profileRepository.save(profile);
    }

    public Profile get(String tenantId, String profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        if (!tenantId.equals(profile.getTenantId())) {
            throw new TenantIsolationException("Tenant isolation violation");
        }

        return profile;
    }

    public Profile update(String tenantId, String profileId, UpdateProfileRequest req) {
        Profile profile = get(tenantId, profileId);
        validateUpdateRequest(tenantId, profileId, req);

        if (req.getCode()          != null) profile.setCode(req.getCode().trim());
        if (req.getLabel()         != null) profile.setLabel(req.getLabel());
        if (req.getMonthlyAmount() != null) profile.setMonthlyAmount(req.getMonthlyAmount());
        if (req.getActive()        != null) profile.setActive(req.getActive());
        profile.setUpdatedAt(Instant.now());
        modelValidationService.validate(profile);
        return profileRepository.save(profile);
    }

    private void validateUpdateRequest(String tenantId, String profileId, UpdateProfileRequest req) {
        if (req.getCode() != null) {
            String code = req.getCode().trim();
            if (code.isBlank()) {
                throw new IllegalArgumentException("Code cannot be blank");
            }

            profileRepository.findByTenantIdAndCode(tenantId, code)
                    .filter(existing -> !existing.getId().equals(profileId))
                    .ifPresent(existing -> {
                        throw new IllegalStateException("Profile code already exists for this tenant");
                    });
        }

        if (req.getLabel() != null && req.getLabel().trim().isBlank()) {
            throw new IllegalArgumentException("Label cannot be blank");
        }

        if (req.getMonthlyAmount() != null && req.getMonthlyAmount().signum() <= 0) {
            throw new IllegalArgumentException("Monthly amount must be greater than 0");
        }
    }

    public void delete(String tenantId, String profileId) {
        validateDeleteRequest(tenantId, profileId);
        Profile profile = get(tenantId, profileId);

        if (!profile.isActive()) {
            throw new IllegalStateException("Profile is already inactive");
        }

        boolean hasActiveUnitsUsingProfile = unitRepository.findByTenantIdAndProfileCode(tenantId, profile.getCode())
                .stream()
                .anyMatch(Unit::isActive);
        if (hasActiveUnitsUsingProfile) {
            throw new IllegalStateException("Cannot delete profile while active units are using this profile");
        }

        profile.setActive(false);
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private void validateDeleteRequest(String tenantId, String profileId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId is required");
        }
        if (!profileId.startsWith("profile::")) {
            throw new IllegalArgumentException("Invalid profileId format");
        }
    }
}