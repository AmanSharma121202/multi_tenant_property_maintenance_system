package com.housing.billing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "TENANT_ADMIN")
class TenantAdminAuthorizationErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listTenants_asNonSuperAdmin_returnsForbiddenWithErrorBody() throws Exception {
        mockMvc.perform(get("/tenants"))
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
                  "billing_date": "2026-04-20",
                  "lateFeeType": "PERCENTAGE",
                  "lateFeeValue": 2.5,
                  "address": "Main Street"
                }
                """;

        mockMvc.perform(post("/tenants")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Unauthorized request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
