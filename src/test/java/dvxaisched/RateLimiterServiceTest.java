/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dvxaisched;

import dvxaisched.service.RateLimiterService;
import dvxaisched.service.RateLimiterService.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        // limit: 3 requests per 2 seconds for session, 5 requests per 2 seconds for IP
        rateLimiter = new RateLimiterService(true, 3, 2, 5, 2);
    }

    @Test
    void testSessionRateLimitingWithinThreshold() {
        String session = "user-session-1";
        String ip = "192.168.1.10";

        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed(), "Request 1 should be allowed");
        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed(), "Request 2 should be allowed");
        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed(), "Request 3 should be allowed");

        RateLimitDecision rejected = rateLimiter.checkRateLimit(session, ip);
        assertFalse(rejected.allowed(), "Request 4 should be rejected exceeding session limit of 3");
        assertTrue(rejected.retryAfterSeconds() >= 1, "Retry-After should be >= 1 second");
        assertTrue(rejected.reason().contains("Rate limit reached"));
    }

    @Test
    void testIndependentSessionsUnderSameIpAggregateLimit() {
        String ip = "203.0.113.1"; // Shared conference NAT IP

        // Session A sends 3 requests
        assertTrue(rateLimiter.checkRateLimit("sess-A", ip).allowed());
        assertTrue(rateLimiter.checkRateLimit("sess-A", ip).allowed());
        assertTrue(rateLimiter.checkRateLimit("sess-A", ip).allowed());
        assertFalse(rateLimiter.checkRateLimit("sess-A", ip).allowed(), "Session A should now be throttled");

        // Session B sends 2 requests (total for IP is now 3 + 2 = 5)
        assertTrue(rateLimiter.checkRateLimit("sess-B", ip).allowed(), "Session B has its own session bucket");
        assertTrue(rateLimiter.checkRateLimit("sess-B", ip).allowed());

        // 6th request from IP across all sessions hits the IP aggregate limit (5)
        RateLimitDecision ipRejected = rateLimiter.checkRateLimit("sess-C", ip);
        assertFalse(ipRejected.allowed(), "Session C should be blocked by IP aggregate limit");
        assertTrue(ipRejected.reason().contains("network"));
    }

    @Test
    void testSlidingWindowRecovery() throws InterruptedException {
        // 2 requests per 1 second
        RateLimiterService fastLimiter = new RateLimiterService(true, 2, 1, 10, 1);
        String session = "fast-session";
        String ip = "10.0.0.1";

        assertTrue(fastLimiter.checkRateLimit(session, ip).allowed());
        assertTrue(fastLimiter.checkRateLimit(session, ip).allowed());
        assertFalse(fastLimiter.checkRateLimit(session, ip).allowed(), "Should be rate limited immediately");

        // Wait for window to expire
        Thread.sleep(1100);

        assertTrue(fastLimiter.checkRateLimit(session, ip).allowed(), "Should be allowed after window expires");
    }

    @Test
    void testDisabledRateLimiterAllowsUnlimited() {
        RateLimiterService disabled = new RateLimiterService(false, 1, 60, 1, 60);
        for (int i = 0; i < 20; i++) {
            assertTrue(disabled.checkRateLimit("test-sess", "1.2.3.4").allowed());
        }
    }

    @Test
    void testReset() {
        String session = "sess-reset";
        String ip = "172.16.0.5";

        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed());
        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed());
        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed());
        assertFalse(rateLimiter.checkRateLimit(session, ip).allowed());

        rateLimiter.reset();

        assertTrue(rateLimiter.checkRateLimit(session, ip).allowed(), "Should be allowed after reset");
    }
}
