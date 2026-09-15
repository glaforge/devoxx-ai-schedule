# Devoxx Belgium 2026 AI Schedule Builder (`dvxaisched`)

A Micronaut application that leverages LangChain4j's AI Agentic system and Google's Gemini 3.8 Flash model (`gemini-3.8-flash` via `dev.langchain4j:langchain4j-google-genai`) to generate personalized conference schedules for Devoxx Belgium 2026.

## Architecture

### 1. Two-Agent Sequential Workflow
- **Agent 1 (`InterestValidatorAgent`)**:
  - Security and content guardrail.
  - Validates user input against prompt injection, instruction overrides (DAN, jailbreaks), insults, obscenities, or meaningless noise.
  - Generates structured `ValidationResult` (valid flag, rejection reason, sanitized interests).
  - Immediately short-circuits the pipeline if rejected, preventing unnecessary LLM calls downstream.
- **Agent 2 (`ScheduleBuilderAgent`)**:
  - Conference schedule curator equipped with `DevoxxConferenceTools`.
  - Analyzes the 201 official Devoxx Belgium 2026 talks across all 5 conference days (Monday Oct 5 to Friday Oct 9, 2026).
  - Returns a strongly typed `ScheduleResponse` Java record detailing the days, time slots, rooms, speakers, tracks, and personalized curation rationale.

### 2. Tech Stack & Dependencies
- **Framework**: Micronaut 5.0 (Java 25)
- **Build Tool**: Gradle (Groovy DSL syntax in [`build.gradle`](file:///Users/glaforge/Projects/dvxaisched/build.gradle) and [`settings.gradle`](file:///Users/glaforge/Projects/dvxaisched/settings.gradle))
- **LLM Integration**: `dev.langchain4j:langchain4j-google-genai:1.20.0-beta30` with `gemini-3.8-flash`
- **Agentic Engine**: `dev.langchain4j:langchain4j-agentic:1.20.0-beta30`
- **Data Source**: Embedded official Devoxx Belgium 2026 schedule dataset (201 talks)

## Prerequisites
- JDK 25 (e.g. installed via SDKman)
- `GEMINI_API_KEY` environment variable set with a Google AI Studio API key

## Running the Application

```bash
cd /Users/glaforge/Projects/dvxaisched
export GEMINI_API_KEY="your-gemini-api-key"
./gradlew run
```

The application starts on `http://localhost:8080`.

Open your browser to:
- **Web UI**: `http://localhost:8080/`
- **Health Check**: `http://localhost:8080/api/health`
- **Conference Tracks**: `http://localhost:8080/api/tracks`

## Running Tests

```bash
./gradlew test
```
