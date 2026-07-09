package com.example.ratelimiter.model;

/**
 * Outbound result of a rate-limit decision.
 *
 * @param allowed             whether this request may proceed
 * @param clientId            the client the decision was made for
 * @param requestLimit        configured RPM ceiling for this client
 * @param remainingRequests   requests left in the current window (floor 0)
 * @param tokenLimit          configured TPM ceiling for this client
 * @param remainingTokens     tokens left in the current window (floor 0)
 * @param resetInSeconds      seconds until the current window rolls over
 * @param resetAtEpochSeconds absolute epoch-second timestamp of the reset
 * @param reason              null when allowed; otherwise which limit tripped
 */
public record RateLimitCheckResponse(
        boolean allowed,
        String clientId,
        long requestLimit,
        long remainingRequests,
        long tokenLimit,
        long remainingTokens,
        long resetInSeconds,
        long resetAtEpochSeconds,
        String reason
) {
}
