package com.housing.billing.service;

import com.housing.billing.dto.request.UpdatePaymentRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Payment;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceIdempotencyTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private DynamicFilterEngine dynamicFilterEngine;
    @Mock
    private ModelValidationService modelValidationService;

    @Test
    void update_whenAmountChanges_recomputesInvoiceAndRecalculatesStatus() {
        Payment existingPayment = payment("payment::1", "tenant::1", "INV-unit::101-202604", "unit::101", "owner::1", new BigDecimal("100"));
        when(paymentRepository.findById("payment::1")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = new Invoice();
        invoice.setId("INV-unit::101-202604");
        invoice.setTenantId("tenant::1");
        invoice.setUnitId("unit::101");
        invoice.setPaymentsInPeriod(new BigDecimal("100"));
        when(invoiceService.get("tenant::1", "INV-unit::101-202604")).thenReturn(invoice);

        Payment updatedPayment = payment("payment::1", "tenant::1", "INV-unit::101-202604", "unit::101", "owner::1", new BigDecimal("130"));
        Payment anotherPayment = payment("payment::2", "tenant::1", "INV-unit::101-202604", "unit::101", "owner::1", new BigDecimal("20"));
        when(paymentRepository.findByTenantIdAndInvoiceId("tenant::1", "INV-unit::101-202604"))
                .thenReturn(List.of(updatedPayment, anotherPayment));

        PaymentService service = new PaymentService(
                paymentRepository,
                invoiceRepository,
                invoiceService,
                dynamicFilterEngine,
                modelValidationService
        );

        UpdatePaymentRequest req = new UpdatePaymentRequest();
        req.setAmount(new BigDecimal("130"));

        service.update("tenant::1", "payment::1", req);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(1)).save(invoiceCaptor.capture());
        assertEquals(new BigDecimal("150"), invoiceCaptor.getValue().getPaymentsInPeriod());
        verify(invoiceService, times(1)).recalculate("tenant::1", "INV-unit::101-202604");
    }

    @Test
    void update_whenAmountNotChanged_doesNotTouchInvoiceTotals() {
        Payment existingPayment = payment("payment::1", "tenant::1", "INV-unit::101-202604", "unit::101", "owner::1", new BigDecimal("100"));
        when(paymentRepository.findById("payment::1")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(
                paymentRepository,
                invoiceRepository,
                invoiceService,
                dynamicFilterEngine,
                modelValidationService
        );

        UpdatePaymentRequest req = new UpdatePaymentRequest();
        req.setMethod("UPI");

        service.update("tenant::1", "payment::1", req);

        verify(invoiceRepository, never()).save(any(Invoice.class));
        verify(invoiceService, never()).recalculate(eq("tenant::1"), any(String.class));
    }

    private Payment payment(String id, String tenantId, String invoiceId, String unitId, String ownerId, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setTenantId(tenantId);
        payment.setInvoiceId(invoiceId);
        payment.setUnitId(unitId);
        payment.setOwnerId(ownerId);
        payment.setAmount(amount);
        payment.setMethod("UPI");
        return payment;
    }
}

