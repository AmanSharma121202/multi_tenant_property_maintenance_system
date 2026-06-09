package com.housing.billing.service;

import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ModelValidationService modelValidationService;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void delete_softDeletesActiveTenant() {
        Tenant tenant = activeTenant("tenant::1");
        when(tenantRepository.findById("tenant::1")).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        tenantService.delete("tenant::1");

        assertEquals(Tenant.INACTIVE, tenant.getStatus());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void delete_throwsWhenTenantAlreadyInactive() {
        Tenant tenant = activeTenant("tenant::1");
        tenant.setStatus(Tenant.INACTIVE);
        when(tenantRepository.findById("tenant::1")).thenReturn(Optional.of(tenant));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tenantService.delete("tenant::1"));

        assertEquals("Tenant is already inactive", ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void isActive_treatsMissingStatusAsActive() {
        Tenant tenant = activeTenant("tenant::1");
        tenant.setStatus(null);
        assertTrue(TenantStatusService.isActive(tenant));
    }

    @Test
    void reactivate_restoresActiveTenant() {
        Tenant tenant = activeTenant("tenant::1");
        tenant.setStatus(Tenant.INACTIVE);
        when(tenantRepository.findById("tenant::1")).thenReturn(Optional.of(tenant));
        when(tenantRepository.findByNameIgnoreCase("Sunrise Residency")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.reactivate("tenant::1");

        assertEquals(Tenant.ACTIVE, result.getStatus());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void reactivate_throwsWhenTenantAlreadyActive() {
        Tenant tenant = activeTenant("tenant::1");
        when(tenantRepository.findById("tenant::1")).thenReturn(Optional.of(tenant));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tenantService.reactivate("tenant::1"));

        assertEquals("Tenant is already active", ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    private Tenant activeTenant(String id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantId(id);
        tenant.setName("Sunrise Residency");
        tenant.setCurrency("INR");
        tenant.setBillingDay(5);
        tenant.setLateFeeType("NONE");
        tenant.setStatus(Tenant.ACTIVE);
        tenant.setType("tenant");
        return tenant;
    }
}
