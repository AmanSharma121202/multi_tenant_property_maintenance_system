package com.housing.billing.controller;

import com.housing.billing.config.JacksonStrictTypeConfig;
import com.housing.billing.exception.GlobalExceptionHandler;
import com.housing.billing.security.JwtAuthFilter;
import com.housing.billing.service.ProfileService;
import com.housing.billing.service.UnitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProfileController.class, UnitController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonStrictTypeConfig.class})
@WithMockUser(roles = "SUPERADMIN")
class StrictJsonTypeValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private UnitService unitService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @MockitoBean
    private com.housing.billing.service.TenantStatusService tenantStatusService;

    @Test
    void createProfile_rejectsQuotedNumberAndBooleanTypes() throws Exception {
        String payload = """
                {
                  "code": "2BHK",
                  "label": "2BHK Deluxe",
                  "monthlyAmount": "12500",
                  "active": "true"
                }
                """;

        mockMvc.perform(post("/tenants/tenant::1/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(profileService);
    }

    @Test
    void createProfile_rejectsNumericBooleanTypeForActive() throws Exception {
        String payload = """
                {
                  "code": "2BHK",
                  "label": "2BHK Deluxe",
                  "monthlyAmount": 12500,
                  "active": 1
                }
                """;

        mockMvc.perform(post("/tenants/tenant::1/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(profileService);
    }

    @Test
    void patchProfile_rejectsQuotedNumberAndBooleanTypes() throws Exception {
        String payload = """
                {
                  "monthlyAmount": "15000",
                  "active": "false"
                }
                """;

        mockMvc.perform(patch("/tenants/tenant::1/profiles/profile::1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(profileService);
    }

    @Test
    void patchProfile_rejectsNumericBooleanTypeForActive() throws Exception {
        String payload = """
                {
                  "active": 1
                }
                """;

        mockMvc.perform(patch("/tenants/tenant::1/profiles/profile::1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(profileService);
    }

    @Test
    void createUnit_rejectsQuotedBooleanTypeForActive() throws Exception {
        String payload = """
                {
                  "unitNumber": "A-101",
                  "profileCode": "2BHK",
                  "active": "true"
                }
                """;

        mockMvc.perform(post("/tenants/tenant::1/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(unitService);
    }

    @Test
    void createUnit_rejectsNumericBooleanTypeForActive() throws Exception {
        String payload = """
                {
                  "unitNumber": "A-101",
                  "profileCode": "2BHK",
                  "active": 1
                }
                """;

        mockMvc.perform(post("/tenants/tenant::1/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(unitService);
    }

    @Test
    void patchUnit_rejectsQuotedBooleanTypeForActive() throws Exception {
        String payload = """
                {
                  "active": "false"
                }
                """;

        mockMvc.perform(patch("/tenants/tenant::1/units/unit::1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(unitService);
    }

    @Test
    void patchUnit_rejectsNumericBooleanTypeForActive() throws Exception {
        String payload = """
                {
                  "active": 1
                }
                """;

        mockMvc.perform(patch("/tenants/tenant::1/units/unit::1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(unitService);
    }
}




