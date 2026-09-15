package dvxaisched;

import dvxaisched.model.ConferenceTalk;
import dvxaisched.service.DevoxxConferenceService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class DevoxxConferenceServiceTest {

    @Inject
    DevoxxConferenceService conferenceService;

    @Test
    void testTalksAreLoaded() {
        List<ConferenceTalk> allTalks = conferenceService.getAllTalks();
        assertNotNull(allTalks);
        assertFalse(allTalks.isEmpty(), "Conference talks should be loaded from devoxx-be-2026.json");
        assertTrue(allTalks.size() >= 190, "Expected around 200 talks, found " + allTalks.size());
    }

    @Test
    void testTracksExist() {
        List<String> tracks = conferenceService.getAllTracks();
        assertNotNull(tracks);
        assertTrue(tracks.contains("Agentic Engineering & Tooling"));
        assertTrue(tracks.contains("Java Language & Platform"));
        assertTrue(tracks.contains("Mind the Geek"));
    }

    @Test
    void testSearchTalks() {
        List<ConferenceTalk> results = conferenceService.searchTalks("agent loop", null, 10);
        assertFalse(results.isEmpty(), "Should find talks matching 'agent loop'");
        assertTrue(results.stream().anyMatch(t -> t.title().toLowerCase().contains("loop") || t.title().toLowerCase().contains("agent")));
    }

    @Test
    void testDayFiltering() {
        List<ConferenceTalk> mondayTalks = conferenceService.getTalksByDay("monday");
        assertFalse(mondayTalks.isEmpty());
        for (ConferenceTalk t : mondayTalks) {
            assertEquals("monday", t.day().toLowerCase());
        }
    }
}
