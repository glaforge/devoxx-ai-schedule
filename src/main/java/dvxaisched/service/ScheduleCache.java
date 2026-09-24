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

import dvxaisched.model.ScheduleResponse;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ScheduleCache {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleCache.class);

    private final boolean enabled;
    private final long ttlMs;
    private final int maxEntries;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record CacheEntry(ScheduleResponse response, long createdAtMs) {
        public boolean isExpired(long ttlMs, long now) {
            return (now - createdAtMs) > ttlMs;
        }
    }

    public ScheduleCache(
        @Value("${cache.schedule.enabled:true}") boolean enabled,
        @Value("${cache.schedule.ttl-minutes:120}") int ttlMinutes,
        @Value("${cache.schedule.max-entries:500}") int maxEntries
    ) {
        this.enabled = enabled;
        this.ttlMs = Math.max(1, ttlMinutes) * 60 * 1000L;
        this.maxEntries = Math.max(10, maxEntries);
    }

    /**
     * Looks up a cached ScheduleResponse for the given query interests.
     */
    public Optional<ScheduleResponse> get(String rawInterests) {
        if (!enabled) {
            return Optional.empty();
        }

        String key = normalizeKey(rawInterests);
        if (key.isBlank()) {
            return Optional.empty();
        }

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        if (entry.isExpired(ttlMs, now)) {
            cache.remove(key);
            return Optional.empty();
        }

        return Optional.of(entry.response());
    }

    /**
     * Caches a valid ScheduleResponse.
     */
    public void put(String rawInterests, ScheduleResponse response) {
        if (!enabled || response == null || !response.valid()) {
            return;
        }

        String key = normalizeKey(rawInterests);
        if (key.isBlank()) {
            return;
        }

        if (cache.size() >= maxEntries) {
            evictExpiredOrOldest();
        }

        cache.put(key, new CacheEntry(response, System.currentTimeMillis()));
        LOG.debug("Cached synthesized schedule for query: '{}' (cache size: {})", key, cache.size());
    }

    /**
     * Normalizes query string: lowercases, trims, and collapses multiple whitespace characters.
     */
    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private void evictExpiredOrOldest() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().isExpired(ttlMs, now));

        if (cache.size() >= maxEntries) {
            // Evict oldest 20% entries
            int toEvict = Math.max(1, maxEntries / 5);
            cache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().createdAtMs()))
                .limit(toEvict)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(cache::remove);
        }
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
