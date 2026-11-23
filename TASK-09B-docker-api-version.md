# TASK-09B — Make the PostgreSQL tests actually run (Docker API version)

Branch: `postgres-docker-api` (from updated `main`). Do ONLY this task. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first.

## Problem
TASK-09 merged, but in CI all 12 tests in `PostgresFilterQueriesTest` were SKIPPED. The CI log shows Testcontainers 1.21.3 failing against the runner's Docker:
`UnixSocketClientProviderStrategy: BadRequestException (Status 400: client version 1.32 is too old. Minimum supported API version is 1.40)`
`Could not find a valid Docker environment`
`disabledWithoutDocker = true` then skips the tests quietly, so the build stays green without testing PostgreSQL.

## Files in scope (nothing else)
- NEW `src/test/resources/docker-java.properties` containing exactly one setting line: `api.version=1.44`
- `TASK-09B-docker-api-version.md` (this file)
- nothing under `src/main`, no `pom.xml` change, no change to the test class

## Task rules (Turbo mode: every command runs the moment you write it)
- Allowed: the git commands in the prompt, `./mvnw -q clean verify`, read-only commands (ls, cat, grep). Forbidden: rm, git reset/clean/rebase/stash, branch deletion, any push other than the one in the prompt.
- If a command fails twice, or `JAVA_HOME` is missing, or `git status` shows a file outside scope: STOP and report. Do not work around it.
- Do not start the app. Do not touch `data/`. No browser check.
- Do not claim the PostgreSQL tests ran unless the Maven output shows `Skipped: 0` for `PostgresFilterQueriesTest`. If the laptop has no Docker the tests stay skipped locally; say so plainly.

## Steps
1. Create `src/test/resources/docker-java.properties` with the single line `api.version=1.44` (add a comment line above it explaining: Docker Engine 29 raised the minimum API version; Testcontainers 1.x defaults to an older one).
2. Run `./mvnw -q clean verify`. The build must still pass.

## Done when
Build passes locally. The real proof is CI on the PR: the `verify` log must show `PostgresFilterQueriesTest ... Skipped: 0`.
