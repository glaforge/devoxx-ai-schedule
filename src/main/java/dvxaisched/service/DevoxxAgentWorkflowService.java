package dvxaisched.service;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.ChatModel;
import dvxaisched.agent.DayScheduleBuilderAgent;
import dvxaisched.agent.DevoxxConferenceTools;
import dvxaisched.agent.InterestValidatorAgent;
import dvxaisched.agent.ParallelScheduleBuilderWorkflow;
import dvxaisched.agent.ScheduleBuilderAgent;
import dvxaisched.model.ConferenceTalk;
import dvxaisched.model.DayPlanRequest;
import dvxaisched.model.DaySchedule;
import dvxaisched.model.ScheduleResponse;
import dvxaisched.model.ScheduledTalk;
import dvxaisched.model.ValidationResult;
import dvxaisched.model.WorkflowProgressEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Singleton
public class DevoxxAgentWorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(DevoxxAgentWorkflowService.class);

    private final ChatModel chatModel;
    private final DevoxxConferenceService conferenceService;
    private final DevoxxConferenceTools conferenceTools;

    private final InterestValidatorAgent validatorAgent;
    private final DayScheduleBuilderAgent dayScheduleAgent;
    private final ParallelScheduleBuilderWorkflow parallelScheduleWorkflow;
    private final ScheduleBuilderAgent scheduleBuilderAgent;

    public DevoxxAgentWorkflowService(
        ChatModel chatModel,
        DevoxxConferenceService conferenceService,
        DevoxxConferenceTools conferenceTools
    ) {
        this.chatModel = chatModel;
        this.conferenceService = conferenceService;
        this.conferenceTools = conferenceTools;

        LOG.info("Initializing LangChain4j Agentic System with Parallel Mapper workflow...");

        AgentListener agentObservabilityListener = new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                String agentName = request.agentName() != null ? request.agentName() : "";
                LOG.info("[AgentListener.beforeAgentInvocation] Agent '{}' starting", agentName);
                @SuppressWarnings("unchecked")
                Consumer<WorkflowProgressEvent> progress = request.agenticScope().executionContextAs(Consumer.class);
                if (progress != null) {
                    if (agentName.toLowerCase().contains("validator") || agentName.equals("validate")) {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent1_start",
                            "Agent 1: Guardrail Validator",
                            "Evaluating prompt safety, prompt injection defenses, and technical relevance..."
                        ));
                    } else if (agentName.toLowerCase().contains("parallel") || agentName.toLowerCase().contains("day")) {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_start",
                            "Parallel Day Optimizers",
                            "Dispatching 5 parallel Gemini workers to synthesize Mon–Fri concurrently..."
                        ));
                    } else {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_start",
                            "Agent 2: Schedule Optimizer",
                            "Curating conflict-free conference timetable with Gemini 3.8 Flash..."
                        ));
                    }
                }
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                String agentName = response.agentName() != null ? response.agentName() : "";
                LOG.info("[AgentListener.afterAgentInvocation] Agent '{}' finished", agentName);
                @SuppressWarnings("unchecked")
                Consumer<WorkflowProgressEvent> progress = response.agenticScope().executionContextAs(Consumer.class);
                if (progress != null) {
                    if (agentName.toLowerCase().contains("validator") || agentName.equals("validate")) {
                        if (response.output() instanceof ValidationResult vr && vr.valid()) {
                            progress.accept(WorkflowProgressEvent.of(
                                "agent1_done",
                                "Agent 1: Guardrail Validator",
                                "Input validated successfully! Topics: " + (vr.sanitizedInterests() != null ? vr.sanitizedInterests() : "")
                            ));
                        }
                    } else if (agentName.toLowerCase().contains("day") || agentName.contains("_")) {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_progress",
                            "Parallel Day Optimizer",
                            "Curated day schedule concurrently"
                        ));
                    } else {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_done",
                            "Agent 2: Schedule Optimizer",
                            "Conference timetable curated successfully!"
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
            public void afterAgentToolExecution(AfterAgentToolExecution tool) {
                LOG.info("[AgentListener.afterAgentToolExecution] Tool finished");
                @SuppressWarnings("unchecked")
                Consumer<WorkflowProgressEvent> progress = tool.agenticScope().executionContextAs(Consumer.class);
                if (progress != null) {
                    progress.accept(WorkflowProgressEvent.of(
                        "agent2_start",
                        "Agent 2: Schedule Optimizer",
                        "Catalog search completed. Synthesizing schedule..."
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

        // Sub-agent: Single Day Schedule Builder
        this.dayScheduleAgent = AgenticServices.agentBuilder(DayScheduleBuilderAgent.class)
            .chatModel(chatModel)
            .outputKey("daySchedule")
            .listener(agentObservabilityListener)
            .build();

        // Parallel Mapper: 5 concurrent day workers executed via virtual threads
        this.parallelScheduleWorkflow = AgenticServices.parallelMapperBuilder(ParallelScheduleBuilderWorkflow.class)
            .subAgents(List.of(dayScheduleAgent))
            .itemsProvider("dayRequests")
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .listener(agentObservabilityListener)
            .errorHandler(errorContext -> {
                String agentName = errorContext.agentName() != null ? errorContext.agentName() : "dayAgent";
                String exMsg = errorContext.exception() != null ? errorContext.exception().getMessage() : "unknown error";

                String retryKey = "retry_count_" + agentName;
                Integer retryCount = errorContext.agenticScope().readState(retryKey, 0);
                if (retryCount < 2) {
                    errorContext.agenticScope().writeState(retryKey, retryCount + 1);
                    LOG.warn("Error in agent '{}' (attempt {} of 2): {}. Retrying via LangChain4j errorHandler...",
                        agentName, retryCount + 1, exMsg);

                    @SuppressWarnings("unchecked")
                    Consumer<WorkflowProgressEvent> progress = errorContext.agenticScope().executionContextAs(Consumer.class);
                    if (progress != null) {
                        progress.accept(WorkflowProgressEvent.of(
                            "agent2_progress",
                            "Parallel Day Optimizer",
                            "Transient issue on day worker (" + agentName + "). Retrying (attempt " + (retryCount + 1) + ")..."
                        ));
                    }
                    return ErrorRecoveryResult.retry();
                }

                LOG.error("Agent '{}' exceeded max retries: {}", agentName, exMsg);
                return ErrorRecoveryResult.throwException();
            })
            .build();

        // Agent 2 Fallback: Monolithic Conference Schedule Builder Agent (equipped with tools)
        this.scheduleBuilderAgent = AgenticServices.agentBuilder(ScheduleBuilderAgent.class)
            .chatModel(chatModel)
            .outputKey("schedule")
            .tools(conferenceTools)
            .listener(agentObservabilityListener)
            .build();

        LOG.info("LangChain4j Parallel Mapper Agentic System initialized with virtual thread executor.");
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
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        if (progressConsumer != null) {
            scope.writeExecutionContext(Consumer.class, progressConsumer);
        }
        LangChain4jManaged.setCurrent(
            Map.of(AgenticScope.class, scope)
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

            // Step 2: Query candidate talks and partition into 5 conference days
            String query = (validation.sanitizedInterests() != null && !validation.sanitizedInterests().isBlank())
                ? validation.sanitizedInterests()
                : rawInput;

            LOG.info("Step 2 [Parallel Mapper]: Partitioning catalog into 5 days for query '{}'", query);
            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "indexing",
                    "Devoxx Catalog Indexer",
                    "Partitioning candidate sessions across all 5 conference days..."
                ));
            }

            String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday"};
            String[] dates = {"2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"};
            String[] labels = {
                "Monday, Oct 5 (Deep Dives & Labs)",
                "Tuesday, Oct 6 (Deep Dives & Labs)",
                "Wednesday, Oct 7 (Keynotes & Conference)",
                "Thursday, Oct 8 (Conference)",
                "Friday, Oct 9 (Conference - Half Day)"
            };

            List<DayPlanRequest> dayRequests = new ArrayList<>();
            List<ConferenceTalk> allCandidateTalks = new ArrayList<>();

            for (int i = 0; i < dayNames.length; i++) {
                String day = dayNames[i];
                List<ConferenceTalk> dayMatches = new ArrayList<>(conferenceService.searchTalks(query, day, 15));
                if (dayMatches.size() < 6) {
                    for (ConferenceTalk top : conferenceService.getTalksByDay(day)) {
                        if (dayMatches.stream().noneMatch(t -> t.id() == top.id())) {
                            dayMatches.add(top);
                        }
                        if (dayMatches.size() >= 12) break;
                    }
                }
                allCandidateTalks.addAll(dayMatches);
                String dayPrompt = conferenceService.formatTalksForPrompt(dayMatches);
                dayRequests.add(new DayPlanRequest(day, dates[i], labels[i], query, dayPrompt));
            }

            if (progressConsumer != null) {
                progressConsumer.accept(WorkflowProgressEvent.of(
                    "agent2_start",
                    "Parallel Day Optimizers",
                    "Dispatching 5 parallel Gemini workers to synthesize Mon–Fri concurrently..."
                ));
            }

            long a2StartTime = System.currentTimeMillis();
            ScheduleResponse finalResponse = null;

            try {
                LOG.info("Invoking ParallelScheduleBuilderWorkflow across 5 virtual threads");
                List<DaySchedule> rawDays = parallelScheduleWorkflow.scheduleDays(dayRequests);
                if (rawDays != null && !rawDays.isEmpty() &&
                    rawDays.stream().anyMatch(d -> d != null && d.talks() != null && !d.talks().isEmpty())) {
                    List<DaySchedule> enrichedDays = enrichDaysWithAbstracts(rawDays);
                    finalResponse = new ScheduleResponse(
                        true,
                        null,
                        "Devoxx Belgium 2026: " + query,
                        "AI-curated conflict-free 5-day conference agenda tailored to your focus on " + query + ".",
                        enrichedDays
                    );
                    LOG.info("Parallel mapper completed successfully with {} days", enrichedDays.size());
                }
            } catch (Exception e) {
                LOG.error("ParallelScheduleBuilderWorkflow failed, trying fallback", e);
            }

            // Fallback to monolithic ScheduleBuilderAgent if parallel mapper produced empty/failed response
            if (finalResponse == null) {
                try {
                    LOG.warn("Falling back to monolithic ScheduleBuilderAgent");
                    String candidatePrompt = conferenceService.formatTalksForPrompt(allCandidateTalks.stream().distinct().toList());
                    ScheduleResponse response = scheduleBuilderAgent.buildSchedule(query, candidatePrompt);
                    if (response != null && response.days() != null && !response.days().isEmpty() &&
                        response.days().stream().anyMatch(d -> d != null && d.talks() != null && !d.talks().isEmpty())) {
                        List<DaySchedule> enrichedDays = enrichDaysWithAbstracts(response.days());
                        finalResponse = new ScheduleResponse(
                            true,
                            null,
                            response.theme() != null ? response.theme() : query,
                            response.overview(),
                            enrichedDays
                        );
                    }
                } catch (Exception e) {
                    LOG.error("Error executing ScheduleBuilderAgent fallback", e);
                }
            }

            if (finalResponse == null) {
                finalResponse = buildFallbackSchedule(query, allCandidateTalks);
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
            LangChain4jManaged.removeCurrent();
        }
    }

    private List<DaySchedule> enrichDaysWithAbstracts(List<DaySchedule> days) {
        if (days == null) return List.of();
        List<DaySchedule> enrichedDays = new ArrayList<>();
        for (DaySchedule day : days) {
            List<ScheduledTalk> enrichedTalks = new ArrayList<>();
            if (day.talks() != null) {
                for (ScheduledTalk talk : day.talks()) {
                    String realAbstract = conferenceService.getTalkById(talk.talkId())
                        .map(ConferenceTalk::talkAbstract)
                        .filter(s -> !s.isBlank())
                        .orElseGet(() -> {
                            if (talk.title() == null || talk.title().isBlank()) return "";
                            return conferenceService.getAllTalks().stream()
                                .filter(t -> t.title() != null && t.title().equalsIgnoreCase(talk.title().trim()))
                                .map(ConferenceTalk::talkAbstract)
                                .findFirst()
                                .orElse(talk.talkAbstract() != null ? talk.talkAbstract() : "");
                        });

                    enrichedTalks.add(new ScheduledTalk(
                        talk.talkId(),
                        talk.day(),
                        talk.date(),
                        talk.startTime(),
                        talk.endTime(),
                        talk.room(),
                        talk.title(),
                        talk.speakers(),
                        talk.track(),
                        talk.sessionType(),
                        talk.reason(),
                        realAbstract
                    ));
                }
            }
            enrichedDays.add(new DaySchedule(day.day(), day.date(), day.dayLabel(), enrichedTalks));
        }
        return enrichedDays;
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
        List<DaySchedule> days = new ArrayList<>();
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

            List<ScheduledTalk> scheduled = new ArrayList<>();
            for (ConferenceTalk t : dayTalks) {
                scheduled.add(new ScheduledTalk(
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
                    "Matches your interest in " + query,
                    t.talkAbstract()
                ));
            }
            days.add(new DaySchedule(day, dates[i], labels[i], scheduled));
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
