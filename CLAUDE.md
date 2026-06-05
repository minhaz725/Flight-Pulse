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

- [x] **F1 — Project bootstrap**
  Spring Boot 4.x + Java 25 setup, package structure, Docker Compose with Postgres + Kafka + Redis, Flyway baseline, Actuator, Springdoc, GitHub Actions CI skeleton. Branch: `feature/project-bootstrap`.

- [x] **F2 — Domain model and persistence**
  `Deal` entity with status enum (INGESTED, SCORED, PUBLISHED, EXPIRED). `UserSubscription` entity with:
  - `preferredChannel` — enum LOG, TELEGRAM, EMAIL, SMS; default LOG (F9 routing)
  - `travelDateFrom` / `travelDateTo` — inclusive travel window; a fixed date is just from == to
  - `alertType` — enum THRESHOLD, NEW_LOW, SCORE (drives matching logic in F8)
  - `status` — enum ACTIVE, EXPIRED (replaces the simple `active` boolean; housekeeping transitions this)
  - `bestPriceSeen` — nullable; updated by the matcher each time a NEW_LOW fires
  - `destination` and `maxPrice` are optional (route-only and score-only subscriptions are valid)
  `PriceHistory` entity: records observed prices per route (origin, destination, price, currency, observedAt) for the scorer's rolling-median baseline. Repositories, Flyway migrations (V2 creates deal + user_subscription; V3 adds price_history and the subscription additions). Branch: `feature/domain-model`.

- [ ] **F3 — Ingestion layer**
  Source adapter interface with multiple implementations. Primary adapters are mocked feeds emitting realistic deals in deliberately different formats (one XML, one JSON, different field names) so the normalisation layer has real work. One optional real adapter (Aviationstack free tier or Travelpayouts) behind a config flag, demonstrating live integration with auth, rate limits, and error handling. The project must run fully on mocks alone if the real adapter is disabled or the API is unavailable. No Amadeus self-service; no Google Flights API or scraper. Branch: `feature/ingestion-layer`.

- [ ] **F4 — Validation**
  Validate deals: future travel date, valid airport codes, sane price and currency. Reject or flag invalid deals. Branch: `feature/deal-validation`.

- [ ] **F5 — Scoring service**
  Score each deal 0–100 from three signals, then move status INGESTED → SCORED.

  **Signals and blend (all weights configurable in `application.yml`):**
  - `priceVsHistory` (default weight 0.60): how far below the route's rolling median the deal price is, normalised to 0–100. Computed from the PriceHistory table (F2).
  - `statedDiscount` (default weight 0.25): the deal's own `discountPercentage` field when the source provides it, normalised to 0–100. Zero if absent.
  - `routePopularity` (default weight 0.15): number of ACTIVE subscriptions watching that route, normalised against a configurable ceiling (e.g. 50 subs = 100 points).

  `score = weight_pvh * priceVsHistory + weight_sd * statedDiscount + weight_rp * routePopularity`, clamped to [0, 100].

  **Cold-start rule**: if a route has fewer than a configurable minimum number of historical observations (e.g. 5), `priceVsHistory` is treated as 0 and the deal is flagged `lowConfidence = true`. Do not fabricate a baseline. Log a WARN when scoring in cold-start mode.

  After scoring, append the observed price to PriceHistory so future deals on the same route have more data. Branch: `feature/scoring-service`.

- [ ] **F6 — Kafka publishing**
  Publish `deal.published` events when a deal reaches PUBLISHED. Producer config, event payloads. Branch: `feature/kafka-publishing`.

