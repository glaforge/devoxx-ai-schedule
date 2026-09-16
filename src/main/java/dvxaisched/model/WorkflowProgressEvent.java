/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
