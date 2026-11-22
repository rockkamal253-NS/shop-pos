# DB-RULES — shop-pos (read before touching entities, queries or migrations)

Stack: Flyway migrations, `ddl-auto: validate`, H2 file DB in dev/test, PostgreSQL in prod.
Every change must work on BOTH H2 and PostgreSQL.

## Migrations (Flyway)
- NEVER edit or rename an existing `V*__*.sql` file. Flyway checksums break. Every schema change is a NEW file: `V<next>__<short_name>.sql`.
- Use only portable SQL (works on H2 and PostgreSQL). No vendor functions, no `SERIAL`, no `ON CONFLICT`, no `ILIKE`.
- Adding a NOT NULL column to an existing table needs a DEFAULT (or a backfill) so old rows survive.
- Keep entities and schema in sync: `ddl-auto: validate` must pass. Column types must match the entity (`BigDecimal` -> `NUMERIC(12,2)`, `Long` -> `BIGINT`).
- Never drop or rename a column/table in the same change that stops using it. Two steps: stop using, then remove later.
- Never put sample/test data in a migration. Dev data lives in `DevSeedRunner`.

## Money and numbers
- Money is `NUMERIC(12,2)` / `BigDecimal` scale 2, `RoundingMode.HALF_UP`. Max stored value is 9999999999.99.
- Any new money input needs an upper bound (`@DecimalMax`) so it can never overflow the column.
- Keep the CHECK constraints (non-negative prices/stock, quantity > 0). Do not remove them to make a test pass.

## Queries
- Pagination + collection fetch: never combine `@EntityGraph`/`JOIN FETCH` on a collection with `Pageable` (in-memory paging, warning HHH90003004). Fetch the page first, then load details with `@BatchSize` or a second query.
- Optional filters (`:x is null or ...`) must be written so PostgreSQL can infer the parameter type (cast or pass typed values). Test each new optional-filter query on PostgreSQL, not only H2.
- Use `open-in-view: false` as the rule: anything a template reads from a lazy association must be loaded inside the service transaction.
- Concurrency: entities that change stock or money keep `@Version`. Catch `OptimisticLockingFailureException` where the user acts and show a clear message.

## Data safety (applies to you in Turbo mode)
- Never delete, edit or reset the `data/` folder or any `*.mv.db` file. Back it up before running the app against it.
- Prod config files (`application-prod.yml`) are never edited to make a test or browser check work. Override on the command line instead, for example:
  `--spring.datasource.url=jdbc:h2:file:<temp path outside the repo> --spring.datasource.driver-class-name=org.h2.Driver`
- Use a temp database outside the repo for any destructive or fresh-start check.

## Verify DB changes
- New migration: confirm it applies on an EMPTY H2 database and on a COPY of the existing dev database.
- If Testcontainers/PostgreSQL is available, run the repository tests on PostgreSQL too. If it is not, say so in your report. Do not claim PostgreSQL was tested.
