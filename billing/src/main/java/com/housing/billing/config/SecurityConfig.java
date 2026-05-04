package com.housing.billing.config;

import com.housing.billing.security.JwtAuthFilter;
import com.housing.billing.security.RestAccessDeniedHandler;
import com.housing.billing.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(restAccessDeniedHandler)
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth

                        // ---------- Public ----------
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/api-docs/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // ---------- SuperAdmin-only ----------
                        // LIST-ALL tenants
                        .requestMatchers(HttpMethod.GET, "/tenants").hasRole("SUPERADMIN")
                        // Create tenant (SuperAdmin model). If you want anonymous bootstrap, see note below.
                        .requestMatchers(HttpMethod.POST, "/tenants").hasRole("SUPERADMIN")

                        // ---------- Tenant-scoped fetch ----------
                        // Allow fetching a single tenant for SUPERADMIN or TENANT_ADMIN;
                        // TenantGuard will enforce path tenant == token tenant (unless SUPERADMIN).
                        .requestMatchers(HttpMethod.GET, "/tenants/*").hasAnyRole("SUPERADMIN", "TENANT_ADMIN")

                        // ---------- Tenant update ----------
                        .requestMatchers(HttpMethod.PATCH, "/tenants/*").hasRole("SUPERADMIN")

                        // ---------- Everything else ----------
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
