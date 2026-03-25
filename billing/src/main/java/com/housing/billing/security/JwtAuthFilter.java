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
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Every request must have: Authorization: Bearer <token>
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // remove "Bearer " prefix

            if (jwtUtil.isTokenValid(token)) {
                Claims claims = jwtUtil.parseToken(token);
                String email = claims.getSubject();
                List<String> roles = claims.get("roles", List.class);

                // Extract tenantId from token claims
                String tokenTenantId = claims.get("tenantId", String.class);

                // Convert roles to Spring Security format
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();

                // Tell Spring Security who this user is
                var auth = new UsernamePasswordAuthenticationToken(
                        email, null, authorities);

                // attach tenantId inside authentication details
                auth.setDetails(new TenantAuthDetails(tokenTenantId));

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
