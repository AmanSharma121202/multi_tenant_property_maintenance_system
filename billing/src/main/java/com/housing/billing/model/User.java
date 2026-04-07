package com.housing.billing.model;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String       email;
    @NotBlank(message = "passwordHash is required")
    private String       passwordHash;  // Always store BCrypt hash, never plaintext
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String       name;
    @NotEmpty(message = "At least one role is required")
    @Size(max = 5, message = "Roles must not exceed 5 entries")
    private List<@Pattern(regexp = "^(SUPERADMIN|TENANT_ADMIN|CSR|OWNER)$",
            message = "Invalid role value") String> roles;
    private boolean      active;
}
