# TASK-09 — Optional-filter queries on PostgreSQL

Branch: `postgres-filter-tests` (from updated `main`). Do ONLY this task. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first.

## Problem
Everything so far ran on H2. The optional-filter queries `(:x is null or ...)` in `ProductRepository.search` and `SaleRepository.search` can fail on PostgreSQL when it cannot infer the parameter type. DB-RULES.md requires each optional-filter query to be tested on PostgreSQL.

## Files in scope (nothing else)
- `pom.xml`: add ONLY test-scope `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` (versions from the Spring Boot BOM; if a version is not managed, STOP and report)
- `TASK-09-postgres-filters.md` (this file)
- one new class `src/test/java/com/example/shoppos/common/PostgresFilterQueriesTest.java`
- nothing under `src/main`

## Task rules (Turbo mode: every command runs the moment you write it)
- Allowed: the git commands in the prompt, `./mvnw -q clean verify`, read-only commands (ls, cat, grep). Forbidden: rm, git reset/clean/rebase/stash, branch deletion, editing `application*.yml`, any push other than the one in the prompt.
- If a command fails twice, or `JAVA_HOME` is missing, or `git status` shows a file outside scope: STOP and report. Do not work around it.
- Do not start the app. Do not touch `data/`. No browser check.
- If a query FAILS on PostgreSQL, do NOT fix it. Report the exact error text and which query.
- Build the test class in small steps. If the Gemini copyright filter blocks it, STOP and report.

## Test class
- `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)`, `@Testcontainers(disabledWithoutDocker = true)`, a `postgres:16` container.
- Properties via `@DynamicPropertySource`: datasource url/username/password, driver `org.postgresql.Driver`, Flyway enabled (real migrations run on PostgreSQL), `ddl-auto: validate`. If `application-test.yml` forces H2, override inside the test class, not in the yml.
- Seed through the repositories: products with different names/SKUs/barcodes (one with a null barcode); sales with different `createdAt` and two different cashiers.

## Assertions
- `ProductRepository.search`: `q` null returns all; a fragment matches name, SKU and barcode case-insensitively; a fragment matching nothing returns empty; paging works.
- `SaleRepository.search`: `from`/`to`/`cashierId` all null; only `from`; only `to`; only `cashierId`; all three together; paging works.

## Done when
`./mvnw -q clean verify` passes, and the report says how many PostgreSQL tests actually RAN (skipped is not passed) and lists any query that failed with its exact error.
