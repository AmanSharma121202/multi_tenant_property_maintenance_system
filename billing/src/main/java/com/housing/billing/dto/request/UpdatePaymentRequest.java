package com.housing.billing.dto.request;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdatePaymentRequest {
    private BigDecimal amount;
    private String     method;
    private String     notes;
    private String     txnRef;
}
