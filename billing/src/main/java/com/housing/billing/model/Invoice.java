package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.math.BigDecimal;
import java.time.Instant;

@Document
@Collection("invoices")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Invoice extends BaseDocument {

    @Id
    private String id;  // Pattern: INV-{unitId}-{YYYY}{MM} e.g. INV-A101-202501
    private String     unitId;
    private String     ownerId;
    private int        year;
    private int        month;
    private BigDecimal openingBalance;    // Previous month's closing balance
    private BigDecimal currentCharges;   // Monthly charge from Profile
    private BigDecimal lateFee;          // Applied by scheduler
    private BigDecimal adjustments;      // Manual adjustments
    private BigDecimal paymentsInPeriod; // Payments received this month
    private BigDecimal closingBalance;   // = opening + charges + lateFee + adj - payments
    private String     status;           // PAID / PARTIAL / OVERDUE
    private Instant issueDate;
    private Instant    dueDate;
}
