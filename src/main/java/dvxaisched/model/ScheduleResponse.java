package dvxaisched.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record ScheduleResponse(
    boolean valid,
    String validationMessage,
    String theme,
    String overview,
    List<DaySchedule> days
) {
    public ScheduleResponse {
        if (days == null) {
            days = List.of();
        }
    }

    public static ScheduleResponse rejected(String message) {
        return new ScheduleResponse(false, message, null, null, List.of());
    }
}
