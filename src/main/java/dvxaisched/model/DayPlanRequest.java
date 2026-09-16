package dvxaisched.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DayPlanRequest(
    String day,
    String date,
    String dayLabel,
    String interests,
    String candidateTalks
) {
    @Override
    public String toString() {
        return """
            User interests: %s
            
            Day to schedule: %s (Date: %s, Day id: %s)
            
            Available Devoxx Belgium candidate talks for %s:
            %s
            
            Please select and return the conflict-free scheduled talks for %s.
            """.formatted(interests, dayLabel, date, day, dayLabel, candidateTalks, dayLabel);
    }
}
