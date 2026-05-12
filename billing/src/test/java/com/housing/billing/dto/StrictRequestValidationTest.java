package com.housing.billing.dto;

import com.housing.billing.dto.request.CreateTenantRequest;
import com.housing.billing.dto.request.CreateOwnerRequest;
import com.housing.billing.dto.request.GenerateInvoiceRequest;
import com.housing.billing.dto.request.LinkOwnerRequest;
import com.housing.billing.dto.request.LoginRequest;
import com.housing.billing.dto.request.RecordPaymentRequest;
import com.housing.billing.dto.request.SignupRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void signupRequest_rejectsInvalidTenantIdAndRoles() {
        SignupRequest req = new SignupRequest();
        req.setName("Admin User");
        req.setEmail("admin@example.com");
        req.setPassword("secret123");
        req.setTenantId("tenant-1");
        req.setRoles(List.of("BAD_ROLE"));

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void loginRequest_rejectsInvalidEmail() {
        LoginRequest req = new LoginRequest();
        req.setEmail("bad-email");
        req.setPassword("secret123");

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void signupRequest_acceptsStrictValidValues() {
        SignupRequest req = new SignupRequest();
        req.setName("Admin User");
        req.setEmail("admin@example.com");
        req.setPassword("Secret123");
        req.setTenantId("tenant::tenant-1");
        req.setRoles(List.of("TENANT_ADMIN"));

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void recordPaymentRequest_rejectsInvalidResourceIds() {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setInvoiceId("invoice-1");
        req.setMethod("UPI");
        req.setAmount(BigDecimal.valueOf(1200));

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void recordPaymentRequest_rejectsBlankPaidByWhenProvided() {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setInvoiceId("INV-unit::123-202604");
        req.setMethod("UPI");
        req.setAmount(BigDecimal.valueOf(1200));
        req.setPaidBy("   ");

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void recordPaymentRequest_acceptsValidPaidBy() {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setInvoiceId("INV-unit::123-202604");
        req.setMethod("UPI");
        req.setAmount(BigDecimal.valueOf(1200));
        req.setPaidBy("Amit Sharma");

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void generateInvoiceRequest_rejectsMonthOutOfRange() {
        GenerateInvoiceRequest req = new GenerateInvoiceRequest();
        req.setUnitId("unit::123");
        req.setYear(2026);
        req.setMonth(13);

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void linkOwnerRequest_rejectsInvalidOwnerIdFormat() {
        LinkOwnerRequest req = new LinkOwnerRequest();
        req.setOwnerId("owner-1");

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createTenantRequest_acceptsStrictValidValues() {
        CreateTenantRequest req = new CreateTenantRequest();
        req.setName("Sunrise Residency");
        req.setCurrency("INR");
        req.setBillingDate(LocalDate.of(2026, 4, 20));
        req.setLateFeeType("PERCENTAGE");
        req.setLateFeeValue(2.5);
        req.setAddress("Main Street");

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void createOwnerRequest_rejectsInvalidPhoneFormat() {
        CreateOwnerRequest req = new CreateOwnerRequest();
        req.setName("Amit Sharma");
        req.setEmail("amit.sharma@example.com");
        req.setPhone("abc123");
        req.setStatus("ACTIVE");

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createOwnerRequest_rejectsBlankPhone() {
        CreateOwnerRequest req = new CreateOwnerRequest();
        req.setName("Amit Sharma");
        req.setEmail("amit.sharma@example.com");
        req.setPhone("   ");
        req.setStatus("ACTIVE");

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createOwnerRequest_acceptsValidPhoneFormat() {
        CreateOwnerRequest req = new CreateOwnerRequest();
        req.setName("Amit Sharma");
        req.setEmail("amit.sharma@example.com");
        req.setPhone("+91-9876543210");
        req.setStatus("ACTIVE");

        assertTrue(validator.validate(req).isEmpty());
    }
}

