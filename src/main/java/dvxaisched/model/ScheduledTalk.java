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
    @Nullable String talkAbstract
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
        String reason
    ) {
        this(talkId, day, date, startTime, endTime, room, title, speakers, track, sessionType, reason, null);
    }
}
