package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;

@Document
@Collection("units")
@Scope("main")
@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseDocument {

    @Id
    private String id;

    @NotBlank(message = "Unit number is required")
    @Pattern(regexp = ".*\\S.*", message = "Unit number cannot be blank")
    private String  unitNumber;   // e.g. "A-101", "B-202"
    @NotBlank(message = "Profile code is required")
    @Pattern(regexp = ".*\\S.*", message = "Profile code cannot be blank")
    private String  profileCode;  // Links to Profile.code
    @Pattern(regexp = "^$|^owner::.+$", message = "Invalid ownerId format")
    private String  ownerId;      // Set when an owner is linked to this unit
    private boolean active;

    private BigDecimal dueAmount;
    private BigDecimal unitBalance;

    @Transient
    @JsonIgnore
    @Schema(hidden = true)
    private BigDecimal totalBalance;
}
