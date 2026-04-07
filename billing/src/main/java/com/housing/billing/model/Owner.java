package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @NotBlank(message = "Name is required")
    private String       name;
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String       email;
    @Pattern(regexp = "^$|^[+0-9][0-9\\- ]{6,19}$", message = "Phone must be a valid contact number")
    private String       phone;
    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Status must be either ACTIVE or INACTIVE")
    private String       status;    // "ACTIVE" or "INACTIVE"
    private List<String> unitIds;   // An owner can have multiple units
}
