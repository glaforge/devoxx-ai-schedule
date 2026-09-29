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

import dvxaisched.model.ConferenceTalk;
import dvxaisched.model.TalkAlternativeRequest;
import dvxaisched.model.TalkAlternativesResponse;
import dvxaisched.service.DevoxxConferenceService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class ScheduleAlternativesTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    DevoxxConferenceService conferenceService;

    @Test
    void testGetAlternativesForConferenceTalk() {
        // Target a standard Wednesday slot
        List<ConferenceTalk> wednesdayTalks = conferenceService.getTalksByDay("wednesday");
        ConferenceTalk targetTalk = wednesdayTalks.stream()
            .filter(t -> "14:00".equals(t.startTime()) && "14:50".equals(t.endTime()))
            .findFirst()
            .orElseThrow();

        TalkAlternativeRequest request = new TalkAlternativeRequest(
            "Java language, virtual threads, and performance",
            targetTalk.id()
        );

        HttpResponse<TalkAlternativesResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/schedule/alternatives", request),
            TalkAlternativesResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatus());
        TalkAlternativesResponse body = response.body();
        assertNotNull(body);
        assertEquals(targetTalk.id(), body.replacedTalkId());
        assertTrue(body.hasAlternatives());
        assertNotNull(body.alternatives());
        assertFalse(body.alternatives().isEmpty());
        assertTrue(body.alternatives().size() <= 3, "Should return at most 3 alternatives");

        for (var alt : body.alternatives()) {
            assertNotEquals(targetTalk.id(), alt.talkId());
            assertEquals("wednesday", alt.day().toLowerCase());
            assertEquals(targetTalk.startTime(), alt.startTime());
            assertEquals(targetTalk.endTime(), alt.endTime());
            assertNotNull(alt.reason(), "Each alternative must have a rationale");
            assertFalse(alt.reason().isBlank());
        }
    }

    @Test
    void testGetAlternativesForPlenaryKeynote() {
        // Target a plenary keynote with no peers
        List<ConferenceTalk> wednesdayTalks = conferenceService.getTalksByDay("wednesday");
        ConferenceTalk keynote = wednesdayTalks.stream()
            .filter(t -> "Keynote".equalsIgnoreCase(t.sessionType()))
            .findFirst()
            .orElseThrow();

        TalkAlternativeRequest request = new TalkAlternativeRequest(
            "AI agents",
            keynote.id()
        );

        HttpResponse<TalkAlternativesResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/schedule/alternatives", request),
            TalkAlternativesResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatus());
        TalkAlternativesResponse body = response.body();
        assertNotNull(body);
        assertFalse(body.hasAlternatives());
        assertTrue(body.safeAlternatives().isEmpty());
        assertNotNull(body.message());
        assertTrue(body.message().toLowerCase().contains("plenary"));
    }

    @Test
    void testInvalidTalkIdRequest() {
        TalkAlternativeRequest request = new TalkAlternativeRequest("AI", -1);
        try {
            client.toBlocking().exchange(
                HttpRequest.POST("/api/schedule/alternatives", request),
                TalkAlternativesResponse.class
            );
            fail("Expected 400 Bad Request");
        } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
    }
}
