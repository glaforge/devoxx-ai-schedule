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

package dvxaisched.service;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class RateLimiterService {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiterService.class);

    private final boolean enabled;
    private final int sessionLimit;
    private final long sessionWindowMs;
    private final int ipLimit;
    private final long ipWindowMs;

    private final Map<String, SlidingWindow> sessionWindows = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindow> ipWindows = new ConcurrentHashMap<>();

    public RateLimiterService(
        @Value("${ratelimit.enabled:true}") boolean enabled,
        @Value("${ratelimit.session-limit:5}") int sessionLimit,
        @Value("${ratelimit.session-window-seconds:60}") int sessionWindowSeconds,
        @Value("${ratelimit.ip-limit:60}") int ipLimit,
        @Value("${ratelimit.ip-window-seconds:60}") int ipWindowSeconds
    ) {
        this.enabled = enabled;
        this.sessionLimit = Math.max(1, sessionLimit);
        this.sessionWindowMs = Math.max(1, sessionWindowSeconds) * 1000L;
        this.ipLimit = Math.max(1, ipLimit);
        this.ipWindowMs = Math.max(1, ipWindowSeconds) * 1000L;
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds, String reason) {
        public static RateLimitDecision allow() {
            return new RateLimitDecision(true, 0L, "");
        }

        public static RateLimitDecision reject(long retryAfterSeconds, String reason) {
            return new RateLimitDecision(false, Math.max(1L, retryAfterSeconds), reason);
        }
    }

    /**
     * Checks if the request should be allowed based on session and IP aggregate sliding windows.
     */
    public RateLimitDecision checkRateLimit(String sessionId, String clientIp) {
        if (!enabled) {
            return RateLimitDecision.allow();
        }

        long now = System.currentTimeMillis();

        // 1. Check session limit (per-browser session) first
        // If an individual session is throttled, it does not burn shared IP aggregate tokens for other attendees
        String effectiveSessionId = (sessionId != null && !sessionId.isBlank()) ? sessionId : clientIp;
        if (effectiveSessionId != null && !effectiveSessionId.isBlank()) {
            SlidingWindow sessWin = sessionWindows.computeIfAbsent(effectiveSessionId, k -> new SlidingWindow(sessionLimit, sessionWindowMs));
            RateLimitDecision sessDecision = sessWin.tryAcquire(now);
            if (!sessDecision.allowed()) {
                LOG.warn("Session rate limit exceeded for {}: retry in {}s", effectiveSessionId, sessDecision.retryAfterSeconds());
                return RateLimitDecision.reject(
                    sessDecision.retryAfterSeconds(),
                    "Rate limit reached (max " + sessionLimit + " requests/minute). Please wait " + sessDecision.retryAfterSeconds() + " seconds."
                );
            }
        }

        // 2. Check IP aggregate limit (protects against single-IP bot generating thousands of fake sessions)
        if (clientIp != null && !clientIp.isBlank()) {
            SlidingWindow ipWin = ipWindows.computeIfAbsent(clientIp, k -> new SlidingWindow(ipLimit, ipWindowMs));
            RateLimitDecision ipDecision = ipWin.tryAcquire(now);
            if (!ipDecision.allowed()) {
                LOG.warn("IP aggregate rate limit exceeded for {}: retry in {}s", clientIp, ipDecision.retryAfterSeconds());
                return RateLimitDecision.reject(
                    ipDecision.retryAfterSeconds(),
                    "Too many requests from this network. Please wait " + ipDecision.retryAfterSeconds() + " seconds."
                );
            }
        }

        // Periodically clean up stale records to prevent memory growth
        if (sessionWindows.size() > 2000) {
            pruneStale(sessionWindows, sessionWindowMs, now);
        }
        if (ipWindows.size() > 2000) {
            pruneStale(ipWindows, ipWindowMs, now);
        }

        return RateLimitDecision.allow();
    }

    private void pruneStale(Map<String, SlidingWindow> map, long windowMs, long now) {
        long maxIdleMs = windowMs * 3;
        map.entrySet().removeIf(e -> e.getValue().isStale(now, maxIdleMs));
    }

    public void reset() {
        sessionWindows.clear();
        ipWindows.clear();
    }

    public int activeSessions() {
        return sessionWindows.size();
    }

    public int activeIps() {
        return ipWindows.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Thread-safe O(1) circular buffer sliding-window rate limiter.
     */
    public static class SlidingWindow {
        private final int limit;
        private final long windowMs;
        private final long[] timestamps;
        private int head = 0;
        private int count = 0;
        private long lastAccessMs;

        public SlidingWindow(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
            this.timestamps = new long[limit];
            this.lastAccessMs = System.currentTimeMillis();
        }

        public synchronized RateLimitDecision tryAcquire(long now) {
            this.lastAccessMs = now;

            if (count < limit) {
                timestamps[count++] = now;
                return RateLimitDecision.allow();
            }

            // The oldest request in the current circular window is at index `head`
            long oldestTimestamp = timestamps[head];
            long elapsed = now - oldestTimestamp;

            if (elapsed < windowMs) {
                long retryAfterMs = windowMs - elapsed;
                long retryAfterSec = (long) Math.ceil(retryAfterMs / 1000.0);
                return RateLimitDecision.reject(retryAfterSec, "Limit exceeded");
            }

            // Window has slid: overwrite the oldest slot and advance head
            timestamps[head] = now;
            head = (head + 1) % limit;
            return RateLimitDecision.allow();
        }

        public synchronized boolean isStale(long now, long maxIdleMs) {
            return (now - lastAccessMs) > maxIdleMs;
        }
    }
}
