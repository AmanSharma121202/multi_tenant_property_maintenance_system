package com.housing.billing.security;

import com.housing.billing.service.TenantStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantGuard implements HandlerInterceptor {

    private final TenantStatusService tenantStatusService;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {

        // Only check tenant-scoped URLs with nested resources (e.g. /tenants/{id}/units).
        // Top-level /tenants/{id} is SUPERADMIN-only CRUD and handled by @PreAuthorize.
        String uri = req.getRequestURI();
        if (!uri.startsWith("/tenants/")) {
            return true;
        }
        String afterPrefix = uri.substring("/tenants/".length());
        if (!afterPrefix.contains("/")) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        // If SUPERADMIN → bypass tenant guard completely
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"))) {
            return true;
        }

        // Extract {tenantId} from path
        Map<String, String> pathVars =
                (Map<String, String>) req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVars == null || !pathVars.containsKey("tenantId")) {
            return true;
        }

        String pathTenantIdRaw = decodePathSegment(pathVars.get("tenantId"));
        String pathTenantId = TenantIdNormalizer.normalize(pathTenantIdRaw);

        // Extract tenantId from JWT details
        if (!(auth.getDetails() instanceof TenantAuthDetails details)) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        String tokenTenantIdRaw = details.getTenantId();
        String tokenTenantId = TenantIdNormalizer.normalize(tokenTenantIdRaw);

        log.info("Token tenantId: {}", tokenTenantId);
        log.info("Request tenantId: {}", pathTenantId);
        log.debug("TenantGuard tenant check: tokenTenantIdRaw={} requestTenantIdRaw={}",
                tokenTenantIdRaw, pathTenantIdRaw);

        if (!pathTenantId.equals(tokenTenantId)) {
            // Use AccessDeniedException so the API consistently returns 403 FORBIDDEN.
            throw new AccessDeniedException("Tenant isolation violation");
        }

        tenantStatusService.requireActive(pathTenantId);

        return true;
    }

    private static String decodePathSegment(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Avoid 500s from malformed percent-encoding in path variables.
            throw new IllegalArgumentException("Invalid tenantId encoding");
        }
    }
}
