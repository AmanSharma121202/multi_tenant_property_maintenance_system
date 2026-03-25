package com.housing.billing.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

public class TenantGuard implements HandlerInterceptor {

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {

        // Only check tenant-scoped URLs
        if (!req.getRequestURI().startsWith("/tenants/")) {
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

        String pathTenantId = pathVars.get("tenantId");

        // Extract tenantId from JWT details
        if (!(auth.getDetails() instanceof TenantAuthDetails details)) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        String tokenTenantId = details.getTenantId();

        if (!pathTenantId.equals(tokenTenantId)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch");
            return false;
        }

        return true;
    }
}