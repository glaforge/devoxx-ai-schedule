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

@Serdeable
public record ScheduledTalk(
    long talkId,
    String day,
    String date,
    String startTime,
    String endTime,
    String room,
    String title,
    String speakers,
    String track,
    String sessionType,
    String reason,
    @Nullable String talkAbstract,
    @Nullable String url
) {
    public ScheduledTalk(
        long talkId,
        String day,
        String date,
        String startTime,
        String endTime,
        String room,
        String title,
        String speakers,
        String track,
        String sessionType,
        String reason,
        @Nullable String talkAbstract
    ) {
        this(talkId, day, date, startTime, endTime, room, title, speakers, track, sessionType, reason, talkAbstract,
            ConferenceTalk.buildDevoxxTalkUrl("dvbe26", talkId, title));
    }

    public ScheduledTalk(
        long talkId,
        String day,
        String date,
        String startTime,
        String endTime,
        String room,
        String title,
        String speakers,
        String track,
        String sessionType,
        String reason
    ) {
        this(talkId, day, date, startTime, endTime, room, title, speakers, track, sessionType, reason, null,
            ConferenceTalk.buildDevoxxTalkUrl("dvbe26", talkId, title));
    }
}
