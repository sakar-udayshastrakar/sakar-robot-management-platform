# infrastructure/

**Status: PARTIAL — local development topology exists and is documented below; no production infrastructure-as-code exists in this directory or anywhere else in the repository.** Populated 2026-09-26 by direct inspection of `backend/docker-compose.yml`, `backend/docker/mosquitto.conf`, `backend/src/main/resources/application.yml`, and the repository root (no `.github/` directory exists). This file previously said "NOT YET IMPLEMENTED"; that described the *contents of this specific directory* (still empty — nothing lives under `infrastructure/` itself) rather than the project's actual topology, which does exist, just defined elsewhere (`backend/docker-compose.yml`). This document reports on that real topology.

## Backend

- **IMPLEMENTED (local dev):** Spring Boot 4.1.0 / Java 21, runs on port **8080**
- Health/info exposed via Spring Boot Actuator's default endpoints (`/actuator/health`, `/actuator/info`) — **NOT VERIFIED** whether `management.endpoints.web.exposure` is scoped beyond the framework default (no explicit configuration found in `application.yml`)
- **PARTIAL:** Dockerized via `backend/docker-compose.yml`'s `backend` service (builds from `backend/Dockerfile`, reaches native Postgres via `host.docker.internal`) — this is one of two supported ways to run it locally (the other being a plain `./mvnw spring-boot:run`/IDE run configuration against native Postgres directly); neither is a production deployment

## Frontend

