package com.housing.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "com.housing.billing.tenant")
public class TenantValidationProperties {

    private List<String> validCurrency = List.of("INR", "USD", "EUR", "GBP");
    private List<String> validLateFeeType = List.of("PERCENTAGE", "FIXED", "NONE");
    private List<String> validProfileCode = List.of("1BHK", "2BHK", "3BHK", "VILLA");
    private List<String> validCode = List.of("1BHK", "2BHK", "3BHK", "VILLA");

    public List<String> getValidCurrency() {
        return validCurrency;
    }

    public void setValidCurrency(List<String> validCurrency) {
        this.validCurrency = validCurrency;
    }

    public List<String> getValidLateFeeType() {
        return validLateFeeType;
    }

    public void setValidLateFeeType(List<String> validLateFeeType) {
        this.validLateFeeType = validLateFeeType;
    }

    public List<String> getValidProfileCode() {
        return validProfileCode;
    }

    public void setValidProfileCode(List<String> validProfileCode) {
        this.validProfileCode = validProfileCode;
    }

    public List<String> getValidCode() {
        return validCode;
    }

    public void setValidCode(List<String> validCode) {
        this.validCode = validCode;
    }
}

