# TASK-09C — Fix the optional-filter queries on PostgreSQL

Branch: `postgres-docker-api` (continue on it, do not create a new one). Do ONLY this task. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first.

## Problem (found by TASK-09 in CI: PostgresFilterQueriesTest ran, 8 of 12 failed)
PostgreSQL cannot infer the type of a bare `?` in `? is null`. H2 hides this.
- `SaleRepository.search` (6 failing tests): `ERROR: could not determine data type of parameter $1`. Fails for every combination of filters, including all three given.
- `ProductRepository.search` (2 failing tests, the ones that pass `q = null`): `ERROR: function lower(bytea) does not exist`. A null `q` is bound as bytea inside `lower(concat('%', ?, '%'))`.
Non-null product searches already pass. Production runs PostgreSQL, so `/sales` and the empty product search are broken there.

## Files in scope (nothing else)
- `src/main/java/com/example/shoppos/sale/SaleRepository.java` (only the `search` query)
- `src/main/java/com/example/shoppos/product/ProductRepository.java` (only the `search` query)
- `TASK-09C-postgres-filter-casts.md` (this file)
- do NOT change the test class, entities, services, templates or `pom.xml`

## Task rules (Turbo mode: every command runs the moment you write it)
- Allowed: the git commands in the prompt, `./mvnw -q clean verify`, read-only commands (ls, cat, grep). Forbidden: rm, git reset/clean/rebase/stash, branch deletion, any push other than the one in the prompt.
- If a command fails twice, or `JAVA_HOME` is missing, or `git status` shows a file outside scope: STOP and report. Do not work around it.
- Do not start the app. Do not touch `data/`. No browser check.
- The laptop has no Docker, so you CANNOT run the PostgreSQL tests locally. Do not claim they pass. Only CI can prove it.

## Steps
1. `SaleRepository.search`: give each optional parameter a typed cast in its null test, keep the real comparison unchanged:
   - `(cast(:from as LocalDateTime) is null or s.createdAt >= :from)`
   - `(cast(:to as LocalDateTime) is null or s.createdAt < :to)`
   - `(cast(:cashierId as Long) is null or s.cashier.id = :cashierId)`
2. `ProductRepository.search`: every occurrence of `:q` becomes `cast(:q as String)`:
   - `(cast(:q as String) is null or lower(p.name) like lower(concat('%', cast(:q as String), '%')) or lower(p.sku) like lower(concat('%', cast(:q as String), '%')) or lower(p.barcode) like lower(concat('%', cast(:q as String), '%')))`
3. Keep the method signatures and `@EntityGraph` exactly as they are. Do not combine any collection fetch with `Pageable`.
4. Run `./mvnw -q clean verify`. The existing H2 tests must all still pass; do not change or weaken any test.

## Done when
Local build passes. The real proof is CI on the PR: the `verify` log shows `PostgresFilterQueriesTest ... Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.