- **IMPLEMENTED (local dev):** Vite dev server, default port **5173** (`web/vite.config.ts` has no explicit port override — Vite's own default applies)
- **NOT IMPLEMENTED:** no production build artifact is served anywhere; no Nginx/static-hosting configuration exists for the built `dist/` output

## PostgreSQL

- **IMPLEMENTED (local dev), NOT containerized by choice:** runs as a **native Windows PostgreSQL install** (`localhost:5432`, database `sakar_robot_platform`, role `sakar`) — `backend/README.md`'s own "Local development database setup" section documents this explicitly, including that an earlier Dockerized-Postgres setup was deliberately migrated away from to avoid running two Postgres instances on one machine
- The `backend` Docker Compose service reaches this same native instance via `host.docker.internal:5432` rather than a `postgres` container
- 26 Flyway migrations (`V1`–`V26`) auto-apply on startup — see `DATABASE_STATUS.md`
- **NOT IMPLEMENTED:** no production Postgres topology (replication, backup schedule, connection pooling beyond HikariCP defaults) exists anywhere in this repository

## Redis

- **IMPLEMENTED (local dev):** `redis:7` official image, port **6379**, health-checked in `docker-compose.yml` via `redis-cli ping`
- Used for login rate-limiting/lockout (per the root README's Security section)
- **NOT VERIFIED:** whether Redis persistence (`appendonly`) is enabled — the compose file sets no explicit persistence flag, so it likely runs with the image's own defaults
- **NOT IMPLEMENTED:** no production Redis topology (clustering, persistence tuning, auth) exists

## MQTT

- **IMPLEMENTED (local dev), explicitly insecure by the config's own comment:** `eclipse-mosquitto:2`, port **1883**, `backend/docker/mosquitto.conf` sets `listener 1883`, `allow_anonymous true`, `persistence false` — the file's own header comment states production requires TLS, per-robot credentials, and topic ACLs, **none of which are configured here**
- Per-robot MQTT credentials are issued and hashed application-side (`RobotCredentialService`) but **NOT enforced by the broker** — a real, previously-documented security gap
- `SAKAR_MQTT_ENABLED` defaults to `false` in the Compose backend service's own environment block — MQTT is opt-in even locally

## WebSocket

- **IMPLEMENTED (backend):** STOMP-over-WebSocket broker wiring exists (`SimpleBrokerMessageHandler` observed starting in backend startup logs), used for command-result/telemetry fan-out
- **PARTIAL/NOT WIRED (frontend):** the frontend's STOMP client exists (`web/src/websocket/stompClient.ts`) but `subscribeToRobot()` is never called anywhere in `web/src` per the root README's own audit — "live" UI updates are actually REST polling today, not push

## Keenon Cloud

- **IMPLEMENTED:** OAuth token flow, robot/area/back-point/cleaning-mode/cleaning-history/map sync, all against the real vendor API (see `DATABASE_STATUS.md`'s sync matrix) — this is an external, internet-reachable dependency, not local infrastructure this repo controls
- Toggled via `SAKAR_KEENON_ENABLED`, defaults to `false` in the Compose backend service

## Android Agent (SakarC40Agent)

- **IMPLEMENTED (software):** Eclipse Paho MQTT client, command dispatch/idempotency, real Peanut SDK calls for `GO_TO_POINT`/`RETURN_TO_DOCK` gated behind `OperatingMode.HARDWARE_TEST` (never enabled in shipped code)
- **NOT IMPLEMENTED:** no signed release build configuration (`signingConfigs`), no watchdog, no foreground service
- Not infrastructure in the server sense — runs on-device, connects outbound to the MQTT broker above
- A large, separate feature-expansion effort (`robot/SakarC40Agent/{app,data,domain,operator-ui}` plus extensive `docs/`) has landed on `dev` recently (commits `2b63481`/`74dd26c`/`14570ff`); this infrastructure document does not assess that work's own runtime/deployment needs — out of scope here

## Virtual Robot / Simulator

- **IMPLEMENTED:** `:virtual-agent` Gradle module, deterministic engine, implements `GO_TO_POINT`/`RETURN_TO_DOCK` only, reuses the real agent's MQTT stack
- **NOT VERIFIED / NOT IMPLEMENTED:** never run against a live MQTT broker in any confirmed test; no Simulator→MQTT→Backend→WebSocket→Frontend end-to-end path has ever been exercised (per the root README)

## Ports (local dev)

| Port | Service |
|---:|---|
| 5173 | Frontend (Vite dev server) |
| 8080 | Backend (Spring Boot) |
| 5432 | PostgreSQL (native, not containerized) |
| 6379 | Redis |
| 1883 | MQTT (Mosquitto) |

## Environment Variables (local dev, non-secret defaults only — see Security below)

`SAKAR_DB_URL`, `SAKAR_DB_USERNAME`, `SAKAR_DB_PASSWORD`, `SAKAR_REDIS_HOST`, `SAKAR_REDIS_PORT`, `SAKAR_MQTT_ENABLED`, `SAKAR_KEENON_ENABLED`, `SAKAR_MAPS_STORAGE_ROOT`, `SAKAR_JWT_SECRET`, `SAKAR_BOOTSTRAP_ADMIN`, `SAKAR_BOOTSTRAP_ADMIN_EMAIL`, `SAKAR_BOOTSTRAP_ADMIN_PASSWORD` — all read from `backend/docker-compose.yml`/`application.yml`/`backend/README.md`. The committed `docker-compose.yml` ships a placeholder `SAKAR_JWT_SECRET` explicitly labeled "dev-only-do-not-use-in-any-real-environment-change-me" in its own comment — this is a real, self-flagged gap for anyone deploying from this file as-is.

## Local Topology

```
Vite dev server (5173) ──REST/JSON──> Spring Boot backend (8080) ──JDBC──> native PostgreSQL (5432)
                                           │
                                           ├──> Redis (6379, rate-limit/lockout state)
                                           ├──> Mosquitto MQTT (1883, opt-in, anonymous/unencrypted)
                                           └──> Keenon Cloud (external HTTPS, opt-in)

SakarC40Agent (Android, on-device) ──MQTT──> Mosquitto (1883)
:virtual-agent (simulator) ──MQTT──> Mosquitto (1883), never live-confirmed
```

## Production Topology

**NOT IMPLEMENTED.** No production Docker Compose, Kubernetes manifest, Nginx/TLS-termination config, or cloud provider IaC (Terraform/CloudFormation/etc.) exists anywhere in this repository. `backend/docker-compose.yml`'s own header comment explicitly states it is dev-only and describes (without implementing) an intended "hardened deployment view" — WAF/Nginx in front, database/broker on a private network with no host port exposure. None of that exists as actual configuration today.

## Security

- Secrets: the only committed "secret" is the explicitly-labeled dev-only JWT secret in `docker-compose.yml` — **NOT IMPLEMENTED:** no secrets manager (Vault, AWS Secrets Manager, etc.) integration exists
- MQTT: **NOT IMPLEMENTED** — anonymous, unencrypted broker access in the committed config (see MQTT section above)
- Bootstrap admin password: **IMPLEMENTED** as of this pass — environment-variable-based (`SAKAR_BOOTSTRAP_ADMIN_PASSWORD`), never a literal default in any tracked file, always BCrypt-hashed before persistence, never logged
- TLS: **NOT IMPLEMENTED** anywhere in the local topology (plain HTTP/JDBC/MQTT throughout)
- See `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` and `docs/security/SAKAR_SECURITY_RISK_REGISTER.md` for the full control specification and risk register (design/requirements documents, not re-audited claim-by-claim in this pass)

## Observability

- Logs: SLF4J/Logback console logging only — **NOT IMPLEMENTED:** no centralized log aggregation (ELK, Loki, etc.)
- Metrics: **NOT IMPLEMENTED** beyond bare Actuator health/info — no Prometheus/Grafana despite being named in the master requirements (Part 32) as the intended stack
- Alerts: **NOT IMPLEMENTED** — the in-app `robot_alerts` domain (low-battery/offline) is an application feature, not infrastructure alerting/paging
- Health checks: **IMPLEMENTED** — Actuator `/actuator/health`, plus Docker Compose's own `redis` healthcheck

## Backup / Recovery

**NOT IMPLEMENTED.** No backup schedule, retention policy, or restore procedure exists for PostgreSQL, Redis, or the raw map-image volume (`sakar_maps_data`) anywhere in this repository.

## Docker / CI/CD

- Docker: **PARTIAL** — one dev-only `docker-compose.yml` (Redis + Mosquitto + backend container) exists; no production Dockerfile hardening (non-root user, multi-stage minimal base, etc.) was verified in this pass
- CI/CD: **NOT IMPLEMENTED** — no `.github/workflows/` directory or any other CI configuration (Jenkins, GitLab CI, CircleCI, etc.) exists anywhere in the repository, confirmed by direct filesystem check

## Summary

| Area | Status |
|---|---|
| Backend runtime | IMPLEMENTED (local dev) |
| Frontend runtime | IMPLEMENTED (local dev) |
| PostgreSQL | IMPLEMENTED (local dev, native, not containerized) |
| Redis | IMPLEMENTED (local dev) |
| MQTT | IMPLEMENTED (local dev) — insecure by the config's own admission |
| WebSocket | PARTIAL — backend implemented, frontend never subscribes |
| Keenon Cloud | IMPLEMENTED (external dependency) |
| Android Agent | IMPLEMENTED (software), NOT IMPLEMENTED (release signing/watchdog) |
| Simulator | IMPLEMENTED, NOT VERIFIED against a live broker |
| Production topology | NOT IMPLEMENTED |
| Secrets management | NOT IMPLEMENTED |
| TLS | NOT IMPLEMENTED |
| Observability (metrics/logs/alerts beyond health) | NOT IMPLEMENTED |
| Backup/recovery | NOT IMPLEMENTED |
| CI/CD | NOT IMPLEMENTED |
