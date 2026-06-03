package com.housing.billing.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantIdNormalizerTest {

    @Test
    void normalizesSingleColonTenantIdToDoubleColon() {
        assertEquals("tenant::f4897530", TenantIdNormalizer.normalize("tenant:f4897530"));
    }

    @Test
    void keepsAlreadyNormalizedTenantId() {
        assertEquals("tenant::f4897530", TenantIdNormalizer.normalize("tenant::f4897530"));
    }

    @Test
    void normalizesMultipleColonsTenantIdToDoubleColon() {
        assertEquals("tenant::f4897530", TenantIdNormalizer.normalize("tenant:::f4897530"));
    }

    @Test
    void keepsSuperadminAsIs() {
        assertEquals("superadmin", TenantIdNormalizer.normalize("superadmin"));
    }
}

