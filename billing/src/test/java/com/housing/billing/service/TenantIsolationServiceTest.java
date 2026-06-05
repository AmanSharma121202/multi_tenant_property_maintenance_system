package com.housing.billing.service;

import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.exception.TenantIsolationException;
import com.housing.billing.dto.request.CreateOwnerRequest;
import com.housing.billing.dto.request.CreateProfileRequest;
import com.housing.billing.dto.request.CreateTenantRequest;
import com.housing.billing.dto.request.CreateUnitRequest;
import com.housing.billing.model.Payment;
import com.housing.billing.model.Owner;
import com.housing.billing.model.Profile;
import com.housing.billing.model.Tenant;
import com.housing.billing.model.Unit;
import com.housing.billing.dto.request.UpdateProfileRequest;
import com.housing.billing.repository.TenantRepository;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.OwnerRepository;
import com.housing.billing.repository.PaymentRepository;
import com.housing.billing.repository.ProfileRepository;
import com.housing.billing.repository.UnitRepository;
import com.housing.billing.filter.DynamicFilterEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class TenantIsolationServiceTest {

    @Mock
    private UnitRepository unitRepository;
    @Mock
    private OwnerRepository ownerRepository;
    @Mock
    private DynamicFilterEngine dynamicFilterEngine;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceService invoiceService;

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ModelValidationService modelValidationService;

    @InjectMocks
    private UnitService unitService;
    @InjectMocks
    private PaymentService paymentService;
    @InjectMocks
    private ProfileService profileService;
    @InjectMocks
    private TenantService tenantService;
    @InjectMocks
    private OwnerService ownerService;

    @Test
    void unitGetReturnsResourceWhenTenantMatches() {
        Unit unit = new Unit();
        unit.setId("unit::1");
        unit.setTenantId("tenant-a");
        when(unitRepository.findById("unit::1")).thenReturn(Optional.of(unit));

        Unit result = unitService.get("tenant-a", "unit::1");

        assertEquals("unit::1", result.getId());
    }

    @Test
    void unitGetThrowsWhenTenantDoesNotMatch() {
        Unit unit = new Unit();
        unit.setId("unit::1");
        unit.setTenantId("tenant-b");
        when(unitRepository.findById("unit::1")).thenReturn(Optional.of(unit));

        assertThrows(TenantIsolationException.class, () -> unitService.get("tenant-a", "unit::1"));
    }

    @Test
    void paymentGetThrowsWhenTenantDoesNotMatch() {
        Payment payment = new Payment();
        payment.setId("payment::1");
        payment.setTenantId("tenant-b");
        when(paymentRepository.findById("payment::1")).thenReturn(Optional.of(payment));

        assertThrows(TenantIsolationException.class, () -> paymentService.get("tenant-a", "payment::1"));
    }

    @Test
    void profileGetThrowsWhenTenantDoesNotMatch() {
        Profile profile = new Profile();
        profile.setId("profile::1");
        profile.setTenantId("tenant-b");
        when(profileRepository.findById("profile::1")).thenReturn(Optional.of(profile));

        assertThrows(TenantIsolationException.class, () -> profileService.get("tenant-a", "profile::1"));
    }

    @Test
    void unitDeleteThrowsForInvalidUnitIdFormat() {
        assertThrows(IllegalArgumentException.class, () -> unitService.delete("tenant-a", "abc"));
        verify(unitRepository, never()).delete(any());
    }

    @Test
    void unitDeleteDeactivatesActiveUnit() {
        Unit unit = new Unit();
        unit.setId("unit::1");
        unit.setTenantId("tenant-a");
        unit.setActive(true);
        unit.setType("unit");
        when(unitRepository.findById("unit::1")).thenReturn(Optional.of(unit));
        when(invoiceRepository.findByTenantIdAndUnitId("tenant-a", "unit::1")).thenReturn(List.of());
        when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        unitService.delete("tenant-a", "unit::1");

        verify(unitRepository, org.mockito.Mockito.atLeastOnce()).save(
                org.mockito.ArgumentMatchers.argThat(saved -> !saved.isActive()));
        verify(unitRepository, never()).delete(any());
    }

    @Test
    void unitDeleteThrowsWhenAlreadyInactive() {
        Unit unit = new Unit();
        unit.setId("unit::1");
        unit.setTenantId("tenant-a");
        unit.setActive(false);
        when(unitRepository.findById("unit::1")).thenReturn(Optional.of(unit));

        assertThrows(IllegalStateException.class, () -> unitService.delete("tenant-a", "unit::1"));
    }

    @Test
    void profileDeleteThrowsWhenReferencedByActiveUnit() {
        Profile profile = new Profile();
        profile.setId("profile::1");
        profile.setTenantId("tenant-a");
        profile.setCode("1BHK");
        profile.setActive(true);

        Unit activeUnit = new Unit();
        activeUnit.setId("unit::1");
        activeUnit.setTenantId("tenant-a");
        activeUnit.setProfileCode("1BHK");
        activeUnit.setActive(true);

        when(profileRepository.findById("profile::1")).thenReturn(Optional.of(profile));
        when(unitRepository.findByTenantIdAndProfileCode("tenant-a", "1BHK")).thenReturn(List.of(activeUnit));

        assertThrows(IllegalStateException.class, () -> profileService.delete("tenant-a", "profile::1"));
        verify(profileRepository, never()).delete(any());
    }

    @Test
    void profileDeleteDeactivatesProfileWhenNoActiveUnitsUseCode() {
        Profile profile = new Profile();
        profile.setId("profile::1");
        profile.setTenantId("tenant-a");
        profile.setCode("1BHK");
        profile.setActive(true);
        profile.setType("profile");
        when(profileRepository.findById("profile::1")).thenReturn(Optional.of(profile));
        when(unitRepository.findByTenantIdAndProfileCode("tenant-a", "1BHK")).thenReturn(List.of());
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileService.delete("tenant-a", "profile::1");

        verify(profileRepository).save(org.mockito.ArgumentMatchers.argThat(saved -> !saved.isActive()));
        verify(profileRepository, never()).delete(any());
    }

    @Test
    void profileUpdateThrowsWhenCodeAlreadyExistsForTenant() {
        Profile profile = new Profile();
        profile.setId("profile::1");
        profile.setTenantId("tenant-a");
        profile.setCode("1BHK");

        Profile another = new Profile();
        another.setId("profile::2");
        another.setTenantId("tenant-a");
        another.setCode("2BHK");

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCode("2BHK");

        when(profileRepository.findById("profile::1")).thenReturn(Optional.of(profile));
        when(profileRepository.findByTenantIdAndCode("tenant-a", "2BHK")).thenReturn(Optional.of(another));

        assertThrows(IllegalStateException.class, () -> profileService.update("tenant-a", "profile::1", req));
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void profileUpdateAllowsCodeChangeWhenUnique() {
        Profile profile = new Profile();
        profile.setId("profile::1");
        profile.setTenantId("tenant-a");
        profile.setCode("1BHK");

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCode("2BHK");

        when(profileRepository.findById("profile::1")).thenReturn(Optional.of(profile));
        when(profileRepository.findByTenantIdAndCode("tenant-a", "2BHK")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile updated = profileService.update("tenant-a", "profile::1", req);

        assertEquals("2BHK", updated.getCode());
        verify(profileRepository).save(any(Profile.class));
    }
    @Test
    void tenantCreateThrowsWhenTenantAlreadyExists() {
        CreateTenantRequest req = new CreateTenantRequest();
        req.setName("Sunrise Residency");
        req.setCurrency("INR");
        req.setBillingDay(20);
        req.setLateFeeType("PERCENTAGE");
        req.setLateFeeValue(2.5);

        when(tenantRepository.findByNameIgnoreCase("Sunrise Residency")).thenReturn(Optional.of(new Tenant()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> tenantService.create(req));
        assertEquals("Tenant already exists", ex.getMessage());
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void profileCreateThrowsWhenProfileAlreadyExists() {
        CreateProfileRequest req = new CreateProfileRequest();
        req.setCode("2BHK");
        req.setLabel("2BHK Deluxe");
        req.setMonthlyAmount(java.math.BigDecimal.valueOf(12500));

        Profile existing = new Profile();
        existing.setId("profile::existing");
        existing.setActive(true);
        when(profileRepository.findByTenantIdAndCode("tenant-a", "2BHK")).thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> profileService.create("tenant-a", req));
        assertEquals("Profile already exists", ex.getMessage());
        verify(profileRepository, never()).save(any(Profile.class));
        verify(profileRepository, never()).delete(any());
    }

    @Test
    void ownerCreateThrowsWhenOwnerAlreadyExists() {
        CreateOwnerRequest req = new CreateOwnerRequest();
        req.setName("Amit Sharma");
        req.setEmail("amit.sharma@example.com");
        req.setStatus("ACTIVE");

        Owner existing = new Owner();
        existing.setId("owner::existing");
        existing.setStatus("ACTIVE");
        when(ownerRepository.findByTenantIdAndEmailIgnoreCase("tenant-a", "amit.sharma@example.com"))
                .thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ownerService.create("tenant-a", req));
        assertEquals("Owner already exists", ex.getMessage());
        verify(ownerRepository, never()).save(any(Owner.class));
        verify(ownerRepository, never()).delete(any());
    }

    @Test
    void unitCreateThrowsWhenUnitAlreadyExists() {
        CreateUnitRequest req = new CreateUnitRequest();
        req.setUnitNumber("A-101");
        req.setProfileCode("2BHK");
        req.setActive(true);

        Unit existing = new Unit();
        existing.setId("unit::existing");
        existing.setActive(true);
        when(unitRepository.findByTenantIdAndUnitNumberIgnoreCase("tenant-a", "A-101"))
                .thenReturn(Optional.of(existing));
        Profile profile = new Profile();
        profile.setCode("2BHK");
        when(profileRepository.findByTenantIdAndCode("tenant-a", "2BHK")).thenReturn(Optional.of(profile));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> unitService.create("tenant-a", req));
        assertEquals("Unit already exists", ex.getMessage());
        verify(unitRepository, never()).save(any(Unit.class));
        verify(unitRepository, never()).delete(any(Unit.class));
    }
}
