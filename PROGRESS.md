# FlightPulse — Build Progress

A running log of what's been built, key decisions, and notes for the next session.
Features and their scope live in `CLAUDE.md` (§9). This file is the cross-session handoff.

---

## F1 — Project bootstrap ✅ (merged to `main`)

Branch `feature/project-bootstrap`, merged via `--no-ff`. Spring Boot app boots, connects to
Postgres/Kafka/Redis, runs Flyway, exposes Actuator + Swagger.

### What's in place
- Spring Boot **4.0.6** / **Java 25**, Maven via bundled `./mvnw` (no global Maven).
- Base package `com.minhaz.flightpulse` with the full §5 package skeleton (`.gitkeep` placeholders in empty ones).
- `application.yml` (env-var driven defaults) + `application-local.yml` (localhost, chattier logging). No secrets committed.
- Flyway `V1__baseline.sql` — enables `pgcrypto`; real tables start in F2. `ddl-auto: validate` (Flyway owns schema).
- `docker-compose.yml`: app + Postgres 16 + Kafka 3.9 (KRaft) + Redis 7, all with healthchecks. Multi-stage `Dockerfile` (JDK 25 build → JRE 25 run).
- Springdoc OpenAPI + Actuator wired. CI workflow at `.github/workflows/ci.yml` (`./mvnw -B verify`, JDK 25) — runs once a remote is added.
- README (ASCII arch + run instructions) and `postman/flightpulse.postman_collection.json` skeleton.
- Testcontainers-backed context smoke test (`FlightPulseApplicationTests`).

### Verified
- `./mvnw -B verify` → green (context boots on a real Postgres, baseline migration applies).
- `docker compose up` → all four containers healthy; `/actuator/health` UP (db + redis), `/v3/api-docs` + Swagger UI 200.

### Key decisions / gotchas (Spring Boot 4 era)
- **SB4 restructured starters**: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-kafka`, modular `*-test` starters, `spring-boot-starter-flyway` + `flyway-database-postgresql`.
- **SB4 no longer manages Testcontainers versions** → `testcontainers-bom` **2.0.5** imported in `<dependencyManagement>`. In TC 2.x the modules are renamed (`testcontainers-postgresql`, `testcontainers-junit-jupiter`, `testcontainers-kafka`) and `PostgreSQLContainer` moved to `org.testcontainers.postgresql`.
- **Springdoc must be 3.0.3** (the Spring Framework 7 / Boot 4 line; the SB3-era 2.x does not work).
- **Kafka (apache/kafka 3.9) KRaft quirk**: its validator rejects the literal `0.0.0.0` in `listeners`. Use the empty-host form `INTERNAL://:19092` instead. Dual listeners: INTERNAL `kafka:19092` (in-compose), EXTERNAL `localhost:9092` (host), CONTROLLER `:9093`.

### Environment notes (this machine)
- `JAVA_HOME` must point at JDK 25: `/Users/minhazurrahman/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home` (PATH default `java` is 21). `./mvnw` respects `JAVA_HOME`.
- Docker is **Rancher Desktop**; start it with `rdctl start` if the daemon is down. It forwards published ports to `localhost` with a ~2–4s delay — that's why the smoke test polls the mapped port before wiring the datasource.
- **Host port 8080 is occupied by an unrelated process** (a pre-existing Java app). `curl localhost:8080` hits that, not FlightPulse. To run the stack locally either stop that process or remap the app's host port in `docker-compose.yml` (e.g. `8088:8080`). The repo keeps the conventional `8080`.

---

## F2 — Domain model & persistence ✅ (merged to `main`)

Branch `feature/domain-model`, merged via `--no-ff`. Later amended on
`feature/domain-model-v2` (after spec expansion) to add `PriceHistory`,
`AlertType`/`SubscriptionStatus` enums, and richer `UserSubscription` fields.

