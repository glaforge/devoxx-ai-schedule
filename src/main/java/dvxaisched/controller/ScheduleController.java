package dvxaisched.controller;

import dvxaisched.model.ConferenceTalk;
import dvxaisched.model.ScheduleRequest;
import dvxaisched.model.ScheduleResponse;
import dvxaisched.service.DevoxxAgentWorkflowService;
import dvxaisched.service.DevoxxConferenceService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Controller("/api")
public class ScheduleController {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleController.class);

    private final DevoxxAgentWorkflowService workflowService;
    private final DevoxxConferenceService conferenceService;

    public ScheduleController(
        DevoxxAgentWorkflowService workflowService,
        DevoxxConferenceService conferenceService
    ) {
        this.workflowService = workflowService;
        this.conferenceService = conferenceService;
    }

    @Post(uri = "/schedule", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<ScheduleResponse> generateSchedule(@Body ScheduleRequest request) {
        if (request == null || request.interests() == null || request.interests().isBlank()) {
            return HttpResponse.badRequest(ScheduleResponse.rejected("Interests cannot be empty."));
        }

        LOG.info("Received schedule generation request: '{}'", request.interests());
        ScheduleResponse response = workflowService.processScheduleRequest(request.interests());
        return HttpResponse.ok(response);
    }

    @Get(uri = "/schedule/stream", produces = MediaType.TEXT_EVENT_STREAM)
    public reactor.core.publisher.Flux<io.micronaut.http.sse.Event<dvxaisched.model.WorkflowProgressEvent>> streamSchedule(
        @QueryValue(value = "interests", defaultValue = "") String interests
    ) {
        if (interests == null || interests.isBlank()) {
            return reactor.core.publisher.Flux.just(
                io.micronaut.http.sse.Event.of(dvxaisched.model.WorkflowProgressEvent.rejected("Interests cannot be empty.", 0L))
            );
        }

        LOG.info("Received streaming schedule request: '{}'", interests);
        return reactor.core.publisher.Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    workflowService.processScheduleRequestWithProgress(interests, event -> {
                        sink.next(io.micronaut.http.sse.Event.of(event));
                    });
                    sink.complete();
                } catch (Exception e) {
                    LOG.error("Error streaming schedule for interests: {}", interests, e);
                    sink.next(io.micronaut.http.sse.Event.of(
                        dvxaisched.model.WorkflowProgressEvent.rejected("Error processing request: " + e.getMessage(), 0L)
                    ));
                    sink.complete();
                }
            });
        });
    }

    @Get(uri = "/tracks", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<List<String>> getTracks() {
        return HttpResponse.ok(conferenceService.getAllTracks());
    }

    @Get(uri = "/talks", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<List<ConferenceTalk>> getTalks(
        @QueryValue(value = "q", defaultValue = "") String query,
        @QueryValue(value = "day", defaultValue = "") String day,
        @QueryValue(value = "limit", defaultValue = "20") int limit
    ) {
        return HttpResponse.ok(conferenceService.searchTalks(query, day, limit));
    }

    @Get(uri = "/talks/{id}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<ConferenceTalk> getTalkById(@PathVariable long id) {
        return conferenceService.getTalkById(id)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    @Get(uri = "/sample-interests", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<List<Map<String, String>>> getSampleInterests() {
        return HttpResponse.ok(List.of(
            Map.of(
                "title", "AI Agents & GenAI",
                "badge", "Agentic",
                "prompt", "I'm interested in AI agents, Generative AI, LLM evaluation, LangChain4j, and loop engineering."
            ),
            Map.of(
                "title", "Java Language & JVM",
                "badge", "Java",
                "prompt", "A schedule focused on the Java language, latest features, Project Loom virtual threads, Valhalla, Amber, and best practices."
            ),
            Map.of(
                "title", "Mind the Geek & Quirky",
                "badge", "Geeky",
                "prompt", "A geeky agenda with fun, entertaining, surprising topics like robotics, Raspberry Pi, game engines, and space computing."
            ),
            Map.of(
                "title", "Architecture & Modernization",
                "badge", "Architecture",
                "prompt", "Modern enterprise architecture, modular monoliths, distributed systems, event-driven design, and guardrails."
            ),
            Map.of(
                "title", "Cloud Native & Kubernetes",
                "badge", "Cloud",
                "prompt", "Cloud-native Java, Kubernetes platforms, container hardening, vector databases, and GPU infrastructure."
            )
        ));
    }

    @Get(uri = "/health", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> health() {
        return HttpResponse.ok(Map.of(
            "status", "UP",
            "conference", "Devoxx Belgium 2026",
            "dates", "October 5-9, 2026",
            "venue", "Kinepolis, Antwerp",
            "totalTalksLoaded", conferenceService.getAllTalks().size(),
            "model", System.getProperty("gemini.model", "gemini-3.8-flash")
        ));
    }
}
