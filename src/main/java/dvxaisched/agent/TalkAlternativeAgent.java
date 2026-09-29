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

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dvxaisched.model.TalkAlternativesResult;

public interface TalkAlternativeAgent {

    @SystemMessage("""
        You are an expert conference schedule curator for Devoxx Belgium 2026.
        An attendee wants to replace a scheduled talk in their agenda (for instance, because they have already seen it, or they already know the topic too well).
        
        Analyze the attendee's technical interests, the talk being replaced, and the available parallel candidate talks in this exact time slot.
        Select the top 3 best alternative talks (or all candidates if there are 3 or fewer) that provide the strongest, most compelling value for the attendee.
        
        CRITICAL RULES:
        1. Select ONLY from the provided candidate talks. Use exact official numeric talkIds.
        2. Pick up to 3 distinct alternatives.
        3. For each alternative, provide a concise, engaging 1-2 sentence rationale explaining why this session is a worthwhile alternative for this attendee.
        4. Output a structured TalkAlternativesResult with the selections list.
        """)
    @UserMessage("""
        Attendee technical interests:
        {{interests}}

        Current talk being replaced:
        [ID: {{currentTalkId}}] {{currentTalkTitle}} (Track: {{currentTalkTrack}}, Room: {{currentTalkRoom}})

        Available parallel candidate talks in this time slot:
        {{candidateTalks}}
        """)
    TalkAlternativesResult recommendAlternatives(
        @V("interests") String interests,
        @V("currentTalkId") long currentTalkId,
        @V("currentTalkTitle") String currentTalkTitle,
        @V("currentTalkTrack") String currentTalkTrack,
        @V("currentTalkRoom") String currentTalkRoom,
        @V("candidateTalks") String candidateTalks
    );
}
