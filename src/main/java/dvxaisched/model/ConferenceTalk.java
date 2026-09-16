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

package dvxaisched.model;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record ConferenceTalk(
    long id,
    String day,
    String date,
    String startTime,
    String endTime,
    String room,
    String title,
    String summary,
    @Nullable String description,
    String track,
    String sessionType,
    int totalFavourites,
    List<SpeakerInfo> speakers,
    @Nullable String url
) {
    public ConferenceTalk(
        long id,
        String day,
        String date,
        String startTime,
        String endTime,
        String room,
        String title,
        String summary,
        @Nullable String description,
        String track,
        String sessionType,
        int totalFavourites,
        List<SpeakerInfo> speakers
    ) {
        this(id, day, date, startTime, endTime, room, title, summary, description, track, sessionType, totalFavourites, speakers,
            buildDevoxxTalkUrl("dvbe26", id, title));
    }

    public ConferenceTalk(
        long id,
        String day,
        String date,
        String startTime,
        String endTime,
        String room,
        String title,
        String summary,
        String track,
        String sessionType,
        int totalFavourites,
        List<SpeakerInfo> speakers
    ) {
        this(id, day, date, startTime, endTime, room, title, summary, null, track, sessionType, totalFavourites, speakers,
            buildDevoxxTalkUrl("dvbe26", id, title));
    }

    public static String buildDevoxxTalkUrl(String eventSlug, long talkId, String title) {
        String slug = eventSlug != null && !eventSlug.isBlank() ? eventSlug : "dvbe26";
        if (title == null || title.isBlank()) {
            return "https://m.devoxx.com/events/" + slug + "/talks/" + talkId;
        }
        String cleanTitle = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return cleanTitle.isBlank()
            ? "https://m.devoxx.com/events/" + slug + "/talks/" + talkId
            : "https://m.devoxx.com/events/" + slug + "/talks/" + talkId + "/" + cleanTitle;
    }

    public String talkAbstract() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return summary != null ? summary : "";
    }

    public String speakersSummary() {
        if (speakers == null || speakers.isEmpty()) {
            return "TBA";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < speakers.size(); i++) {
            if (i > 0) sb.append(", ");
            SpeakerInfo s = speakers.get(i);
            sb.append(s.name());
            if (s.company() != null && !s.company().isBlank()) {
                sb.append(" (").append(s.company()).append(")");
            }
        }
        return sb.toString();
    }
}
