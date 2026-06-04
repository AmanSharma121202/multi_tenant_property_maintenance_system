package com.housing.billing.model;

import com.housing.billing.model.BaseDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
    @Schema(example = "2BHK")
    @NotBlank(message = "Code is required")
    @Pattern(regexp = ".*\\S.*", message = "Code cannot be blank")
    private String     code;           // e.g. "1BHK", "2BHK", "VILLA"
    @Schema(example = "2BHK Deluxe")
    @NotBlank(message = "Label is required")
    @Pattern(regexp = ".*\\S.*", message = "Label cannot be blank")
    private String     label;
    @Schema(example = "12500")
    @NotNull(message = "Monthly amount is required")
    @Positive(message = "Monthly amount must be greater than 0")
    private BigDecimal monthlyAmount;  // Monthly charge amount
    @Schema(example = "true")
    private boolean    active;
}




