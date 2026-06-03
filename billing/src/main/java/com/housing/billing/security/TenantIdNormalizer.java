package com.housing.billing.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes tenant ids into a canonical format used by the backend:
 * - "superadmin" is kept as-is
 * - "tenant:<uuid>" and "tenant::<uuid>" are normalized to "tenant::<uuid>"
 */
public final class TenantIdNormalizer {
    private TenantIdNormalizer() {}

    private static final Pattern TENANT_WITH_SINGLE_OR_MULTI_COLON =
            Pattern.compile("^tenant:+(.+)$");

    public static String normalize(String rawTenantId) {
        if (rawTenantId == null) {
            return null;
        }

        String tenantId = rawTenantId.trim();
        if (tenantId.isBlank()) {
            return tenantId;
        }

        if ("superadmin".equalsIgnoreCase(tenantId)) {
            return "superadmin";
        }

        if (!tenantId.startsWith("tenant:")) {
            return tenantId;
        }

        Matcher m = TENANT_WITH_SINGLE_OR_MULTI_COLON.matcher(tenantId);
        if (!m.matches()) {
            // Unknown format; best-effort return so callers can still compare.
            return tenantId;
        }

        String uuidPart = m.group(1);
        if (uuidPart == null || uuidPart.isBlank()) {
            return tenantId;
        }

        return "tenant::" + uuidPart;
    }
}

