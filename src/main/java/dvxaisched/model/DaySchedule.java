package dvxaisched.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record DaySchedule(
    String day,
    String date,
    String dayLabel,
    List<ScheduledTalk> talks
) {
}
