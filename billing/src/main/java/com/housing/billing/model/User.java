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
@Collection("users")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseDocument {

    @Id
    private String       id;
    private String       email;
    private String       passwordHash;  // Always store BCrypt hash, never plaintext
    private String       name;
    private List<String> roles;         // TENANT_ADMIN, CSR, SUPERADMIN
    private boolean      active;
}
