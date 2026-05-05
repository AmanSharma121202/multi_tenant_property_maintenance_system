package com.housing.billing.controller;

import com.housing.billing.dto.request.GenerateTenantInvoicesRequest;
import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceFlowEventPublisher invoiceFlowEventPublisher;

    @InjectMocks
    private InvoiceController invoiceController;

    @Test
    void generateTenantInvoices_publishesKafkaEvent() {
        ReflectionTestUtils.setField(invoiceController, "kafkaEnabled", true);

        GenerateTenantInvoicesRequest request = new GenerateTenantInvoicesRequest();
        request.setYear(2026);
        request.setMonth(5);

        ResponseEntity<Map<String, String>> response = invoiceController.generateTenantInvoices("tenant::1", request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(invoiceFlowEventPublisher).publishTenantInvoiceDue(argThat(event ->
                "tenant::1".equals(event.getTenantId())
                        && LocalDate.of(2026, 5, 1).equals(event.getBillingDate())
                        && event.getDelaySeconds() == 0L
                        && event.getEventId() != null
                        && event.getEventId().startsWith("manual-")
        ));
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
    void generateTenantInvoices_rejectsWhenKafkaDisabled() {
        ReflectionTestUtils.setField(invoiceController, "kafkaEnabled", false);

        GenerateTenantInvoicesRequest request = new GenerateTenantInvoicesRequest();
        request.setYear(2026);
        request.setMonth(5);

        assertThrows(IllegalArgumentException.class,
                () -> invoiceController.generateTenantInvoices("tenant::1", request));

        verifyNoInteractions(invoiceFlowEventPublisher);
    }
}