- [ ] **F7 — Subscription API**
  REST endpoints to create, list, and delete user subscriptions. DTOs, validation, Swagger, Postman entries.

  **Request fields (POST /api/subscriptions):**
  - `userId` (required)
  - `origin` (required; 3-letter IATA code)
  - `destination` (optional; omit for "any destination from origin")
  - `travelDateFrom` / `travelDateTo` (both required; ISO date; from == to for a fixed date)
  - `alertType` (required; THRESHOLD | NEW_LOW | SCORE)
  - `maxPrice` (required when alertType is THRESHOLD; optional otherwise)
  - `minScore` (required when alertType is SCORE; optional otherwise)
  - `preferredChannel` (optional; default LOG)

  **Lifecycle fields (managed by the system, not user-settable):**
  `status` starts ACTIVE. `bestPriceSeen` is null until the first match.

  Validate that `travelDateFrom` is not in the past and `travelDateTo >= travelDateFrom`. Return 400 with a clear message on constraint violations. Branch: `feature/subscription-api`.

- [ ] **F8 — Subscription matching**
  Consume `deal.published`, match against ACTIVE subscriptions, fire `alert.triggered` events. Dead-letter handling for failures.

  **Match criteria (all must hold):**
  1. Subscription origin == deal origin.
  2. Subscription destination matches deal destination (or subscription destination is null = any).
  3. Deal `departureDate` falls within subscription `travelDateFrom`..`travelDateTo` (inclusive).

  **Alert type evaluation (after route/window match):**
  - `THRESHOLD`: fire if deal price <= subscription maxPrice.
  - `NEW_LOW`: fire if deal price < subscription bestPriceSeen (or bestPriceSeen is null — the first match always fires). After firing, update bestPriceSeen to the deal price.
  - `SCORE`: fire if deal score >= subscription minScore.

  **Framing**: the system promises "best seen so far on this watch", never "lowest possible". Comments and README must reflect this.

  **Acceptance examples (verified in tests):**
  - THRESHOLD subscription maxPrice=900, deal price=850, date in window → alert fires.
  - NEW_LOW subscription: deal at 1100 fires (first), deal at 1040 fires (new low), deal at 930 fires (new low), deal at 960 does NOT fire.
  - Deal outside the travel window → no alert regardless of price or score.
  - Deal inside window but price above maxPrice on THRESHOLD subscription → no alert.

  Branch: `feature/subscription-matching`.

- [ ] **F9 — Notification stub**
  `NotificationChannel` interface in the `messaging` package. Implementations: `LogChannel` (default, always available, logs the intended message), `TelegramChannel` (bot token), `EmailChannel` (SMTP/transactional email), `SmsChannel` (Twilio, optional, behind a config flag, disabled by default). A user subscription specifies its preferred channel. The notification stub consumes `alert.triggered` events and routes to the chosen channel, falling back to `LogChannel` if that channel is unavailable. Every channel must degrade gracefully so the project runs end to end with no external credentials. Retry logic + dead-letter queue. Branch: `feature/notification-stub`.

- [ ] **F10 — Deals query API**
  `GET /api/deals` with filtering by origin, destination, price range, score. Swagger, Postman entries. Branch: `feature/deals-query-api`.

- [ ] **F11 — Scheduled polling job**
  `@Scheduled` job that triggers ingestion every X minutes. Configurable interval. Branch: `feature/polling-job`.

- [ ] **F12 — Housekeeping job**
  Daily scheduled job (configurable interval) that handles expiry and cleanup.

  **Deal expiry**: move deals with `departureDate` in the past to status EXPIRED.

  **Subscription expiry**: transition ACTIVE subscriptions to EXPIRED once their `travelDateFrom` has passed. On expiry, fire one final `alert.triggered` event carrying:
  - `bestPriceSeen` — the lowest price seen during the watch lifetime (if any match fired).
  - or a "no match" summary if `bestPriceSeen` is still null (the watch ended with no qualifying deal found).

  **Purge**: delete EXPIRED deals older than a configurable retention window (e.g. 30 days). Delete EXPIRED subscriptions older than a configurable retention window.

  **Daily summary log**: emit a single INFO log line summarising how many deals were expired, how many subscriptions were expired, how many records were purged.

  All retention windows and intervals configurable via `application.yml`. Branch: `feature/housekeeping-job`.

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

**STATUS: IN PROGRESS — F2 (domain model and persistence) complete**
