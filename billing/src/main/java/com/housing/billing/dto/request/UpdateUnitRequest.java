package com.housing.billing.dto.request;
import lombok.Data;

@Data
public class UpdateUnitRequest {
    private String  profileCode;
    private Boolean active;
}
