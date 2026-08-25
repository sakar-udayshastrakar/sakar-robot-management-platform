# database/

**Status: NOT YET IMPLEMENTED.** Reserved for PostgreSQL schema migration scripts and any database-side tooling (e.g., a migration framework's versioned SQL/changesets) implementing the schema specified in `docs/architecture/SAKAR_ROBOT_PLATFORM_DATABASE.md`.

Includes, per that specification, tables for: `organizations`, `sites`, `users`, `roles`, `permissions`, `robots`, `robot_models`, `robot_credentials`, `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_alerts`, `robot_commands`, `command_results`, `robot_locks`, `robot_tasks`, `task_events`, `maps`, `map_points`, `cleaning_sessions`, `charging_sessions`, `notifications`, `audit_logs`, `application_logs`.

**Security note carried forward from `docs/security/SAKAR_SECURITY_REQUIREMENTS.md`:** the `audit_logs` table must be append-only at the database-role level (`INSERT`/`SELECT` only, no `UPDATE`/`DELETE` for the application role) — this is a schema/migration requirement, not an application-code convention.

**Current phase: DOCUMENTATION / ARCHITECTURE PREPARATION.** No migration scripts exist here yet; no database has been created or modified.
