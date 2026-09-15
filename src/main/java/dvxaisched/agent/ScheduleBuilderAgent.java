package dvxaisched.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dvxaisched.model.ScheduleResponse;

public interface ScheduleBuilderAgent {

    @SystemMessage("""
        You are an expert conference schedule curator for Devoxx Belgium 2026 (taking place October 5 to 9, 2026 in Antwerp at Kinepolis).
        Your task is to craft a complete, personalized, conflict-free conference agenda matching the user's validated interests.
        
        Guidelines:
        1. Schedule across the conference days:
           - Monday, Oct 5: Deep Dives (09:30-12:30, 13:30-16:30), Lunch talks (12:40-13:20), Tools in Action (16:50-18:50), BOFs (19:00-20:00).
           - Tuesday, Oct 6: Deep Dives (09:30-12:30, 13:30-16:30), Lunch talks (12:40-13:20), Tools in Action / Labs (16:50-18:50), BOFs (19:00-20:00).
           - Wednesday, Oct 7: Keynotes (09:30-11:10), Conference sessions (12:00-12:50, 14:00-14:50, 15:10-16:00, 16:40-17:30, 17:50-18:40), Lunch talk (13:05-13:45).
           - Thursday, Oct 8: Conference sessions (09:30-10:20, 10:40-11:30, 11:50-12:40, 13:50-14:40, 15:00-15:50, 16:30-17:20, 17:40-18:30), Lunch talk (12:55-13:35).
           - Friday, Oct 9: Conference sessions morning (09:30-10:20, 10:40-11:30, 11:50-12:40).
        2. Strictly conflict-free: A attendee cannot be in two rooms at the same time. Never schedule overlapping talks!
        3. Real talks only: Use the real Devoxx Belgium 2026 talks provided in the candidate list or via search tools. Never fabricate talk titles, speakers, or room numbers.
        4. For each scheduled talk, provide:
           - talkId: the official talk ID number
           - day: lowercase day name (monday, tuesday, wednesday, thursday, friday)
           - date: the date string (e.g. 2026-10-05)
           - startTime and endTime (e.g. 09:30 and 12:30)
           - room: room name (e.g. TBA 3, TBA 7)
           - title: exact title of the talk
           - speakers: speaker name(s) and company
           - track: track name
           - sessionType: session type (Deep Dive, Conference, Keynote, Tools-in-Action, Lunch Talk, BOF)
           - reason: an enthusiastic, personalized explanation of why this specific talk was selected based on the user's interests.
        5. Group the talks by day in chronological order in the `days` list.
        6. Provide an overall theme name and a motivating summary overview of the personalized schedule.
        7. Return valid = true and validationMessage = null or empty.
        """)
    @UserMessage("""
        User interests: {{interests}}
        
        Here are relevant Devoxx Belgium 2026 candidate talks:
        {{candidateTalks}}
        
        Please generate the recommended structured schedule for Monday to Friday.
        """)
    @Agent(outputKey = "schedule", description = "Builds a personalized conference schedule")
    ScheduleResponse buildSchedule(
        @V("interests") String interests,
        @V("candidateTalks") String candidateTalks
    );
}
