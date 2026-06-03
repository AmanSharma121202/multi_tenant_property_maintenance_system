package com.housing.billing.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UnitController.class, ProfileController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(roles = "SUPERADMIN")
class DeleteIdValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnitService unitService;

    @MockitoBean
    private ProfileService profileService;


    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void deleteUnit_WithInvalidUnitIdFormat_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("Invalid unitId format"))
                .when(unitService)
                .delete(eq("tenant-1"), eq("not-a-unit-id"));

        mockMvc.perform(delete("/tenants/tenant-1/units/not-a-unit-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid unitId format")));
    }

    @Test
    void deleteProfile_WithInvalidProfileIdFormat_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("Invalid profileId format"))
                .when(profileService)
                .delete(eq("tenant-1"), eq("not-a-profile-id"));

        mockMvc.perform(delete("/tenants/tenant-1/profiles/not-a-profile-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid profileId format")));
    }
}





