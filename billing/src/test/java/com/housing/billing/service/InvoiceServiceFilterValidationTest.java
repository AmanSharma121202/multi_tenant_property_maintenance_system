package com.housing.billing.service;

import com.housing.billing.exception.InvalidFilterSyntaxException;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.ProfileRepository;
import com.housing.billing.repository.TenantRepository;
import com.housing.billing.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceFilterValidationTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private DynamicFilterEngine dynamicFilterEngine;
    @Mock
    private ModelValidationService modelValidationService;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void list_rejectsMonthWithoutYear() {
        when(invoiceRepository.findAllByTenantId("tenant::1")).thenReturn(List.of());

        assertThrows(InvalidFilterSyntaxException.class,
                () -> invoiceService.list("tenant::1", "month==1"));

        verifyNoInteractions(dynamicFilterEngine);
    }

    @Test
    void list_allowsMonthWithYear() {
        when(invoiceRepository.findAllByTenantId("tenant::1")).thenReturn(List.of());
        when(dynamicFilterEngine.apply(any(), eq("year==2026 && month==1"), eq(Invoice.class), any(), any()))
                .thenReturn(List.of());

        invoiceService.list("tenant::1", "year==2026 && month==1");

        verify(dynamicFilterEngine, times(1))
                .apply(any(), eq("year==2026 && month==1"), eq(Invoice.class), any(), any());
    }
}

