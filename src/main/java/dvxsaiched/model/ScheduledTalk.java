package dvxsaiched.model;

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
    String reason
) {
}