### What's in place
- **Enums**: `DealStatus` (INGESTED, SCORED, PUBLISHED, EXPIRED), `PreferredChannel` (LOG, TELEGRAM, EMAIL, SMS), `AlertType` (THRESHOLD, NEW_LOW, SCORE), `SubscriptionStatus` (ACTIVE, EXPIRED).
- **`Deal`** entity: origin, destination, airline, price, currency, departure/return dates, `discountPercentage`, `score`, `status`, `sourceAdapter`, `externalId`, JPA-managed `createdAt`/`updatedAt`. `setScore()` and `setStatus()` are the only mutators (scoring and publishing own the transitions).
- **`UserSubscription`** entity: `userId`, optional `origin`/`destination`, `travelDateFrom`/`travelDateTo` (inclusive window), `alertType`, `maxPrice` (THRESHOLD), `minScore` (SCORE), `preferredChannel`, `status` (ACTIVE/EXPIRED), `bestPriceSeen` (updated by matcher on NEW_LOW), `createdAt`. `deactivate()` → EXPIRED transition; `updateBestPrice()` persists a new low.
- **`PriceHistory`** entity: per-route price observation (origin, destination, price, currency, observedAt). Written by the scorer after each deal is scored; read to compute the rolling-median baseline.
- **Repositories**: `DealRepository` (findByStatus, findByOriginAndDestination, dedup check), `UserSubscriptionRepository` (findByStatus, findByUserId), `PriceHistoryRepository` (findByOriginAndDestinationOrderByObservedAtDesc for scorer).
- **Flyway migrations**: V2 creates `deal` + `user_subscription`; V3 adds `price_history` and the new subscription columns.
- **Tests**: 10 Testcontainers-backed repository tests — all green.

### Key decisions
- `active: boolean` replaced by `status: SubscriptionStatus` to support the housekeeping expiry flow (F12) and the subscription lifecycle spec without an extra column.
- `bestPriceSeen` lives on the subscription (not a separate table) — the matcher updates it atomically with the alert-fired event; this is enough for "best seen so far" semantics without a query join.
- `PriceHistory` is append-only; no update path. The scorer queries the most recent N rows for a route and computes the median in Java — avoids a complex window-function query while keeping the service testable.
- Cold-start (< configurable min observations) is a first-class concept: scorer sets `score = 0` and logs WARN rather than fabricating a baseline. Documented in F5 scope.

### Gotchas
- `char(3)` in PostgreSQL maps to `bpchar`, which fails Hibernate's schema validation against `varchar(3)`. Use `varchar(3)` in migrations for all fixed-length codes.
- `@SpringBootTest` tests share the application context — `contains()` on entities uses reference equality. Use `.anyMatch(s -> s.getId().equals(...))` instead.
- `PostgreSQLContainer` in Testcontainers 2.x is a raw (non-generic) class; use without type parameter.

---

## Notes for F3 — Ingestion layer (next)

Branch: `feature/ingestion-layer`. Scope in `CLAUDE.md` §9.

- `SourceAdapter` interface: single method `List<Deal> fetchDeals()`.
- Two mock adapters: `MockJsonAdapter` (JSON format, one field-name set) and `MockXmlAdapter` (XML, deliberately different field names) — both emit realistic static/semi-random deals so the normalisation layer has real work.
- One optional real adapter behind `flightpulse.ingestion.real-adapter.enabled` — if disabled or the API call fails, fall through gracefully; mocks continue.
- Normalisation service maps each adapter's raw payload into the `Deal` domain entity and saves via `DealRepository`. Check `existsByExternalIdAndSourceAdapter` to skip duplicates.
- After saving, each new deal is at status INGESTED — it flows into validation (F4) and scoring (F5) from there.
- No `@Scheduled` here — the job that calls the adapters lives in F11. For F3, wire a `IngestionService.ingestAll()` that iterates enabled adapters and normalises; it can be called from a simple `@Component` `CommandLineRunner` for manual testing if useful.
- Unit tests: mock the adapter output, assert the normalised `Deal` fields and dedup behaviour.
