package dvxaisched.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScheduleRequest(
    String interests
) {
}
