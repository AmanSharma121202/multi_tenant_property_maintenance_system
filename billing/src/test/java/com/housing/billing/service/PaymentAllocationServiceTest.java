package com.housing.billing.service;

import com.housing.billing.dto.request.RecordPaymentRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Payment;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.PaymentRepository;
import com.housing.billing.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceTest {

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

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void record_whenTargetCovered_paysTargetFirstAndKeepsRemainder() {
        Invoice targetInvoice = invoice("INV-unit::1-202605", "unit::1", "owner::1", 2026, 5, "OVERDUE", new BigDecimal("100"), new BigDecimal("0"));
        Invoice olderDue = invoice("INV-unit::1-202604", "unit::1", "owner::1", 2026, 4, "OVERDUE", new BigDecimal("80"), new BigDecimal("0"));
        Unit unit = new Unit();
        unit.setId("unit::1");
        unit.setTenantId("tenant::1");
        unit.setUnitBalance(BigDecimal.ZERO);

        when(paymentRepository.findById(anyString())).thenReturn(Optional.empty());
        when(invoiceService.get("tenant::1", "INV-unit::1-202605")).thenReturn(targetInvoice);
        when(invoiceRepository.findByTenantIdAndUnitId("tenant::1", "unit::1"))
                .thenReturn(List.of(targetInvoice, olderDue));
        when(unitRepository.findById("unit::1")).thenReturn(Optional.of(unit));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setInvoiceId("INV-unit::1-202605");
        req.setMethod("UPI");
        req.setAmount(new BigDecimal("120"));

        Payment saved = paymentService.record("tenant::1", req, "idempotent-key");

        assertEquals(new BigDecimal("120"), saved.getAmount());
        assertEquals(new BigDecimal("20"), unit.getUnitBalance());
        verify(invoiceRepository, times(1)).save(targetInvoice);
        verify(invoiceRepository, times(0)).save(olderDue);
        verify(invoiceService, times(1)).recalculate("tenant::1", "INV-unit::1-202605");
        verify(unitRepository, times(1)).save(unit);
    }

    @Test
    void record_whenTargetNotCovered_accumulatesBalance() {
        Invoice targetInvoice = invoice("INV-unit::1-202605", "unit::1", "owner::1", 2026, 5, "OVERDUE", new BigDecimal("100"), new BigDecimal("0"));
        Unit unit = new Unit();
        unit.setId("unit::1");
        unit.setTenantId("tenant::1");
        unit.setUnitBalance(BigDecimal.ZERO);

        when(paymentRepository.findById(anyString())).thenReturn(Optional.empty());
        when(invoiceService.get("tenant::1", "INV-unit::1-202605")).thenReturn(targetInvoice);
        when(invoiceRepository.findByTenantIdAndUnitId("tenant::1", "unit::1"))
                .thenReturn(List.of(targetInvoice));
        when(unitRepository.findById("unit::1")).thenReturn(Optional.of(unit));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setInvoiceId("INV-unit::1-202605");
        req.setMethod("UPI");
        req.setAmount(new BigDecimal("60"));

        Payment saved = paymentService.record("tenant::1", req, "idempotent-key");

        assertEquals(new BigDecimal("60"), saved.getAmount());
        assertEquals(new BigDecimal("60"), unit.getUnitBalance());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(invoiceRepository, times(0)).save(any(Invoice.class));
        verify(unitRepository, times(1)).save(unit);
    }

    private Invoice invoice(String id, String unitId, String ownerId, int year, int month, String status,
                            BigDecimal closingBalance, BigDecimal paymentsInPeriod) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setTenantId("tenant::1");
        invoice.setUnitId(unitId);
        invoice.setOwnerId(ownerId);
        invoice.setYear(year);
        invoice.setMonth(month);
        invoice.setStatus(status);
        invoice.setClosingBalance(closingBalance);
        invoice.setPaymentsInPeriod(paymentsInPeriod);
        return invoice;
    }
}
