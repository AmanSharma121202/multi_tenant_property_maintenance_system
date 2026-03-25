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
@Collection("payments")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseDocument {

    @Id
    private String     id;

    private String     invoiceId;
    private String     unitId;
    private String     ownerId;
    private String     method;       // CASH, BANK_TRANSFER, UPI, CHEQUE
    private BigDecimal amount;
    private String     txnRef;       // Bank transaction reference number
    private Instant receivedAt;
    private String     notes;
}
