package com.housing.billing.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

@Document
@Collection("tenants")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Tenant extends BaseDocument {

    @Id
    private String id;
    private String name;          // Housing society name
    private String currency;      // e.g. INR
    private int    billingDay;    // Day of month when invoices are generated
    private String lateFeeType;   // PERCENTAGE or FIXED
    private double lateFeeValue;  // e.g. 2.0 for 2%
    private String address;
}
