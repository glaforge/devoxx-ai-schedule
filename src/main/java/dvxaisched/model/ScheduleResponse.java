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
