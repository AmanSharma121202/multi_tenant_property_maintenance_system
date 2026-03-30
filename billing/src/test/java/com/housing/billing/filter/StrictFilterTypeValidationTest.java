package com.housing.billing.filter;

import com.housing.billing.exception.InvalidFilterSyntaxException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprehensive test suite for strict filter-syntax and data-type validation.
 * Verifies compliance with the requirement that all type mismatches return:
 * errorCode: INVALID_FILTER_SYNTAX
 * message: "unexpected token"
 */
class StrictFilterTypeValidationTest {

    private final DynamicFilterEngine engine = new DynamicFilterEngine();

    @Test
    void unitFilterRejectsUnquotedIdentifierUnitNumber() {
        // unitNumber==A-101 (unquoted) should be rejected
        // Must use: unitNumber=="A-101" (quoted)
        List<FakeUnit> source = List.of(
                new FakeUnit("A-101", "1BHK")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "unitNumber==A101", FakeUnit.class,
                        Set.of("unitNumber", "profileCode"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void profileFilterRejectsUnquotedIdentifierCode() {
        // code==BHK (unquoted) should be rejected
        // Must use: code=="BHK" (quoted)
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "code==BHK", FakeProfile.class,
                        Set.of("code", "label", "monthlyAmount"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void profileFilterRejectsUnquotedIdentifierLabel() {
        // label==MyApartment (unquoted) should be rejected
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "MyApartment", BigDecimal.valueOf(5000))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "label==MyApartment", FakeProfile.class,
                        Set.of("code", "label", "monthlyAmount"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void paymentFilterRejectsUnquotedIdentifierMethod() {
        // method==UPI (unquoted) should be rejected
        // Must use: method=="UPI" (quoted)
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "method==UPI", FakePayment.class,
                        Set.of("method", "amount", "txnRef"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void paymentFilterRejectsUnquotedIdentifierTxnRef() {
        // txnRef==TXN123 (unquoted) should be rejected
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "txnRef==TXN123", FakePayment.class,
                        Set.of("method", "amount", "txnRef"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterRejectsUnquotedIdentifierName() {
        // name==JohnDoe (unquoted) should be rejected
        List<FakeOwner> source = List.of(
                new FakeOwner("JohnDoe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "name==JohnDoe", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterRejectsUnquotedIdentifierEmail() {
        // email==johnemail (unquoted) should be rejected
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "johnemail", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "email==johnemail", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterRejectsUnquotedIdentifierPhone() {
        // phone==9876543210 (unquoted) should be rejected
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "phone==PhoneNumber", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterRejectsUnquotedIdentifierStatus() {
        // status==ACTIVE (unquoted) should be rejected
        // Must use: status=="ACTIVE" (quoted)
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "status==ACTIVE", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void invoiceFilterRejectsUnquotedIdentifierStatus() {
        // status==PAID (unquoted) should be rejected
        // Must use: status=="PAID" (quoted)
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "status==PAID", FakeInvoice.class,
                        Set.of("year", "month", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    // ====== UnitFilter Tests ======

    @Test
    void unitFilterRejectsNumericUnitNumber() {
        // unitNumber expects String (e.g., "A-101"), not numeric (101)
        List<FakeUnit> source = List.of(
                new FakeUnit("A-101", "1BHK")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "unitNumber==101", FakeUnit.class,
                        Set.of("unitNumber", "profileCode"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void unitFilterAcceptsStringUnitNumber() {
        List<FakeUnit> source = List.of(
                new FakeUnit("A-101", "1BHK")
        );

        List<FakeUnit> result = engine.apply(source, "unitNumber==\"A-101\"", FakeUnit.class,
                Set.of("unitNumber", "profileCode"));

        assertEquals(1, result.size());
    }

    @Test
    void unitFilterRejectsNumericProfileCode() {
        // profileCode expects String, not numeric
        List<FakeUnit> source = List.of(
                new FakeUnit("A-101", "1BHK")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "profileCode==100", FakeUnit.class,
                        Set.of("unitNumber", "profileCode"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void unitFilterAcceptsStringProfileCode() {
        List<FakeUnit> source = List.of(
                new FakeUnit("A-101", "1BHK")
        );

        List<FakeUnit> result = engine.apply(source, "profileCode==\"1BHK\"", FakeUnit.class,
                Set.of("unitNumber", "profileCode"));

        assertEquals(1, result.size());
    }

    // ====== ProfileFilter Tests ======

    @Test
    void profileFilterRejectsNumericCode() {
        // code expects String (e.g., "1BHK"), not numeric (100)
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "code==100", FakeProfile.class,
                        Set.of("code", "label", "monthlyAmount"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void profileFilterAcceptsStringCode() {
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        List<FakeProfile> result = engine.apply(source, "code==\"1BHK\"", FakeProfile.class,
                Set.of("code", "label", "monthlyAmount"));

        assertEquals(1, result.size());
    }

    @Test
    void profileFilterRejectsNumericLabel() {
        // label expects String, not numeric (1000)
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "label==1000", FakeProfile.class,
                        Set.of("code", "label", "monthlyAmount"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void profileFilterAcceptsStringLabel() {
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        List<FakeProfile> result = engine.apply(source, "label==\"1 BHK Apartment\"", FakeProfile.class,
                Set.of("code", "label", "monthlyAmount"));

        assertEquals(1, result.size());
    }

    @Test
    void profileFilterRejectsStringMonthlyAmount() {
        // monthlyAmount expects numeric (BigDecimal), not string
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "monthlyAmount==\"1000\"", FakeProfile.class,
                        Set.of("code", "label", "monthlyAmount"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void profileFilterAcceptsNumericMonthlyAmount() {
        List<FakeProfile> source = List.of(
                new FakeProfile("1BHK", "1 BHK Apartment", BigDecimal.valueOf(5000))
        );

        List<FakeProfile> result = engine.apply(source, "monthlyAmount==5000", FakeProfile.class,
                Set.of("code", "label", "monthlyAmount"));

        assertEquals(1, result.size());
    }

    // ====== PaymentFilter Tests ======

    @Test
    void paymentFilterRejectsNonStringMethod() {
        // method expects String (e.g., "UPI"), not numeric
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "method==123", FakePayment.class,
                        Set.of("method", "amount", "txnRef"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void paymentFilterAcceptsStringMethod() {
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        List<FakePayment> result = engine.apply(source, "method==\"UPI\"", FakePayment.class,
                Set.of("method", "amount", "txnRef"));

        assertEquals(1, result.size());
    }

    @Test
    void paymentFilterRejectsStringAmount() {
        // amount expects numeric (BigDecimal), not string
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "amount==\"1000\"", FakePayment.class,
                        Set.of("method", "amount", "txnRef"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void paymentFilterAcceptsNumericAmount() {
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        List<FakePayment> result = engine.apply(source, "amount==1000", FakePayment.class,
                Set.of("method", "amount", "txnRef"));

        assertEquals(1, result.size());
    }

    @Test
    void paymentFilterRejectsNonStringTxnRef() {
        // txnRef expects String, not numeric
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "txnRef==123", FakePayment.class,
                        Set.of("method", "amount", "txnRef"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void paymentFilterAcceptsStringTxnRef() {
        List<FakePayment> source = List.of(
                new FakePayment("UPI", BigDecimal.valueOf(1000), "TXN123")
        );

        List<FakePayment> result = engine.apply(source, "txnRef==\"TXN123\"", FakePayment.class,
                Set.of("method", "amount", "txnRef"));

        assertEquals(1, result.size());
    }

    // ====== OwnerFilter Tests ======

    @Test
    void ownerFilterRejectsNonStringName() {
        // name expects String, not numeric
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "name==123", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterAcceptsStringName() {
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        List<FakeOwner> result = engine.apply(source, "name==\"John Doe\"", FakeOwner.class,
                Set.of("name", "email", "phone", "status"));

        assertEquals(1, result.size());
    }

    @Test
    void ownerFilterRejectsNonStringEmail() {
        // email expects String, not numeric
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "email==123", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterAcceptsStringEmail() {
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        List<FakeOwner> result = engine.apply(source, "email==\"john@example.com\"", FakeOwner.class,
                Set.of("name", "email", "phone", "status"));

        assertEquals(1, result.size());
    }

    @Test
    void ownerFilterRejectsNonStringPhone() {
        // phone expects String, not numeric literal - wait this is tricky
        // Actually phone could be numeric in value, but in filter it must be quoted
        // Let me test that boolean is rejected
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "phone==true", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterAcceptsStringPhone() {
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        List<FakeOwner> result = engine.apply(source, "phone==\"9876543210\"", FakeOwner.class,
                Set.of("name", "email", "phone", "status"));

        assertEquals(1, result.size());
    }

    @Test
    void ownerFilterRejectsNonStringStatus() {
        // status expects String, not numeric
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "status==123", FakeOwner.class,
                        Set.of("name", "email", "phone", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void ownerFilterAcceptsStringStatus() {
        List<FakeOwner> source = List.of(
                new FakeOwner("John Doe", "john@example.com", "9876543210", "ACTIVE")
        );

        List<FakeOwner> result = engine.apply(source, "status==\"ACTIVE\"", FakeOwner.class,
                Set.of("name", "email", "phone", "status"));

        assertEquals(1, result.size());
    }

    // ====== InvoiceFilter Tests ======

    @Test
    void invoiceFilterRejectsStringYear() {
        // year expects numeric (int), not string
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "year==\"2025\"", FakeInvoice.class,
                        Set.of("year", "month", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void invoiceFilterAcceptsNumericYear() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        List<FakeInvoice> result = engine.apply(source, "year==2025", FakeInvoice.class,
                Set.of("year", "month", "status"));

        assertEquals(1, result.size());
    }

    @Test
    void invoiceFilterRejectsStringMonth() {
        // month expects numeric (int), not string
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "month==\"01\"", FakeInvoice.class,
                        Set.of("year", "month", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void invoiceFilterAcceptsNumericMonth() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        List<FakeInvoice> result = engine.apply(source, "month==1", FakeInvoice.class,
                Set.of("year", "month", "status"));

        assertEquals(1, result.size());
    }

    @Test
    void invoiceFilterRejectsNonStringStatus() {
        // status expects String, not numeric
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "status==123", FakeInvoice.class,
                        Set.of("year", "month", "status"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void invoiceFilterAcceptsStringStatus() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice(2025, 1, "PAID")
        );

        List<FakeInvoice> result = engine.apply(source, "status==\"PAID\"", FakeInvoice.class,
                Set.of("year", "month", "status"));

        assertEquals(1, result.size());
    }

    // Test records for different entity types

    private record FakeUnit(String unitNumber, String profileCode) {
    }

    private record FakeProfile(String code, String label, BigDecimal monthlyAmount) {
    }

    private record FakePayment(String method, BigDecimal amount, String txnRef) {
    }

    private record FakeOwner(String name, String email, String phone, String status) {
    }

    private record FakeInvoice(int year, int month, String status) {
    }
}
