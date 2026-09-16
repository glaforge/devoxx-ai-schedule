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

package dvxaisched.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dvxaisched.model.ConferenceTalk;
import dvxaisched.service.DevoxxConferenceService;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class DevoxxConferenceTools {

    private final DevoxxConferenceService conferenceService;

    public DevoxxConferenceTools(DevoxxConferenceService conferenceService) {
        this.conferenceService = conferenceService;
    }

    @Tool("Search Devoxx Belgium 2026 conference talks by topic keywords, with optional day filter (monday, tuesday, wednesday, thursday, friday)")
    public String searchTalks(
        @P("Search keywords (e.g. 'agent', 'loom', 'security', 'spring', 'valhalla')") String query,
        @P("Day of conference (monday, tuesday, wednesday, thursday, friday) or empty for all days") String day
    ) {
        List<ConferenceTalk> results = conferenceService.searchTalks(query, day, 15);
        if (results.isEmpty()) {
            return "No talks found matching '" + query + "' on " + (day != null ? day : "any day") + ".";
        }
        return conferenceService.formatTalksForPrompt(results);
    }

    @Tool("Get all official conference tracks at Devoxx Belgium 2026")
    public List<String> getConferenceTracks() {
        return conferenceService.getAllTracks();
    }

    @Tool("Get talks for a specific conference track and day at Devoxx Belgium 2026")
    public String getTalksByTrack(
        @P("Track name (e.g., 'Agentic Engineering & Tooling', 'Java Language & Platform', 'Mind the Geek')") String track,
        @P("Day of conference (monday, tuesday, wednesday, thursday, friday) or empty for all days") String day
    ) {
        List<ConferenceTalk> results = conferenceService.getTalksByTrack(track, day);
        if (results.isEmpty()) {
            return "No talks found for track '" + track + "' on " + (day != null ? day : "any day") + ".";
        }
        return conferenceService.formatTalksForPrompt(results);
    }

    @Tool("Get all talks scheduled for a given day (monday, tuesday, wednesday, thursday, friday)")
    public String getTalksForDay(
        @P("Day of conference: monday, tuesday, wednesday, thursday, friday") String day
    ) {
        List<ConferenceTalk> results = conferenceService.getTalksByDay(day);
        if (results.isEmpty()) {
            return "No talks found for day " + day + ".";
        }
        return conferenceService.formatTalksForPrompt(results);
    }

    @Tool("Get the most popular / favorited talks across Devoxx Belgium 2026")
    public String getTopFavoritedTalks(
        @P("Maximum number of talks to return (e.g. 10)") int limit
    ) {
        List<ConferenceTalk> results = conferenceService.getTopTalks(limit > 0 ? limit : 10);
        return conferenceService.formatTalksForPrompt(results);
    }
}
