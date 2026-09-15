package dvxaisched.service;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dvxaisched.agent.DevoxxConferenceTools;
import dvxaisched.agent.InterestValidatorAgent;
import dvxaisched.agent.ScheduleBuilderAgent;
import dvxaisched.model.ConferenceTalk;
import dvxaisched.model.ScheduleResponse;
import dvxaisched.model.ValidationResult;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dvxaisched.model.WorkflowProgressEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
public class DevoxxAgentWorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(DevoxxAgentWorkflowService.class);

    private final ChatModel chatModel;
    private final DevoxxConferenceService conferenceService;
    private final DevoxxConferenceTools conferenceTools;

    private final InterestValidatorAgent validatorAgent;
    private final ScheduleBuilderAgent scheduleBuilderAgent;

    public DevoxxAgentWorkflowService(
        ChatModel chatModel,
        DevoxxConferenceService conferenceService,
        DevoxxConferenceTools conferenceTools
    ) {
        this.chatModel = chatModel;
        this.conferenceService = conferenceService;
        this.conferenceTools = conferenceTools;

        LOG.info("Initializing LangChain4j Agentic System with 2-agent sequence...");

        AgentListener agentObservabilityListener = new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                LOG.info("[AgentListener.beforeAgentInvocation] Agent '{}' starting", request.agentName());
                @SuppressWarnings("unchecked")
                Consumer<WorkflowProgressEvent> progress = request.agenticScope().executionContextAs(Consumer.class);
                if (progress != null) {
                    if (request.agentName().toLowerCase().contains("validator") || request.agentName().equals("validate")) {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent1_start",
                            "Agent 1: Guardrail Validator",
                            "Evaluating prompt safety, prompt injection defenses, and technical relevance..."
                        ));
                    } else {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_start",
                            "Agent 2: Schedule Optimizer",
                            "Curating conflict-free 5-day timetable with Gemini 3.8 Flash..."
                        ));
                    }
                }
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                LOG.info("[AgentListener.afterAgentInvocation] Agent '{}' finished", response.agentName());
                @SuppressWarnings("unchecked")
                Consumer<WorkflowProgressEvent> progress = response.agenticScope().executionContextAs(Consumer.class);
                if (progress != null) {
                    if (response.agentName().toLowerCase().contains("validator") || response.agentName().equals("validate")) {
                        if (response.output() instanceof ValidationResult vr && vr.valid()) {
                            progress.accept(WorkflowProgressEvent.of(
                                "agent1_done",
                                "Agent 1: Guardrail Validator",
                                "Input validated successfully! Topics: " + (vr.sanitizedInterests() != null ? vr.sanitizedInterests() : "")
                            ));
                        }
                    } else {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_done",
                            "Agent 2: Schedule Optimizer",
                            "5-day conference timetable curated successfully!"
                        ));
                    }
                }
            }

            @Override
            public void beforeAgentToolExecution(BeforeAgentToolExecution tool) {
                String toolName = (tool.toolExecution() != null && tool.toolExecution().request() != null)
                    ? tool.toolExecution().request().name()
                    : "tool";
                LOG.info("[AgentListener.beforeAgentToolExecution] Tool: {}", toolName);
                @SuppressWarnings("unchecked")
                Consumer<WorkflowProgressEvent> progress = tool.agenticScope().executionContextAs(Consumer.class);
                if (progress != null) {
                    progress.accept(WorkflowProgressEvent.of(
                        "tool_call",
                        "Agent 2: Schedule Optimizer",
                        "Searching session catalog via tool '" + toolName + "'..."
                    ));
                }
            }

            @Override
            public boolean inheritedBySubagents() {
                return true;
            }
        };

        // Agent 1: Interest Validation & Guardrail Agent
        this.validatorAgent = AgenticServices.agentBuilder(InterestValidatorAgent.class)
            .chatModel(chatModel)
            .outputKey("validationResult")
            .listener(agentObservabilityListener)
            .build();

        // Agent 2: Conference Schedule Builder Agent (equipped with conference tools)
        this.scheduleBuilderAgent = AgenticServices.agentBuilder(ScheduleBuilderAgent.class)
            .chatModel(chatModel)
            .outputKey("schedule")
            .tools(conferenceTools)
            .listener(agentObservabilityListener)
            .build();

        LOG.info("LangChain4j 2-agent system initialized successfully with observability listener.");
    }

    /**
     * Executes the 2-agent sequence without streaming callback.
     */
    public ScheduleResponse processScheduleRequest(String userInterests) {
        return processScheduleRequestWithProgress(userInterests, null);
    }

    /**
     * Executes the 2-agent sequence with a live progress listener hooked into the AgenticScope.
     */
    public ScheduleResponse processScheduleRequestWithProgress(
        String userInterests,
        Consumer<WorkflowProgressEvent> progressConsumer
    ) {
        if (userInterests == null || userInterests.trim().isBlank()) {
            ScheduleResponse rejection = ScheduleResponse.rejected("Please enter one or more topics, technologies, or themes you are interested in.");
            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.rejected(rejection.validationMessage(), 0L));
            }
            return rejection;
        }

        long totalStartTime = System.currentTimeMillis();
        String rawInput = userInterests.trim();

        // Establish an AgenticScope across the 2-agent sequence
        dev.langchain4j.agentic.scope.DefaultAgenticScope scope =
            dev.langchain4j.agentic.scope.DefaultAgenticScope.ephemeralAgenticScope();
        if (progressConsumer != null) {
            scope.writeExecutionContext(Consumer.class, progressConsumer);
        }
        dev.langchain4j.invocation.LangChain4jManaged.setCurrent(
            Map.of(dev.langchain4j.agentic.scope.AgenticScope.class, scope)
        );

        try {
            LOG.info("Step 1 [Agent 1 - Validator]: Validating input '{}'", rawInput);
            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "agent1_start",
                    "Agent 1: Guardrail Validator",
                    "Analyzing input for safety, prompt injection defenses, and technical relevance..."
                ));
            }

            long a1StartTime = System.currentTimeMillis();
            ValidationResult validation;
            try {
                validation = validatorAgent.validate(rawInput);
            } catch (Exception e) {
                LOG.error("Error executing InterestValidatorAgent", e);
                validation = performFallbackValidation(rawInput);
            }
            long a1Duration = System.currentTimeMillis() - a1StartTime;

            LOG.info("Agent 1 Result in {}ms: valid={}, reason='{}', sanitized='{}'",
                a1Duration, validation.valid(), validation.reason(), validation.sanitizedInterests());

            // Short-circuit if validation fails
            if (!validation.valid()) {
                String rejectMsg = (validation.reason() != null && !validation.reason().isBlank())
                    ? validation.reason()
                    : "The request could not be accepted. Please enter topics related to software engineering or technology.";
                ScheduleResponse rejection = new ScheduleResponse(false, rejectMsg, rawInput, null, List.of());
                if (progressConsumer != null) {
                    progressConsumer.accept(WorkflowProgressEvent.rejected(rejectMsg, a1Duration));
                }
                return rejection;
            }

            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "agent1_done",
                    "Agent 1: Guardrail Validator",
                    String.format(Locale.US, "Passed guardrail in %.1fs! Topics: %s", a1Duration / 1000.0,
                        (validation.sanitizedInterests() != null ? validation.sanitizedInterests() : rawInput)),
                    a1Duration
                ));
            }

            // Step 2: Query candidate talks for the validated interests
            String query = (validation.sanitizedInterests() != null && !validation.sanitizedInterests().isBlank())
                ? validation.sanitizedInterests()
                : rawInput;

            LOG.info("Step 2 [Agent 2 - Schedule Builder]: Building schedule for query '{}'", query);
            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "indexing",
                    "Devoxx Catalog Indexer",
                    "Searching 201 Devoxx Belgium 2026 sessions for candidate talks..."
                ));
            }

            List<ConferenceTalk> matched = new ArrayList<>(conferenceService.searchTalks(query, null, 35));
            if (matched.size() < 15) {
                for (ConferenceTalk top : conferenceService.getTopTalks(20)) {
                    if (matched.stream().noneMatch(t -> t.id() == top.id())) {
                        matched.add(top);
                    }
                }
            }

            String candidatePrompt = conferenceService.formatTalksForPrompt(matched);

            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "agent2_start",
                    "Agent 2: Schedule Optimizer",
                    "Synthesizing personalized, conflict-free 5-day agenda with Gemini 3.8 Flash..."
                ));
            }

            long a2StartTime = System.currentTimeMillis();
            ScheduleResponse finalResponse = null;

            try {
                ScheduleResponse response = scheduleBuilderAgent.buildSchedule(query, candidatePrompt);
                if (response != null) {
                    finalResponse = new ScheduleResponse(
                        true,
                        null,
                        response.theme() != null ? response.theme() : query,
                        response.overview(),
                        response.days() != null ? response.days() : List.of()
                    );
                }
            } catch (Exception e) {
                LOG.error("Error executing ScheduleBuilderAgent", e);
            }

            if (finalResponse == null) {
                finalResponse = buildFallbackSchedule(query, matched);
            }

            long a2Duration = System.currentTimeMillis() - a2StartTime;
            long totalDuration = System.currentTimeMillis() - totalStartTime;

            LOG.info("Agent 2 finished in {}ms. Total curation duration: {}ms", a2Duration, totalDuration);

            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "agent2_done",
                    "Agent 2: Schedule Optimizer",
                    String.format(Locale.US, "5-day timetable synthesized in %.1fs!", a2Duration / 1000.0),
                    a2Duration
                ));
                progressConsumer.accept(WorkflowProgressEvent.complete(finalResponse, totalDuration));
            }

            return finalResponse;
        } finally {
            dev.langchain4j.invocation.LangChain4jManaged.removeCurrent();
        }
    }

    private ValidationResult performFallbackValidation(String input) {
        String lower = input.toLowerCase();
        if (lower.contains("ignore previous") || lower.contains("system prompt") || lower.contains("you are now")) {
            return new ValidationResult(false, "Instruction override or prompt injection attempt detected.", null);
        }
        return new ValidationResult(true, null, input);
    }

    private ScheduleResponse buildFallbackSchedule(String query, List<ConferenceTalk> talks) {
        LOG.info("Building fallback structured schedule for query '{}'", query);
        List<dvxaisched.model.DaySchedule> days = new ArrayList<>();
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday"};
        String[] dates = {"2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"};
        String[] labels = {
            "Monday, Oct 5 (Deep Dives & Labs)",
            "Tuesday, Oct 6 (Deep Dives & Labs)",
            "Wednesday, Oct 7 (Keynotes & Conference)",
            "Thursday, Oct 8 (Conference)",
            "Friday, Oct 9 (Conference - Half Day)"
        };

        for (int i = 0; i < dayNames.length; i++) {
            String day = dayNames[i];
            List<ConferenceTalk> dayTalks = talks.stream()
                .filter(t -> t.day().equalsIgnoreCase(day))
                .toList();

            List<dvxaisched.model.ScheduledTalk> scheduled = new ArrayList<>();
            for (ConferenceTalk t : dayTalks) {
                scheduled.add(new dvxaisched.model.ScheduledTalk(
                    t.id(),
                    t.day(),
                    t.date(),
                    t.startTime(),
                    t.endTime(),
                    t.room(),
                    t.title(),
                    t.speakersSummary(),
                    t.track(),
                    t.sessionType(),
                    "Matches your interest in " + query
                ));
            }
            days.add(new dvxaisched.model.DaySchedule(day, dates[i], labels[i], scheduled));
        }

        return new ScheduleResponse(
            true,
            null,
            "Devoxx Belgium 2026: " + query,
            "Personalized schedule curated for interests in " + query,
            days
        );
    }
}
