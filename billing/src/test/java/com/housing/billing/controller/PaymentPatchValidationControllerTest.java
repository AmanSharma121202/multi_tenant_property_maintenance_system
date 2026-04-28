package com.housing.billing.controller;

import com.housing.billing.dto.request.UpdatePaymentMetadataRequest;
import com.housing.billing.exception.GlobalExceptionHandler;
import com.housing.billing.model.Payment;
import com.housing.billing.security.JwtAuthFilter;
import com.housing.billing.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(roles = "SUPERADMIN")
class PaymentPatchValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void patchPayment_rejectsFinancialFieldAmount() throws Exception {
        String payload = """
                {
                  "amount": 100
                }
                """;

        mockMvc.perform(patch("/tenants/tenant::1/payments/payment::1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Field 'amount' is not allowed in payment metadata update"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void patchPayment_allowsMetadataFields() throws Exception {
        String payload = """
                {
                  "txnRef": "UPI-APR-2026-0001",
                  "notes": "updated note",
                  "paidBy": "Amit Sharma"
                }
                """;

        Payment payment = new Payment();
        payment.setId("payment::1");
        payment.setTenantId("tenant::1");
        payment.setTxnRef("UPI-APR-2026-0001");

        when(paymentService.update(eq("tenant::1"), eq("payment::1"), any(UpdatePaymentMetadataRequest.class)))
                .thenReturn(payment);

        mockMvc.perform(patch("/tenants/tenant::1/payments/payment::1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("payment::1"));

        verify(paymentService).update(eq("tenant::1"), eq("payment::1"), any(UpdatePaymentMetadataRequest.class));
    }
}

