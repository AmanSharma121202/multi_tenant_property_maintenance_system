package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import java.util.List;

@Document
@Collection("owners")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Owner extends BaseDocument {

    @Id
    private String       id;
    private String       name;
    private String       email;
    private String       phone;
    private String       status;    // "ACTIVE" or "INACTIVE"
    private List<String> unitIds;   // An owner can have multiple units
}
