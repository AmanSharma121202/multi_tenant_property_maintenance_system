package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

@Document
@Collection("units")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseDocument {

    @Id
    private String id;

    private String  unitNumber;   // e.g. "A-101", "B-202"
    private String  profileCode;  // Links to Profile.code
    private String  ownerId;      // Set when an owner is linked to this unit
    private boolean active;
}
