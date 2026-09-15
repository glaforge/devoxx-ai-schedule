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

import java.util.ArrayList;
import java.util.List;

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

        // Agent 1: Interest Validation & Guardrail Agent
        this.validatorAgent = AgenticServices.agentBuilder(InterestValidatorAgent.class)
            .chatModel(chatModel)
            .outputKey("validationResult")
            .build();

        // Agent 2: Conference Schedule Builder Agent (equipped with conference tools)
        this.scheduleBuilderAgent = AgenticServices.agentBuilder(ScheduleBuilderAgent.class)
            .chatModel(chatModel)
            .outputKey("schedule")
            .tools(conferenceTools)
            .build();

        LOG.info("LangChain4j 2-agent system initialized successfully.");
    }

    /**
     * Executes the 2-agent sequence:
     * 1. Agent 1 checks user interests (guardrail: no prompt injection, offensive text, or off-topic spam).
     * 2. If valid, Agent 2 builds a personalized Devoxx Belgium 2026 conference schedule using real talks.
     */
    public ScheduleResponse processScheduleRequest(String userInterests) {
        if (userInterests == null || userInterests.trim().isBlank()) {
            return ScheduleResponse.rejected("Please enter one or more topics, technologies, or themes you are interested in.");
        }

        String rawInput = userInterests.trim();

        // Establish an AgenticScope across the 2-agent sequence
        dev.langchain4j.agentic.scope.DefaultAgenticScope scope =
            dev.langchain4j.agentic.scope.DefaultAgenticScope.ephemeralAgenticScope();
        dev.langchain4j.invocation.LangChain4jManaged.setCurrent(
            java.util.Map.of(dev.langchain4j.agentic.scope.AgenticScope.class, scope)
        );

        try {
            LOG.info("Step 1 [Agent 1 - Validator]: Validating input '{}'", rawInput);

            ValidationResult validation;
            try {
                validation = validatorAgent.validate(rawInput);
            } catch (Exception e) {
                LOG.error("Error executing InterestValidatorAgent", e);
                validation = performFallbackValidation(rawInput);
            }

            LOG.info("Agent 1 Result: valid={}, reason='{}', sanitized='{}'",
                validation.valid(), validation.reason(), validation.sanitizedInterests());

            // Short-circuit if validation fails
            if (!validation.valid()) {
                String rejectMsg = (validation.reason() != null && !validation.reason().isBlank())
                    ? validation.reason()
                    : "The request could not be accepted. Please enter topics related to software engineering or technology.";
                return new ScheduleResponse(false, rejectMsg, rawInput, null, List.of());
            }

            // Step 2: Query candidate talks for the validated interests
            String query = (validation.sanitizedInterests() != null && !validation.sanitizedInterests().isBlank())
                ? validation.sanitizedInterests()
                : rawInput;

            LOG.info("Step 2 [Agent 2 - Schedule Builder]: Building schedule for query '{}'", query);

            List<ConferenceTalk> matched = new ArrayList<>(conferenceService.searchTalks(query, null, 35));
            if (matched.size() < 15) {
                for (ConferenceTalk top : conferenceService.getTopTalks(20)) {
                    if (matched.stream().noneMatch(t -> t.id() == top.id())) {
                        matched.add(top);
                    }
                }
            }

            String candidatePrompt = conferenceService.formatTalksForPrompt(matched);

            try {
                ScheduleResponse response = scheduleBuilderAgent.buildSchedule(query, candidatePrompt);
                if (response != null) {
                    return new ScheduleResponse(
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

            // Fallback programmatic schedule generation if agentic call experienced issues
            return buildFallbackSchedule(query, matched);
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
