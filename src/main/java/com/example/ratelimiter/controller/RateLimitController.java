package com.example.ratelimiter.controller;

import com.example.ratelimiter.model.RateLimitCheckRequest;
import com.example.ratelimiter.model.RateLimitCheckResponse;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface for the rate limiter. All decisions flow through
 * {@link RateLimiterService}, which is oblivious to whichever
 * {@code RateLimitStore} backend is currently active.
 */
@RestController
@RequestMapping("/api/v1/rate-limit")
@Validated
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Consumes one request (and any estimated tokens) from the client's
     * current window and reports whether it was allowed.
     *
     * Returns HTTP 200 with {@code allowed:true} when under limits, and
     * HTTP 429 with {@code allowed:false} when either RPM or TPM was
     * exceeded - the body always carries the full quota detail either way.
     */
    @PostMapping("/check")
    public ResponseEntity<RateLimitCheckResponse> check(@Valid @RequestBody RateLimitCheckRequest request) {
        RateLimitCheckResponse response = rateLimiterService.check(request.clientId(), request.tokensOrZero());
        HttpStatus status = response.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Read-only quota lookup for a client - does not consume any request or
     * token allowance. Useful for dashboards / pre-flight checks.
     */
    @GetMapping("/status/{clientId}")
    public ResponseEntity<RateLimitCheckResponse> status(
            @PathVariable @NotBlank(message = "clientId must not be blank") String clientId) {
        return ResponseEntity.ok(rateLimiterService.status(clientId));
    }
}
