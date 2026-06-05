package com.housing.billing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.time.LocalDate;

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
    @NotNull(message = "Billing day is required")
    @Min(value = 1, message = "Billing day must be between 1 and 31")
    @Max(value = 31, message = "Billing day must be between 1 and 31")
    @JsonProperty("billing_day")
    @Field("billing_day")
    private Integer billingDay;   // Day of month for invoice generation (1–31)
    @NotBlank(message = "Late fee type is required")
    @Pattern(regexp = "^(PERCENTAGE|FIXED|NONE)$",
            message = "Late fee type must be one of: PERCENTAGE, FIXED, NONE")
    private String lateFeeType;   // PERCENTAGE or FIXED
    @PositiveOrZero(message = "Late fee value must be zero or positive")
    private double lateFeeValue;  // e.g. 2.0 for 2%
    @Pattern(regexp = "^$|.*\\S.*", message = "Address cannot be blank")
    private String address;

    /**
     * Legacy field: reads the old "billing_date" field from Couchbase documents.
     * Read-only — not written back on save (only billing_day is persisted).
     * Used as a fallback in getBillingDay() to support old documents without migration.
     */
    @Field("billing_date")
    @ReadOnlyProperty
    @JsonIgnore
    private LocalDate legacyBillingDate;

    /**
     * Returns the billing day, falling back to the legacy billing_date field
     * if billingDay is not set (for old Couchbase documents).
     */
    public Integer getBillingDay() {
        if (billingDay != null && billingDay != 0) {
            return billingDay;
        }
        if (legacyBillingDate != null) {
            return legacyBillingDate.getDayOfMonth();
        }
        return null;
    }

    /**
     * Lazy migration: if a legacy "billing_date" is received via JSON API,
     * extract the day-of-month and set billingDay.
     */
    @JsonSetter("billing_date")
    public void setBillingDateLegacy(LocalDate legacyDate) {
        if (legacyDate != null && (this.billingDay == null || this.billingDay == 0)) {
            this.billingDay = legacyDate.getDayOfMonth();
        }
    }
}

