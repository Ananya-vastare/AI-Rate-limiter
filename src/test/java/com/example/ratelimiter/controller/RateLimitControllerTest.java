package com.example.ratelimiter.controller;

import com.example.ratelimiter.model.RateLimitCheckResponse;
import com.example.ratelimiter.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateLimitController.class)
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void returns200WhenAllowed() throws Exception {
        when(rateLimiterService.check(eq("clientA"), anyLong())).thenReturn(
                new RateLimitCheckResponse(true, "clientA", 60, 59, 10000, 9900, 45, 1_000_045L, null));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"clientA","estimatedTokens":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remainingRequests").value(59));
    }

    @Test
    void returns429WhenDenied() throws Exception {
        when(rateLimiterService.check(eq("clientA"), anyLong())).thenReturn(
                new RateLimitCheckResponse(false, "clientA", 60, 0, 10000, 9900, 12, 1_000_012L, "RPM_EXCEEDED"));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"clientA","estimatedTokens":100}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("RPM_EXCEEDED"));
    }

    @Test
    void returns400WhenClientIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estimatedTokens":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void statusEndpointReturnsCurrentQuota() throws Exception {
        when(rateLimiterService.status(eq("clientA"))).thenReturn(
                new RateLimitCheckResponse(true, "clientA", 60, 60, 10000, 10000, 60, 1_000_060L, null));

        mockMvc.perform(get("/api/v1/rate-limit/status/clientA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingRequests").value(60));
    }
}
