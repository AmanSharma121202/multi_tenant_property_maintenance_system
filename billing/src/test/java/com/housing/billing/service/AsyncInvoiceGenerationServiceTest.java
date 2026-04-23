package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncInvoiceGenerationServiceTest {

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private UnitRepository unitRepository;

    @Test
    void scheduleTenantInvoiceGeneration_generatesInvoicesForAllTenantUnits() {
        Unit u1 = unit("unit::101");
        Unit u2 = unit("unit::102");
        when(unitRepository.findByTenantId("tenant::1")).thenReturn(List.of(u1, u2));

        AsyncInvoiceGenerationService service = new AsyncInvoiceGenerationService(
                unitRepository,
                invoiceService,
                Runnable::run
        );

        LocalDate invoiceDate = LocalDate.of(2026, 4, 20);
        service.scheduleTenantInvoiceGeneration("tenant::1", invoiceDate, Duration.ZERO);

        verify(invoiceService, org.mockito.Mockito.timeout(1000).times(2))
                .generate(eq("tenant::1"), any(GenerateInvoiceRequest.class));
    }

    @Test
    void scheduleTenantInvoiceGeneration_continuesWhenOneUnitFails() {
        Unit u1 = unit("unit::101");
        Unit u2 = unit("unit::102");
        when(unitRepository.findByTenantId("tenant::1")).thenReturn(List.of(u1, u2));

        AtomicInteger calls = new AtomicInteger(0);
        doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                throw new RuntimeException("first unit failed");
            }
            return null;
        }).when(invoiceService).generate(eq("tenant::1"), any(GenerateInvoiceRequest.class));

        AsyncInvoiceGenerationService service = new AsyncInvoiceGenerationService(
                unitRepository,
                invoiceService,
                Runnable::run
        );

        service.scheduleTenantInvoiceGeneration("tenant::1", LocalDate.of(2026, 4, 20), Duration.ZERO);

        verify(invoiceService, org.mockito.Mockito.timeout(1000).times(2))
                .generate(eq("tenant::1"), any(GenerateInvoiceRequest.class));
        assertEquals(2, calls.get());
    }

    private Unit unit(String id) {
        Unit unit = new Unit();
        unit.setId(id);
        return unit;
    }
}


