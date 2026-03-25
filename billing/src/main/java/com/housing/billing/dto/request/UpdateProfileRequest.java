package com.housing.billing.dto.request;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateProfileRequest {
    private String     label;
    private BigDecimal monthlyAmount;
    private Boolean    active;
}
