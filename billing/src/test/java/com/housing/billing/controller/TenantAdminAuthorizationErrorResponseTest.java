package com.housing.billing.controller;

import com.housing.billing.security.TenantAuthDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantAdminAuthorizationErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    private UsernamePasswordAuthenticationToken tenantAdminAuth(String tenantId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        auth.setDetails(new TenantAuthDetails(tenantId));
        return auth;
    }

    @Test
    void listTenants_asNonSuperAdmin_returnsForbiddenWithErrorBody() throws Exception {
        mockMvc.perform(get("/tenants").with(user("admin").roles("TENANT_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Unauthorized request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createTenant_asNonSuperAdmin_returnsForbiddenWithErrorBody() throws Exception {
        String payload = """
                {
                  "name": "Sunrise Residency",
                  "currency": "USD",
                  "billing_day": 20,
                  "lateFeeType": "PERCENTAGE",
                  "lateFeeValue": 2.5,
                  "address": "Main Street"
                }
                """;

        mockMvc.perform(post("/tenants")
                        .with(user("admin").roles("TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Unauthorized request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void updateTenant_asTenantAdmin_returnsForbiddenWithErrorBody() throws Exception {
        String payload = """
                {
                  "name": "Updated Tenant",
                  "currency": "USD"
                }
                """;

        mockMvc.perform(patch("/tenants/tenant::123")
                        .with(authentication(tenantAdminAuth("tenant::123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Unauthorized request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void deleteTenant_asTenantAdmin_returnsForbiddenWithErrorBody() throws Exception {
        mockMvc.perform(delete("/tenants/tenant::123").with(authentication(tenantAdminAuth("tenant::123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Unauthorized request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
