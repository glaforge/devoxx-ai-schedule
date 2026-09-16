# ==============================================================================
# Devoxx Belgium 2026 AI Schedule Builder — Project Workflow Automation
# ==============================================================================
# Variables can be overridden via environment variables or command-line arguments:
#   just project=my-other-project region=us-central1 deploy
# ==============================================================================

# Google Cloud Platform configuration
project     := env_var_or_default("GOOGLE_CLOUD_PROJECT", "genai-java-demos")
region      := env_var_or_default("CLOUD_RUN_REGION", "europe-west1")
service     := env_var_or_default("CLOUD_RUN_SERVICE", "dvxaisched")
base_image  := env_var_or_default("CLOUD_RUN_BASE_IMAGE", "google-24/java25")
memory      := env_var_or_default("CLOUD_RUN_MEMORY", "2Gi")
cpu         := env_var_or_default("CLOUD_RUN_CPU", "2")
secret      := env_var_or_default("CLOUD_RUN_SECRET", "GEMINI_API_KEY=GEMINI_API_KEY:latest")

# Conference data configuration
event_slug  := env_var_or_default("DEVOXX_EVENT_SLUG", "dvbe26")

# Default: List all available recipes
default:
    @just --list

# Run all unit and integration test suites
test:
    ./gradlew test

# Run the Micronaut application locally
run:
    ./gradlew run

# Fetch the latest official schedule from the CFP API and update resources
fetch-schedule slug=event_slug:
    ./gradlew fetchSchedule -PeventSlug={{slug}}

alias fetch := fetch-schedule

# Package the shadow fat JAR and stage it for Cloud Run build-less deployment
build:
    ./gradlew shadowJar
    mkdir -p build/run
    cp build/libs/dvxaisched-0.1-all.jar build/run/application.jar

# Clean build artifacts and staging directories
clean:
    ./gradlew clean
    rm -rf build/run

# Build and deploy the service to Google Cloud Run (build-less Java 25)
deploy: build
    gcloud beta run deploy {{service}} \
        --source=build/run \
        --base-image={{base_image}} \
        --region={{region}} \
        --project={{project}} \
        --no-build \
        --set-secrets={{secret}} \
        --set-env-vars=MICRONAUT_SERVER_PORT=8080 \
        --memory={{memory}} \
        --cpu={{cpu}} \
        --allow-unauthenticated \
        --quiet

# Show Cloud Run service status, URL, and revision
status:
    gcloud run services describe {{service}} \
        --project={{project}} \
        --region={{region}} \
        --format="table(status.url:label=URL,status.latestReadyRevisionName:label=REVISION,status.conditions[0].status:label=READY)"

# Tail live application logs from Cloud Run
logs:
    gcloud beta run services logs tail {{service}} \
        --project={{project}} \
        --region={{region}}
