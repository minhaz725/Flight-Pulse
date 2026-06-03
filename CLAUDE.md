# CLAUDE.md — FlightPulse

This file guides Claude Code while building the FlightPulse project. Read it fully before writing any code. Follow every rule here unless I explicitly override it in a prompt.

---

## 1. What We Are Building

FlightPulse is a backend service that continuously watches multiple flight deal sources, normalises them into one schema, scores how good each deal is via a scoring service, persists them with a status lifecycle, publishes them over Kafka, and matches them against user subscriptions to fire alert events.

This is NOT a flight search tool. Users do not query for flights on demand. Users create subscriptions (saved watches). The system runs in the background — polling, processing, matching — and notifies users when a matching deal appears.

There is no frontend. This is a pure backend portfolio piece. All user interaction is via REST API.

---

## 2. Core Interaction Model

- Users create subscriptions via REST: `POST /api/subscriptions`
- Users browse current deals via REST: `GET /api/deals`
- Ingestion is internal and automatic via scheduled jobs — no user involved
- Matching is triggered when a deal reaches PUBLISHED status
- Alerts are fired as Kafka events to a notification stub
- App configuration (API keys, poll intervals, Kafka broker, DB) comes from `application.yml` and environment variables. Command line args are ONLY for startup flags such as `--spring.profiles.active=local`. Never use command line args for user input like origin, destination, or traveller counts.

---

## 3. Pipeline Flow

```
Sources (APIs / mocked feeds)
        |
  Ingestion Layer        - normalises different formats into one Deal schema
        |
  Validation             - future date? valid airports? sane price?
        |
  Scoring Service        - how good is this deal? discount %, route popularity
        |
  PostgreSQL             - status: INGESTED -> SCORED -> PUBLISHED -> EXPIRED
        |
  Kafka Topic            - deal.published event fired
        |
  Subscription Matcher   - does this deal match any user subscription?
        |
  Notification Events    - alert.triggered event (stub email/push)
```

Parallel background jobs:
- Polling job: pulls deal sources every X minutes
- Housekeeping job: expires old deals, purges stale data

---

## 4. Tech Stack

| Concern | Tool |
|---|---|
| Core framework | Spring Boot 4.x, Java 25 |
| Messaging | Apache Kafka (via Docker) |
| Database | PostgreSQL + Flyway migrations |
| ORM | Spring Data JPA |
| Batch processing | Spring Batch (bulk deal ingestion runs) |
| Scheduling | Spring @Scheduled (polling + housekeeping) |
| Caching | Redis (cache latest deals, avoid hammering source APIs) |
| Testing | JUnit 5 + Testcontainers (Postgres + Kafka) |
| API docs | Springdoc OpenAPI / Swagger UI |
| Observability | Micrometer + Spring Boot Actuator |
| CI | GitHub Actions |
| Local infra | Docker Compose (one command runs everything) |
| Data sources | Mock-first: multiple mocked feed adapters in differing formats (XML + JSON, divergent field names) + one optional real adapter (Aviationstack free tier or Travelpayouts) behind a config flag, disabled by default. No Amadeus, no Google Flights. |
| Notifications | NotificationChannel interface — Log (default), Telegram (bot), Email (SMTP), SMS (Twilio, optional). Graceful degradation, runs with no creds. |

---

## 5. Package Structure

Base package: `com.minhaz.flightpulse`

Separate concerns into clear packages. Do not mix layers.

```
com.minhaz.flightpulse
├── api            controllers, request/response DTOs
├── service        business logic, orchestration
├── model          domain entities, enums, status types
├── repository     Spring Data JPA repositories
├── ingestion      source adapters, normalisation logic
├── scoring        deal scoring service
├── matching       subscription matching logic
├── messaging      Kafka producers, consumers, event payloads, notification channels (NotificationChannel + implementations)
├── batch          Spring Batch jobs and configs
├── scheduling     scheduled polling and housekeeping jobs
├── config         Spring config, beans, properties
└── common         shared utilities, exceptions, constants
```

Keep controllers thin. Business logic lives in services. Never put logic in controllers or repositories.

---

## 6. Code Style Rules

These rules are strict. Follow them everywhere.

- Use few comments. Only comment where intent is not obvious from the code.
- Comments start with a lowercase letter.
- Use single line comments only. Never use multi line comments.
- Comment format example: `// this method will be used for polling`
- No Javadoc blocks unless I explicitly ask for them.
- Prefer clear method and variable names over comments.
- Use records for DTOs and immutable data where it fits.
- No God classes. No magic numbers — extract them to named constants.
- Constructor injection only. No field injection.

---

## 7. Git Workflow

- Every feature is built on its own branch. Never commit features directly to main.
- Branch naming: `feature/<short-name>` for example `feature/ingestion-layer`.
- Use small, focused commits with proper messages.
- Commit message format: imperative mood, concise subject, optional body.
  - Good: `add deal normalisation for mock xml source`
  - Good: `wire kafka producer for deal.published event`
  - Bad: `update`, `fix stuff`, `wip`
- When a feature is complete, merge its branch into main (or open a PR if I ask for PRs), then move to the next feature.
- Do not squash everything into one commit. The history should tell the story of how the project was built.

