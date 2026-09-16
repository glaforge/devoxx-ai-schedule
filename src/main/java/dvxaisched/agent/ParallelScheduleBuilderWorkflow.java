package dvxaisched.agent;

import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.service.V;
import dvxaisched.model.DayPlanRequest;
import dvxaisched.model.DaySchedule;

import java.util.List;

public interface ParallelScheduleBuilderWorkflow {

    @ParallelMapperAgent(
        outputKey = "daySchedules",
        subAgent = DayScheduleBuilderAgent.class,
        itemsProvider = "dayRequests"
    )
    List<DaySchedule> scheduleDays(@V("dayRequests") List<DayPlanRequest> dayRequests);
}
