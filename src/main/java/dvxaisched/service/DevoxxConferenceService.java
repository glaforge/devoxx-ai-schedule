package dvxaisched.service;

import dvxaisched.model.ConferenceTalk;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class DevoxxConferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(DevoxxConferenceService.class);

    private final ObjectMapper objectMapper;
    private final ResourceResolver resourceResolver;
    private final List<ConferenceTalk> talks = new ArrayList<>();

    public DevoxxConferenceService(ObjectMapper objectMapper, ResourceResolver resourceResolver) {
        this.objectMapper = objectMapper;
        this.resourceResolver = resourceResolver;
    }

    @PostConstruct
    public void init() {
        loadEmbeddedSchedule();
    }

    private synchronized void loadEmbeddedSchedule() {
        try (InputStream is = getClass().getResourceAsStream("/devoxx-be-2026.json")) {
            if (is != null) {
                ConferenceTalk[] loaded = objectMapper.readValue(is, ConferenceTalk[].class);
                talks.clear();
                talks.addAll(Arrays.asList(loaded));
                LOG.info("Loaded {} scheduled talks for Devoxx Belgium 2026 from embedded dataset", talks.size());
            } else {
                LOG.warn("devoxx-be-2026.json resource not found");
            }
        } catch (Exception e) {
            LOG.error("Failed to load devoxx-be-2026.json", e);
        }
    }

    public List<ConferenceTalk> getAllTalks() {
        return Collections.unmodifiableList(talks);
    }

    public Optional<ConferenceTalk> getTalkById(long id) {
        return talks.stream().filter(t -> t.id() == id).findFirst();
    }

    public List<String> getAllTracks() {
        return talks.stream()
            .map(ConferenceTalk::track)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    }

    public List<ConferenceTalk> getTalksByDay(String day) {
        if (day == null || day.isBlank()) return getAllTalks();
        String normalizedDay = day.trim().toLowerCase();
        return talks.stream()
            .filter(t -> t.day() != null && t.day().equalsIgnoreCase(normalizedDay))
            .sorted(Comparator.comparing(ConferenceTalk::startTime))
            .toList();
    }

    public List<ConferenceTalk> getTalksByTrack(String track, String day) {
        return talks.stream()
            .filter(t -> track == null || track.isBlank() || (t.track() != null && t.track().toLowerCase().contains(track.toLowerCase())))
            .filter(t -> day == null || day.isBlank() || (t.day() != null && t.day().equalsIgnoreCase(day)))
            .sorted(Comparator.comparing(ConferenceTalk::startTime))
            .toList();
    }

    public List<ConferenceTalk> getTopTalks(int limit) {
        return talks.stream()
            .sorted(Comparator.comparingInt(ConferenceTalk::totalFavourites).reversed())
            .limit(limit > 0 ? limit : 20)
            .toList();
    }

    public List<ConferenceTalk> searchTalks(String query, String day, int limit) {
        if (query == null || query.isBlank()) {
            return getTalksByDay(day).stream().limit(limit > 0 ? limit : 20).toList();
        }

        String[] keywords = query.toLowerCase().split("\\s+");
        return talks.stream()
            .filter(t -> day == null || day.isBlank() || (t.day() != null && t.day().equalsIgnoreCase(day)))
            .map(t -> {
                int score = scoreTalk(t, keywords);
                return new SimpleEntry<>(t, score);
            })
            .filter(entry -> entry.getValue() > 0)
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .map(Entry::getKey)
            .limit(limit > 0 ? limit : 20)
            .toList();
    }

    private int scoreTalk(ConferenceTalk talk, String[] keywords) {
        int score = 0;
        String title = talk.title() != null ? talk.title().toLowerCase() : "";
        String summary = talk.summary() != null ? talk.summary().toLowerCase() : "";
        String track = talk.track() != null ? talk.track().toLowerCase() : "";
        String speakers = talk.speakersSummary().toLowerCase();

        for (String kw : keywords) {
            if (kw.length() < 2) continue;
            if (title.contains(kw)) score += 10;
            if (track.contains(kw)) score += 8;
            if (speakers.contains(kw)) score += 6;
            if (summary.contains(kw)) score += 3;
        }

        // Add popularity weight
        score += Math.min(talk.totalFavourites(), 10);
        return score;
    }

    public String formatTalksForPrompt(List<ConferenceTalk> selectedTalks) {
        StringBuilder sb = new StringBuilder();
        for (ConferenceTalk t : selectedTalks) {
            sb.append(String.format("- [ID: %d] [%s %s-%s | Room: %s | Track: %s | Type: %s | %d favs]\n",
                t.id(), t.day().toUpperCase(), t.startTime(), t.endTime(), t.room(), t.track(), t.sessionType(), t.totalFavourites()));
            sb.append(String.format("  Title: %s\n", t.title()));
            sb.append(String.format("  Speakers: %s\n", t.speakersSummary()));
            if (t.summary() != null && !t.summary().isBlank()) {
                String cleanSummary = t.summary().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                if (cleanSummary.length() > 200) {
                    cleanSummary = cleanSummary.substring(0, 197) + "...";
                }
                sb.append(String.format("  Summary: %s\n", cleanSummary));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
