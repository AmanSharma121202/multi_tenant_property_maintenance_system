package com.housing.billing.service;

import com.housing.billing.dto.request.UpdatePaymentMetadataRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Payment;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.PaymentRepository;
import com.housing.billing.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Mock
    private UnitRepository unitRepository;

    @Test
    void update_updatesOnlyMetadataFields_andNeverRecomputesInvoice() {
        Payment existingPayment = payment("payment::1", "tenant::1", "INV-unit::101-202604", "unit::101", "owner::1", new BigDecimal("100"));
        when(paymentRepository.findById("payment::1")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(
                paymentRepository,
                invoiceRepository,
                invoiceService,
                unitRepository,
                dynamicFilterEngine,
                modelValidationService
        );

        UpdatePaymentMetadataRequest req = new UpdatePaymentMetadataRequest();
        req.setTxnRef("UPI-APR-2026-0001");
        req.setNotes("Adjusted note");
        req.setPaidBy("Amit Sharma");
        req.setReceivedAt(Instant.parse("2026-04-24T10:15:30Z"));

        Payment updated = service.update("tenant::1", "payment::1", req);

        assertEquals(new BigDecimal("100"), updated.getAmount());
        assertEquals("INV-unit::101-202604", updated.getInvoiceId());
        assertEquals("unit::101", updated.getUnitId());
        assertEquals("owner::1", updated.getOwnerId());
        assertEquals("UPI", updated.getMethod());
        assertEquals("UPI-APR-2026-0001", updated.getTxnRef());
        assertEquals("Adjusted note", updated.getNotes());
        assertEquals("Amit Sharma", updated.getPaidBy());
        assertEquals(Instant.parse("2026-04-24T10:15:30Z"), updated.getReceivedAt());
        assertNotNull(updated.getUpdatedAt());

        verify(invoiceRepository, never()).save(any(Invoice.class));
        verify(invoiceService, never()).recalculate(eq("tenant::1"), any(String.class));
        verify(paymentRepository, times(1)).save(any(Payment.class));
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