---

## 8. Deliverables Required Alongside Code

These are not optional. Build them as you go, not at the end.

- Swagger / OpenAPI docs via Springdoc, available at runtime.
- Postman collection (a JSON file in the repo, for example `/postman/flightpulse.postman_collection.json`) covering every endpoint with example requests.
- README with: what the project is, architecture diagram (ASCII is fine), how to run it via Docker Compose, and key design decisions.
- `docker-compose.yml` that brings up the app, Postgres, Kafka, and Redis with one command.
- GitHub Actions CI workflow that builds and runs tests.

---

## 9. Feature List and Scope

Build features in this order. Each feature gets its own branch. Mark a feature done only when its code, tests, Swagger entries, and Postman entries are all complete.

- [ ] **F1 — Project bootstrap**
  Spring Boot 4.x + Java 25 setup, package structure, Docker Compose with Postgres + Kafka + Redis, Flyway baseline, Actuator, Springdoc, GitHub Actions CI skeleton. Branch: `feature/project-bootstrap`.

- [ ] **F2 — Domain model and persistence**
  `Deal` entity with status enum (INGESTED, SCORED, PUBLISHED, EXPIRED), `UserSubscription` entity (including a `preferredChannel` field — enum LOG, TELEGRAM, EMAIL, SMS; default LOG — used by F9 routing), repositories, Flyway migrations. Branch: `feature/domain-model`.

- [ ] **F3 — Ingestion layer**
  Source adapter interface with multiple implementations. Primary adapters are mocked feeds emitting realistic deals in deliberately different formats (one XML, one JSON, different field names) so the normalisation layer has real work. One optional real adapter (Aviationstack free tier or Travelpayouts) behind a config flag, demonstrating live integration with auth, rate limits, and error handling. The project must run fully on mocks alone if the real adapter is disabled or the API is unavailable. No Amadeus self-service; no Google Flights API or scraper. Branch: `feature/ingestion-layer`.

- [ ] **F4 — Validation**
  Validate deals: future travel date, valid airport codes, sane price and currency. Reject or flag invalid deals. Branch: `feature/deal-validation`.

- [ ] **F5 — Scoring service**
  Score deal quality based on discount percentage and route popularity. Move status INGESTED -> SCORED. Branch: `feature/scoring-service`.

- [ ] **F6 — Kafka publishing**
  Publish `deal.published` events when a deal reaches PUBLISHED. Producer config, event payloads. Branch: `feature/kafka-publishing`.

- [ ] **F7 — Subscription API**
  REST endpoints to create, list, and delete user subscriptions. DTOs, validation, Swagger, Postman entries. Branch: `feature/subscription-api`.

- [ ] **F8 — Subscription matching**
  Consume `deal.published`, match against subscriptions, fire `alert.triggered` events. Dead-letter handling for failures. Branch: `feature/subscription-matching`.

- [ ] **F9 — Notification stub**
  `NotificationChannel` interface in the `messaging` package. Implementations: `LogChannel` (default, always available, logs the intended message), `TelegramChannel` (bot token), `EmailChannel` (SMTP/transactional email), `SmsChannel` (Twilio, optional, behind a config flag, disabled by default). A user subscription specifies its preferred channel. The notification stub consumes `alert.triggered` events and routes to the chosen channel, falling back to `LogChannel` if that channel is unavailable. Every channel must degrade gracefully so the project runs end to end with no external credentials. Retry logic + dead-letter queue. Branch: `feature/notification-stub`.

- [ ] **F10 — Deals query API**
  `GET /api/deals` with filtering by origin, destination, price range, score. Swagger, Postman entries. Branch: `feature/deals-query-api`.

- [ ] **F11 — Scheduled polling job**
  `@Scheduled` job that triggers ingestion every X minutes. Configurable interval. Branch: `feature/polling-job`.

- [ ] **F12 — Housekeeping job**
  Expire old deals (status -> EXPIRED), purge stale data, daily summary log. Branch: `feature/housekeeping-job`.

- [ ] **F13 — Spring Batch bulk ingestion**
  Batch job for bulk deal ingestion runs with chunked processing. Branch: `feature/batch-ingestion`.

- [ ] **F14 — Caching**
  Redis caching for latest deals to avoid hammering source APIs. Branch: `feature/redis-caching`.

- [ ] **F15 — Observability**
  Micrometer metrics, Actuator endpoints, basic tracing setup. Branch: `feature/observability`.

- [ ] **F16 — Testing pass**
  Ensure unit tests across services and at least one Testcontainers integration test covering Postgres + Kafka end to end. Branch: `feature/testing-pass`.

- [ ] **F17 — Docs finalisation**
  Complete README, finalise Postman collection, verify Swagger covers all endpoints, polish git history. Branch: `feature/docs-finalisation`.

---

## 10. Completion Definition

The project is COMPLETE only when every feature F1 through F17 is checked off, all tests pass in CI, Docker Compose brings the whole stack up with one command, Swagger documents every endpoint, and the Postman collection covers every endpoint. When all features are done, update the top of this file to state: **STATUS: COMPLETED**.

Until then, keep this status line current:

**STATUS: NOT STARTED**
