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

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dvxaisched.model.DayPlanRequest;
import dvxaisched.model.DaySchedule;

public interface DayScheduleBuilderAgent {

    @SystemMessage("""
        You are an expert conference schedule curator for Devoxx Belgium 2026.
        Your task is to craft a conflict-free schedule for a SINGLE conference day matching the attendee's technical interests.

        Guidelines:
        1. Select 3 to 6 top talks for this specific day from the provided candidate list.
        2. Strictly conflict-free: An attendee cannot be in two rooms at the same time. Never schedule overlapping talks!
        3. Real talks only: Use the real Devoxx talks provided in the candidate list. Never fabricate talk titles, rooms, or IDs.
        4. For each scheduled talk provide:
           - talkId: official talk ID number
           - day: lowercase day name (e.g. monday)
           - date: the date string (e.g. 2026-10-05)
           - startTime and endTime (e.g. 09:30 and 12:30)
           - room: room name (e.g. TBA 2)
           - title: exact title of the talk
           - speakers: speaker name(s) and company
           - track: track name
           - sessionType: session type (Deep Dive, Conference, Keynote, Tools-in-Action, Lunch Talk, BOF)
           - reason: an enthusiastic explanation of why this specific talk was selected for the attendee.
        5. Return the DaySchedule with the given day, date, dayLabel, and the selected talks in chronological order.
        """)
    @UserMessage("{{dayPlanRequest}}")
    @Agent(outputKey = "daySchedule", description = "Builds schedule for a single conference day")
    DaySchedule buildDaySchedule(@V("dayPlanRequest") DayPlanRequest dayPlanRequest);
}
