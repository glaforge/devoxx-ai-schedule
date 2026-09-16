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
public record DayPlanRequest(
    String day,
    String date,
    String dayLabel,
    String interests,
    String candidateTalks
) {
    @Override
    public String toString() {
        return """
            User interests: %s
            
            Day to schedule: %s (Date: %s, Day id: %s)
            
            Available Devoxx Belgium candidate talks for %s:
            %s
            
            Please select and return the conflict-free scheduled talks for %s.
            """.formatted(interests, dayLabel, date, day, dayLabel, candidateTalks, dayLabel);
    }
}
