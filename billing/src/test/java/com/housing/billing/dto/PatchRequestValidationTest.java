package com.housing.billing.dto;

import com.housing.billing.dto.request.UpdateProfileRequest;
import com.housing.billing.dto.request.UpdateTenantRequest;
import com.housing.billing.dto.request.UpdateUnitRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void updateRequests_allowEmptyPayloadForPatchSemantics() {
        assertTrue(validator.validate(new UpdateUnitRequest()).isEmpty());
        assertTrue(validator.validate(new UpdateProfileRequest()).isEmpty());
        assertTrue(validator.validate(new UpdateTenantRequest()).isEmpty());
    }

    @Test
    void updateRequests_rejectBlankStringsWhenFieldIsProvided() {
        UpdateUnitRequest unitReq = new UpdateUnitRequest();
        unitReq.setProfileCode("   ");

        UpdateProfileRequest profileReq = new UpdateProfileRequest();
        profileReq.setCode("  ");
        profileReq.setLabel(" ");

        UpdateTenantRequest tenantReq = new UpdateTenantRequest();
        tenantReq.setName("   ");
        tenantReq.setCurrency("\t");

        assertFalse(validator.validate(unitReq).isEmpty());
        assertFalse(validator.validate(profileReq).isEmpty());
        assertFalse(validator.validate(tenantReq).isEmpty());
    }
}

