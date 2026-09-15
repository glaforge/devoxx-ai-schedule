package dvxsaiched.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SpeakerInfo(
    long id,
    String name,
    String company,
    String bio
) {
}
