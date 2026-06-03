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

## Notes for F2 — Domain model & persistence (next)

Branch: `feature/domain-model`. Scope in `CLAUDE.md` §9.

- `Deal` entity + status enum `INGESTED → SCORED → PUBLISHED → EXPIRED`.
- `UserSubscription` entity — **must include `preferredChannel`** (enum `LOG, TELEGRAM, EMAIL, SMS`, default `LOG`); F9 routing depends on it.
- Spring Data JPA repositories.
- Flyway migration `V2__...sql` creates the real tables. `pgcrypto` is already enabled (use `gen_random_uuid()` if UUID PKs are wanted). Keep `ddl-auto: validate` — entities must match the migration exactly.
- Add a Testcontainers slice or reuse the existing pattern for repository tests.
- Remember per `CLAUDE.md` §6: constructor injection, records for DTOs, few lowercase single-line comments, named constants, no God classes.

### Workflow reminder
Feature-by-feature with checkpoints: build one feature fully (code + tests + Swagger + Postman where applicable), then stop for review before the next. Git remote to be added later by the user.
