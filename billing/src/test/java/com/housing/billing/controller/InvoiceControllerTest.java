package com.housing.billing.controller;

import com.housing.billing.dto.request.GenerateTenantInvoicesRequest;
import com.housing.billing.dto.response.TenantInvoiceGenerationResult;
import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import com.housing.billing.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceFlowEventPublisher invoiceFlowEventPublisher;

    @Mock
    private AsyncInvoiceGenerationService asyncInvoiceGenerationService;

    @InjectMocks
    private InvoiceController invoiceController;

    @Test
    void generateTenantInvoices_publishesKafkaEventWithoutDirectGeneration() {
        ReflectionTestUtils.setField(invoiceController, "kafkaEnabled", true);

        GenerateTenantInvoicesRequest request = new GenerateTenantInvoicesRequest();
        request.setYear(2026);
        request.setMonth(5);
        request.setUnitId("unit::101");

        ResponseEntity<Map<String, String>> response = invoiceController.generateTenantInvoices("tenant::1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("true", response.getBody().get("queued"));
        assertEquals("0", response.getBody().get("created"));
        assertEquals("0", response.getBody().get("failed"));
        assertEquals("unit::101", response.getBody().get("unitId"));
        verify(asyncInvoiceGenerationService, never()).scheduleTenantInvoiceGeneration(
                eq("tenant::1"),
                eq(LocalDate.of(2026, 5, 1)),
                eq(Duration.ZERO),
                argThat(flowId -> flowId != null && flowId.startsWith("manual-")),
                eq("unit::101")
        );
        verify(invoiceFlowEventPublisher).publishTenantInvoiceDue(argThat(event ->
                "tenant::1".equals(event.getTenantId())
                        && "unit::101".equals(event.getUnitId())
                        && LocalDate.of(2026, 5, 1).equals(event.getBillingDate())
                        && event.getDelaySeconds() == 0L
                        && event.getEventId() != null
                        && event.getEventId().startsWith("manual-")
        ));
        verify(invoiceService).validateTenantInvoiceGeneration("tenant::1", 2026, 5, "unit::101");
    }

    @Test
    void generateTenantInvoices_rejectsCycleBeforeUnitStartDate() {
        ReflectionTestUtils.setField(invoiceController, "kafkaEnabled", true);

        GenerateTenantInvoicesRequest request = new GenerateTenantInvoicesRequest();
        request.setYear(2025);
        request.setMonth(12);
        request.setUnitId("unit::101");

        doThrow(new IllegalArgumentException(
                "Cannot generate invoice for A-101: billing period is before unit start date (2026-01-01)"))
                .when(invoiceService)
                .validateTenantInvoiceGeneration("tenant::1", 2025, 12, "unit::101");

        assertThrows(IllegalArgumentException.class,
                () -> invoiceController.generateTenantInvoices("tenant::1", request));

        verifyNoInteractions(invoiceFlowEventPublisher);
        verify(asyncInvoiceGenerationService, never()).scheduleTenantInvoiceGeneration(
                eq("tenant::1"),
                eq(LocalDate.of(2025, 12, 1)),
                eq(Duration.ZERO),
                org.mockito.ArgumentMatchers.anyString(),
                eq("unit::101")
        );
    }

    @Test
    void generateTenantInvoices_rejectsFutureMonth() {
        ReflectionTestUtils.setField(invoiceController, "kafkaEnabled", true);

        YearMonth future = YearMonth.now(ZoneOffset.UTC).plusMonths(1);
        GenerateTenantInvoicesRequest request = new GenerateTenantInvoicesRequest();
        request.setYear(future.getYear());
        request.setMonth(future.getMonthValue());

        assertThrows(IllegalArgumentException.class,
                () -> invoiceController.generateTenantInvoices("tenant::1", request));

        verifyNoInteractions(invoiceFlowEventPublisher);
    }

    @Test
    void generateTenantInvoices_skipsKafkaPublishWhenKafkaDisabled() {
        ReflectionTestUtils.setField(invoiceController, "kafkaEnabled", false);

        GenerateTenantInvoicesRequest request = new GenerateTenantInvoicesRequest();
        request.setYear(2026);
        request.setMonth(5);
        request.setUnitId("unit::101");

        when(asyncInvoiceGenerationService.scheduleTenantInvoiceGeneration(
                eq("tenant::1"),
                eq(LocalDate.of(2026, 5, 1)),
                eq(Duration.ZERO),
                argThat(flowId -> flowId != null && flowId.startsWith("manual-")),
                eq("unit::101")
        )).thenReturn(new TenantInvoiceGenerationResult(1, 0, 1, 0));

        ResponseEntity<Map<String, String>> response = invoiceController.generateTenantInvoices("tenant::1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("0", response.getBody().get("created"));
        assertEquals("1", response.getBody().get("skipped"));
        verifyNoInteractions(invoiceFlowEventPublisher);
        verify(asyncInvoiceGenerationService).scheduleTenantInvoiceGeneration(
                eq("tenant::1"),
                eq(LocalDate.of(2026, 5, 1)),
                eq(Duration.ZERO),
                argThat(flowId -> flowId != null && flowId.startsWith("manual-")),
                eq("unit::101")
        );
    }
}
