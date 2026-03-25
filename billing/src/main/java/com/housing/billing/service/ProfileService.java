package com.housing.billing.service;

import com.housing.billing.dto.request.CreateProfileRequest;
import com.housing.billing.dto.request.UpdateProfileRequest;
import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Profile;
import com.housing.billing.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final DynamicFilterEngine dynamicFilterEngine;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "code", "label", "monthlyAmount", "active"
    );

    public List<Profile> list(String tenantId, String filter) {
        List<Profile> tenantScopedProfiles = profileRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(tenantScopedProfiles, filter, Profile.class, FILTERABLE_FIELDS);
    }

    public Profile create(String tenantId, CreateProfileRequest req) {
        Profile profile = new Profile();
        profile.setId("profile::" + UUID.randomUUID());
        profile.setTenantId(tenantId);
        profile.setCode(req.getCode());
        profile.setLabel(req.getLabel());
        profile.setMonthlyAmount(req.getMonthlyAmount());
        profile.setActive(req.isActive());
        profile.setType("profile");
        profile.setCreatedAt(Instant.now());
        return profileRepository.save(profile);
    }

    public Profile get(String tenantId, String profileId) {
        return profileRepository.findById(profileId)
                .filter(profile -> tenantId.equals(profile.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    public Profile update(String tenantId, String profileId, UpdateProfileRequest req) {
        Profile profile = get(tenantId, profileId);
        if (req.getLabel()         != null) profile.setLabel(req.getLabel());
        if (req.getMonthlyAmount() != null) profile.setMonthlyAmount(req.getMonthlyAmount());
        if (req.getActive()        != null) profile.setActive(req.getActive().booleanValue());
        profile.setUpdatedAt(Instant.now());
        return profileRepository.save(profile);
    }

    public void delete(String tenantId, String profileId) {
        Profile profile = get(tenantId, profileId);
        profile.setActive(false);
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }
}