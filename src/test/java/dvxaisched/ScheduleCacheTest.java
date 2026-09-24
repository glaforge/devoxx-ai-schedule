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

import dvxaisched.model.DaySchedule;
import dvxaisched.model.ScheduleResponse;
import dvxaisched.service.ScheduleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleCacheTest {

    private ScheduleCache cache;

    @BeforeEach
    void setUp() {
        // enabled=true, ttlMinutes=120, maxEntries=10
        cache = new ScheduleCache(true, 120, 10);
    }

    @Test
    void testQueryNormalization() {
        assertEquals("spring boot and ai", ScheduleCache.normalizeKey("  Spring   Boot  AND   AI "));
        assertEquals("kubernetes", ScheduleCache.normalizeKey("KUBERNETES"));
        assertEquals("", ScheduleCache.normalizeKey(null));
        assertEquals("", ScheduleCache.normalizeKey("   "));
    }

    @Test
    void testPutAndGet() {
        ScheduleResponse dummy = new ScheduleResponse(
            true,
            "Valid interests",
            "AI Theme",
            "Overview of AI",
            List.of(new DaySchedule("monday", "2026-10-05", "Monday", List.of()))
        );

        cache.put("Spring Boot & LangChain4j", dummy);

        // Case and space variations should all match
        assertTrue(cache.get("spring boot & langchain4j").isPresent());
        assertTrue(cache.get("   Spring   Boot   &   LangChain4j  ").isPresent());
        assertEquals(dummy, cache.get("Spring Boot & LangChain4j").get());

        // Unrelated query should miss
        assertTrue(cache.get("Quarkus").isEmpty());
    }

    @Test
    void testDoNotCacheRejectedResponses() {
        ScheduleResponse rejected = ScheduleResponse.rejected("Not technical");
        cache.put("Pizza recipes", rejected);

        assertTrue(cache.get("Pizza recipes").isEmpty(), "Rejected responses must not be cached");
    }

    @Test
    void testMaxEntriesEviction() {
        ScheduleResponse dummy = new ScheduleResponse(
            true, "OK", "Theme", "Overview", List.of()
        );

        // Put 15 items in a cache with maxEntries=10
        for (int i = 0; i < 15; i++) {
            cache.put("query " + i, dummy);
        }

        assertTrue(cache.size() <= 10, "Cache size should not exceed maxEntries limit");
    }

    @Test
    void testClear() {
        ScheduleResponse dummy = new ScheduleResponse(true, "OK", "Theme", "Overview", List.of());
        cache.put("topic", dummy);
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.get("topic").isEmpty());
    }
}
