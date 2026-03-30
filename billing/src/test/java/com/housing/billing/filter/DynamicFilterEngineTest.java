package com.housing.billing.filter;

import com.housing.billing.exception.FilterValueNotFoundException;
import com.housing.billing.exception.InvalidFilterSyntaxException;
import com.housing.billing.exception.UnknownFilterFieldException;
import com.housing.billing.exception.UnsupportedFilterOperatorException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicFilterEngineTest {

    private final DynamicFilterEngine engine = new DynamicFilterEngine();

    @Test
    void appliesAndPredicateForStringAndBooleanFields() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z")),
                new FakeInvoice("A102", "OVERDUE", true, 1, BigDecimal.valueOf(200), Instant.parse("2025-01-02T00:00:00Z")),
                new FakeInvoice("A103", "PAID", false, 2, BigDecimal.valueOf(300), Instant.parse("2025-01-03T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(
                source,
                "status==\"PAID\" && active==true",
                FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate")
        );

        assertEquals(1, result.size());
        assertEquals("A101", result.getFirst().unitNumber());
    }

    @Test
    void appliesOrPredicateAndNumericComparison() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z")),
                new FakeInvoice("A102", "OVERDUE", true, 1, BigDecimal.valueOf(200), Instant.parse("2025-01-02T00:00:00Z")),
                new FakeInvoice("A103", "PAID", false, 2, BigDecimal.valueOf(300), Instant.parse("2025-01-03T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(
                source,
                "status==\"OVERDUE\" || amount>=300",
                FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate")
        );

        assertEquals(2, result.size());
    }

    @Test
    void throwsForUnknownField() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        assertThrows(
                UnknownFilterFieldException.class,
                () -> engine.apply(source, "tenantId==\"x\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );
    }

    @Test
    void throwsForBadSyntax() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "status=\"PAID\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );
    }

    @Test
    void throwsForUnsupportedOperatorAndType() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        assertThrows(
                UnsupportedFilterOperatorException.class,
                () -> engine.apply(source, "active>1", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );
    }

    @Test
    void throwsInvalidFilterSyntaxForWrongDatatypeValue() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "month==\"abc\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsWhenConfiguredFilterValueDoesNotExist() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("B-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        assertThrows(
                FilterValueNotFoundException.class,
                () -> engine.apply(
                        source,
                        "unitNumber==\"B-1\"",
                        FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"),
                        Map.of("unitNumber", "Unit not found")
                )
        );
    }

    @Test
    void doesNotThrowWhenFieldIsNotConfiguredForValueValidation() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("B-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(
                source,
                "unitNumber==\"B-1\"",
                FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"),
                Map.of()
        );

        assertEquals(0, result.size());
    }

    @Test
    void throwsComparatorNotFoundForLessThanOrEqualWhenEmpty() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        FilterValueNotFoundException ex = assertThrows(
                FilterValueNotFoundException.class,
                () -> engine.apply(source, "amount<=50", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("Not found value less than or equal to 50", ex.getMessage());
    }

    @Test
    void throwsComparatorNotFoundForGreaterThanOrEqualWhenEmpty() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        FilterValueNotFoundException ex = assertThrows(
                FilterValueNotFoundException.class,
                () -> engine.apply(source, "amount>=300", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("Not found value greater than or equal to 300", ex.getMessage());
    }

    @Test
    void throwsComparatorNotFoundForLessThanWhenEmpty() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        FilterValueNotFoundException ex = assertThrows(
                FilterValueNotFoundException.class,
                () -> engine.apply(source, "amount<50", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("Not found value less than 50", ex.getMessage());
    }

    @Test
    void throwsComparatorNotFoundForGreaterThanWhenEmpty() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        FilterValueNotFoundException ex = assertThrows(
                FilterValueNotFoundException.class,
                () -> engine.apply(source, "amount>300", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("Not found value greater than 300", ex.getMessage());
    }

    @Test
    void returnsResultsForNotEqualWhenAnyRowMatches() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z")),
                new FakeInvoice("A102", "OVERDUE", true, 1, BigDecimal.valueOf(200), Instant.parse("2025-01-02T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(
                source,
                "status!=\"PAID\"",
                FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate")
        );

        assertEquals(1, result.size());
        assertEquals("A102", result.getFirst().unitNumber());
    }

    @Test
    void throwsComparatorNotFoundForNotEqualWhenEmpty() {
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z")),
                new FakeInvoice("A102", "PAID", true, 1, BigDecimal.valueOf(200), Instant.parse("2025-01-02T00:00:00Z"))
        );

        FilterValueNotFoundException ex = assertThrows(
                FilterValueNotFoundException.class,
                () -> engine.apply(source, "status!=\"PAID\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("Not found value not equal to PAID", ex.getMessage());
    }

    // ====== STRICT DATA-TYPE VALIDATION TESTS ======

    @Test
    void throwsInvalidFilterSyntaxForStringFieldWithNumericValue() {
        // unitNumber expects String, not number
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "unitNumber==101", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForStringFieldWithBooleanValue() {
        // status expects String, not boolean
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "status==true", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForBooleanFieldWithStringValue() {
        // active expects boolean, not string
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "active==\"true\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForBooleanFieldWithNumericValue() {
        // active expects boolean, not number
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "active==1", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForNumericFieldWithStringValue() {
        // month expects numeric, not string
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "month==\"january\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForNumericFieldWithBooleanValue() {
        // month expects numeric, not boolean
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "month==true", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForBigDecimalFieldWithStringValue() {
        // amount expects BigDecimal (numeric), not string
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "amount==\"1000\"", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void throwsInvalidFilterSyntaxForBigDecimalFieldWithBooleanValue() {
        // amount expects BigDecimal (numeric), not boolean
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        InvalidFilterSyntaxException ex = assertThrows(
                InvalidFilterSyntaxException.class,
                () -> engine.apply(source, "amount==true", FakeInvoice.class,
                        Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"))
        );

        assertEquals("unexpected token", ex.getMessage());
    }

    @Test
    void acceptsCorrectStringFilter() {
        // Properly quoted strings should work
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(source, "unitNumber==\"A-101\"", FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"));

        assertEquals(1, result.size());
    }

    @Test
    void acceptsCorrectNumericFilter() {
        // Unquoted numbers should work for numeric fields
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(source, "month==1", FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"));

        assertEquals(1, result.size());
    }

    @Test
    void acceptsCorrectBooleanFilter() {
        // Unquoted booleans should work for boolean fields
        List<FakeInvoice> source = List.of(
                new FakeInvoice("A-101", "PAID", true, 1, BigDecimal.valueOf(100), Instant.parse("2025-01-01T00:00:00Z"))
        );

        List<FakeInvoice> result = engine.apply(source, "active==true", FakeInvoice.class,
                Set.of("unitNumber", "status", "active", "month", "amount", "issueDate"));

        assertEquals(1, result.size());
    }

    private record FakeInvoice(String unitNumber,
                               String status,
                               boolean active,
                               int month,
                               BigDecimal amount,
                               Instant issueDate) {
    }
}
