package com.housing.billing.service;

import com.housing.billing.exception.ResourceNotFoundException;
import com.housing.billing.model.Payment;
import com.housing.billing.model.Profile;
import com.housing.billing.model.Unit;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private UnitService unitService;
    @InjectMocks
    private PaymentService paymentService;
    @InjectMocks
    private ProfileService profileService;

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

        assertThrows(ResourceNotFoundException.class, () -> unitService.get("tenant-a", "unit::1"));
    }

    @Test
    void paymentGetThrowsWhenTenantDoesNotMatch() {
        Payment payment = new Payment();
        payment.setId("payment::1");
        payment.setTenantId("tenant-b");
        when(paymentRepository.findById("payment::1")).thenReturn(Optional.of(payment));

        assertThrows(ResourceNotFoundException.class, () -> paymentService.get("tenant-a", "payment::1"));
    }

    @Test
    void profileGetThrowsWhenTenantDoesNotMatch() {
        Profile profile = new Profile();
        profile.setId("profile::1");
        profile.setTenantId("tenant-b");
        when(profileRepository.findById("profile::1")).thenReturn(Optional.of(profile));

        assertThrows(ResourceNotFoundException.class, () -> profileService.get("tenant-a", "profile::1"));
    }
}

