package com.housing.billing.security;

import com.housing.billing.service.TenantStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantGuardTest {

    @Mock
    private TenantStatusService tenantStatusService;

    private TenantGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TenantGuard(tenantStatusService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantGuard_allowsWhenTenantIdFormatDiffers() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);

        when(req.getRequestURI()).thenReturn("/tenants/tenant:abc/invoices/generate");
        when(req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("tenantId", "tenant:abc"));
        doNothing().when(tenantStatusService).requireActive("tenant::abc");

        var auth = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        auth.setDetails(new TenantAuthDetails("tenant::abc"));

        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(guard.preHandle(req, res, new Object()));
        verify(tenantStatusService).requireActive("tenant::abc");
    }

    @Test
    void tenantGuard_blocksWhenTenantUuidsDontMatch() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);

        when(req.getRequestURI()).thenReturn("/tenants/tenant:xyz/invoices/generate");
        when(req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("tenantId", "tenant:xyz"));

        var auth = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        auth.setDetails(new TenantAuthDetails("tenant::abc"));

        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(AccessDeniedException.class, () -> guard.preHandle(req, res, new Object()));
    }

    @Test
    void tenantGuard_bypassesForSuperadmin() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);

        when(req.getRequestURI()).thenReturn("/tenants/tenant:xyz/invoices/generate");

        var auth = new UsernamePasswordAuthenticationToken(
                "super@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
        );
        auth.setDetails(new TenantAuthDetails("tenant::abc"));

        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(guard.preHandle(req, res, new Object()));
    }

    @Test
    void tenantGuard_skipsTopLevelTenantCrudPath() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);

        when(req.getRequestURI()).thenReturn("/tenants/tenant::abc");

        var auth = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        auth.setDetails(new TenantAuthDetails("tenant::abc"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(guard.preHandle(req, res, new Object()));
    }

    @Test
    void tenantGuard_rejectsMalformedTenantIdEncoding() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);

        when(req.getRequestURI()).thenReturn("/tenants/tenant%2/invoices/generate");
        when(req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("tenantId", "tenant%2"));

        var auth = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        auth.setDetails(new TenantAuthDetails("tenant::abc"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(IllegalArgumentException.class, () -> guard.preHandle(req, res, new Object()));
    }
}
