package com.housing.billing.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Every request must have: Authorization: Bearer <token>
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // remove "Bearer " prefix

            if (jwtUtil.isTokenValid(token)) {
                Claims claims = jwtUtil.parseToken(token);
                String email = claims.getSubject();
                Object rawRoles = claims.get("roles");

                // Extract tenantId from token claims (normalize to canonical backend format)
                String tokenTenantIdRaw = claims.get("tenantId", String.class);
                String tokenTenantId = TenantIdNormalizer.normalize(tokenTenantIdRaw);

                // Convert roles to Spring Security format
                List<String> safeRoles = toRoleList(rawRoles);
                List<SimpleGrantedAuthority> authorities = safeRoles.stream()
                        .map(this::normalizeRoleToAuthority)
                        .filter(role -> !role.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                // Tell Spring Security who this user is
                var auth = new UsernamePasswordAuthenticationToken(
                        email, null, authorities);

                // attach tenantId inside authentication details (canonical format)
                auth.setDetails(new TenantAuthDetails(tokenTenantId));

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private List<String> toRoleList(Object rawRoles) {
        if (!(rawRoles instanceof List<?> roleList)) {
            return Collections.emptyList();
        }

        return roleList.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private String normalizeRoleToAuthority(String role) {
        if (role == null) {
            return "";
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }

        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}
