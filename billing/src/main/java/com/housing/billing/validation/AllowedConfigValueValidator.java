package com.housing.billing.validation;

import com.housing.billing.config.TenantValidationProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AllowedConfigValueValidator implements ConstraintValidator<AllowedConfigValue, String> {

    @Autowired(required = false)
    private TenantValidationProperties tenantValidationProperties;

    private AllowedValueType type;

    @Override
    public void initialize(AllowedConfigValue constraintAnnotation) {
        this.type = constraintAnnotation.type();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String normalized = value.trim();
        List<String> allowedValues = getAllowedValues(type);
        return allowedValues.stream().anyMatch(v -> v.equalsIgnoreCase(normalized));
    }

    private List<String> getAllowedValues(AllowedValueType fieldType) {
        if (tenantValidationProperties == null) {
            return switch (fieldType) {
                case CURRENCY -> List.of("INR", "USD", "EUR", "GBP");
                case LATE_FEE_TYPE -> List.of("PERCENTAGE", "FIXED", "NONE");
                case PROFILE_CODE, CODE -> List.of("1BHK", "2BHK", "3BHK", "VILLA");
            };
        }

        return switch (fieldType) {
            case CURRENCY -> tenantValidationProperties.getValidCurrency();
            case LATE_FEE_TYPE -> tenantValidationProperties.getValidLateFeeType();
            case PROFILE_CODE -> tenantValidationProperties.getValidProfileCode();
            case CODE -> tenantValidationProperties.getValidCode();
        };
    }
}

