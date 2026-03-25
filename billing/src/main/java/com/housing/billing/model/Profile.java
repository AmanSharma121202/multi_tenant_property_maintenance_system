package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.math.BigDecimal;

@Document
@Collection("profiles")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Profile extends BaseDocument {

    @Id
    private String id;
    private String     code;           // e.g. "1BHK", "2BHK", "VILLA"
    private String     label;          // Display name e.g. "1 BHK Apartment"
    private BigDecimal monthlyAmount;  // Monthly charge amount
    private boolean    active;
}
