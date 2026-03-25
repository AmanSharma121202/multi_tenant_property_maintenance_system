package com.housing.billing.dto.request;
import lombok.Data;

@Data
public class UpdateTenantRequest {
    private String  name;
    private String  currency;
    private Integer billingDay;
    private String  lateFeeType;
    private Double  lateFeeValue;
    private String  address;
}
