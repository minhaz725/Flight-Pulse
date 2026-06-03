# FlightPulse

FlightPulse is a backend service that continuously watches multiple flight-deal sources,
normalises them into one schema, scores how good each deal is, persists them through a status
lifecycle, publishes them over Kafka, and matches them against user subscriptions to fire
alert events.

This is **not** a flight-search tool. Users do not query flights on demand — they create
**subscriptions** (saved watches), and the system notifies them in the background when a
matching deal appears. There is no frontend; all interaction is via REST.

## Architecture

```
  Sources (mocked feeds + 1 optional real adapter)
          |
   Ingestion Layer        normalises differing formats (XML/JSON) into one Deal schema
          |
   Validation             future date? valid airports? sane price?
          |
   Scoring Service        how good is this deal? discount %, route popularity
          |
   PostgreSQL             status: INGESTED -> SCORED -> PUBLISHED -> EXPIRED
          |
   Kafka Topic            deal.published event fired
          |
   Subscription Matcher   does this deal match any user subscription?
          |
   Notification Channels  alert.triggered -> Log / Telegram / Email / SMS (with fallback)

  Parallel background jobs:
    - polling job: pulls deal sources every X minutes
    - housekeeping job: expires old deals, purges stale data
```

## Tech stack

Spring Boot 4.x · Java 25 · PostgreSQL + Flyway · Apache Kafka · Redis · Spring Batch ·
Springdoc OpenAPI · Micrometer + Actuator · Testcontainers · Docker Compose · GitHub Actions.

## Data sources

Mock-first. Multiple mocked feed adapters emit realistic deals in deliberately different
formats (one XML, one JSON, divergent field names) so the normalisation layer does real work.
One optional real adapter (Aviationstack free tier or Travelpayouts) sits behind a config flag
and is disabled by default — the project runs fully on mocks alone. No Amadeus, no Google Flights.

## Running it

Prerequisites: Docker + Docker Compose, and (for local builds) JDK 25.

```bash
# bring up the whole stack (app + postgres + kafka + redis)
docker compose up --build
```

Once up:

- API base: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Building locally (without Docker for the app)

The Maven wrapper is bundled, so no global Maven install is needed. Point `JAVA_HOME` at JDK 25:

```bash
export JAVA_HOME=/path/to/jdk-25
./mvnw -B verify          # runs tests (uses Testcontainers — Docker must be running)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # run against the compose services
```

## Key design decisions

- **Mock-first ingestion** behind a `SourceAdapter` interface, so the system is fully runnable
  with zero external credentials; a real adapter is opt-in via config.
- **Graceful-degradation notifications**: a `NotificationChannel` abstraction (Log default,
  plus Telegram / Email / SMS) that falls back to the always-available `LogChannel`.
- **Flyway owns the schema** (`spring.jpa.hibernate.ddl-auto: validate`).
- **Kafka in KRaft mode** (no Zookeeper) to keep the local stack lean.
- **Config via `application.yml` + env vars**; command-line args only for startup flags.

## Project status

Built feature-by-feature (F1–F17). See `CLAUDE.md` for the full feature list and current status.
