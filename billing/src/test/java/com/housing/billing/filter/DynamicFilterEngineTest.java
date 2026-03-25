package com.housing.billing.filter;

import com.housing.billing.exception.InvalidFilterSyntaxException;
import com.housing.billing.exception.UnknownFilterFieldException;
import com.housing.billing.exception.UnsupportedFilterOperatorException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    private record FakeInvoice(String unitNumber,
                               String status,
                               boolean active,
                               int month,
                               BigDecimal amount,
                               Instant issueDate) {
    }
}

