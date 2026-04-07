package com.housing.billing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnauthenticatedAuthorizationErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listTenants_withoutLogin_returnsForbiddenWithErrorBody() throws Exception {
        mockMvc.perform(get("/tenants"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Unauthorized request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

