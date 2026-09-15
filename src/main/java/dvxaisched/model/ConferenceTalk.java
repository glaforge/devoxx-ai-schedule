package dvxaisched.model;

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
    String track,
    String sessionType,
    int totalFavourites,
    List<SpeakerInfo> speakers
) {
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
