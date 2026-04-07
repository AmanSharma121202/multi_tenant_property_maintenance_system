package com.housing.billing.service;

import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.filter.DynamicFilterEngine;
import com.housing.billing.model.Invoice;
import com.housing.billing.model.Profile;
import com.housing.billing.model.Tenant;
import com.housing.billing.model.Unit;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.ProfileRepository;
import com.housing.billing.repository.TenantRepository;
import com.housing.billing.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceDateComputationTest {

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invoiceService, "paymentTermsDays", 10);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generate_computesBothDatesWhenBothAreNull() {
        GenerateInvoiceRequest req = baseRequest();

        Invoice generated = invoiceService.generate("tenant::1", req);

        Instant expectedIssue = LocalDate.of(2026, 4, 5).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant expectedDue = expectedIssue.plusSeconds(10L * 24 * 60 * 60);

        assertEquals(expectedIssue, generated.getIssueDate());
        assertEquals(expectedDue, generated.getDueDate());
    }

    @Test
    void generate_keepsIssueDateAndComputesDueDateFromProvidedIssueDate() {
        GenerateInvoiceRequest req = baseRequest();
        Instant customIssue = Instant.parse("2026-04-12T00:00:00Z");
        req.setIssueDate(customIssue);

        Invoice generated = invoiceService.generate("tenant::1", req);

        Instant expectedDue = customIssue.plusSeconds(10L * 24 * 60 * 60);
        assertEquals(customIssue, generated.getIssueDate());
        assertEquals(expectedDue, generated.getDueDate());
    }

    @Test
    void generate_computesIssueDateAndKeepsProvidedDueDate() {
        GenerateInvoiceRequest req = baseRequest();
        Instant customDue = Instant.parse("2026-04-25T00:00:00Z");
        req.setDueDate(customDue);

        Invoice generated = invoiceService.generate("tenant::1", req);

        Instant expectedIssue = LocalDate.of(2026, 4, 5).atStartOfDay().toInstant(ZoneOffset.UTC);
        assertEquals(expectedIssue, generated.getIssueDate());
        assertEquals(customDue, generated.getDueDate());
    }

    @Test
    void generate_keepsBothProvidedDatesAsIs() {
        GenerateInvoiceRequest req = baseRequest();
        Instant customIssue = Instant.parse("2026-04-08T00:00:00Z");
        Instant customDue = Instant.parse("2026-04-20T00:00:00Z");
        req.setIssueDate(customIssue);
        req.setDueDate(customDue);

        Invoice generated = invoiceService.generate("tenant::1", req);

        assertEquals(customIssue, generated.getIssueDate());
        assertEquals(customDue, generated.getDueDate());
    }

    private GenerateInvoiceRequest baseRequest() {
        GenerateInvoiceRequest req = new GenerateInvoiceRequest();
        req.setUnitId("unit::101");
        req.setYear(2026);
        req.setMonth(4);

        Unit unit = new Unit();
        unit.setId("unit::101");
        unit.setTenantId("tenant::1");
        unit.setProfileCode("2BHK");
        when(unitRepository.findById("unit::101")).thenReturn(Optional.of(unit));

        Profile profile = new Profile();
        profile.setId("profile::2bhk");
        profile.setTenantId("tenant::1");
        profile.setCode("2BHK");
        profile.setMonthlyAmount(BigDecimal.valueOf(12000));
        when(profileRepository.findByTenantIdAndCode("tenant::1", "2BHK")).thenReturn(Optional.of(profile));

        Tenant tenant = new Tenant();
        tenant.setId("tenant::1");
        tenant.setBillingDay(5);
        lenient().when(tenantRepository.findById("tenant::1")).thenReturn(Optional.of(tenant));

        when(invoiceRepository.findById("INV-unit::101-202604")).thenReturn(Optional.empty());
        when(invoiceRepository.findById("INV-unit::101-202603")).thenReturn(Optional.empty());

        return req;
    }
}


