package dvxaisched.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WorkflowProgressEvent(
    String stage,
    String agent,
    String message,
    Long durationMs,
    ScheduleResponse schedule
) {
    public static WorkflowProgressEvent of(String stage, String agent, String message) {
        return new WorkflowProgressEvent(stage, agent, message, null, null);
    }

    public static WorkflowProgressEvent of(String stage, String agent, String message, Long durationMs) {
        return new WorkflowProgressEvent(stage, agent, message, durationMs, null);
    }

    public static WorkflowProgressEvent complete(ScheduleResponse schedule, Long durationMs) {
        return new WorkflowProgressEvent("complete", "Complete", "Your personalized Devoxx Belgium 2026 schedule is ready!", durationMs, schedule);
    }

    public static WorkflowProgressEvent rejected(String reason, Long durationMs) {
        return new WorkflowProgressEvent("rejected", "Agent 1: Guardrail Validator", reason, durationMs, ScheduleResponse.rejected(reason));
    }
}
