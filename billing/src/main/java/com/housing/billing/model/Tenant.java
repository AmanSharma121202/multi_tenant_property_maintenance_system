package com.housing.billing.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
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
    @NotBlank(message = "Tenant name is required")
    private String name;          // Housing society name
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. INR)")
    private String currency;      // e.g. INR
    @Min(value = 1, message = "Billing day must be between 1 and 28")
    @Max(value = 28, message = "Billing day must be between 1 and 28")
    private int    billingDay;    // Day of month when invoices are generated
    @NotBlank(message = "Late fee type is required")
    @Pattern(regexp = "^(PERCENTAGE|FIXED|NONE)$",
            message = "Late fee type must be one of: PERCENTAGE, FIXED, NONE")
    private String lateFeeType;   // PERCENTAGE or FIXED
    @PositiveOrZero(message = "Late fee value must be zero or positive")
    private double lateFeeValue;  // e.g. 2.0 for 2%
    @Pattern(regexp = "^$|.*\\S.*", message = "Address cannot be blank")
    private String address;
}
