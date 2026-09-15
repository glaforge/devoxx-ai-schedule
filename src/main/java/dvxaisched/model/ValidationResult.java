package dvxaisched.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ValidationResult(
    boolean valid,
    String reason,
    String sanitizedInterests
) {
}
