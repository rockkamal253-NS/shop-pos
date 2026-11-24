# ShopPOS — Cashier + Inventory for a small shop

Spring Boot 3 + Thymeleaf + HTMX point-of-sale with stock tracking. H2 file DB (dev), PostgreSQL-ready (prod).

## Run

```bash
./mvnw spring-boot:run        # dev profile, H2 file DB at ./data/shoppos
open http://localhost:8080
```

Default admin in dev: **admin / admin123** (from `application-dev.yml`).
Dev sample data adds cashier **cashier1 / cashier123** and 15 products across 4 categories.
In production, `APP_ADMIN_PASSWORD` is required (the app refuses to start otherwise) and cannot be `admin123`.
Prod: PostgreSQL — set `PROD_DB_URL`, `PROD_DB_USER`, `PROD_DB_PASSWORD`, `APP_ADMIN_PASSWORD`, then `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`. Deploy note: production must set `SPRING_PROFILES_ACTIVE=prod`; the app refuses to start with no profile.

## Tests

`./mvnw clean verify` runs the full suite. The PostgreSQL query tests use Testcontainers and need Docker; without Docker they are skipped, so before merging check that the CI log shows `Skipped: 0`.

## Deploying to production

1. Profile: set `SPRING_PROFILES_ACTIVE=prod`. The app refuses to start without a profile.
2. Environment variables: `PROD_DB_URL`, `PROD_DB_USER`, `PROD_DB_PASSWORD`, `APP_ADMIN_PASSWORD` (required, must not be `admin123`) and optionally `APP_ADMIN_USERNAME`. `SESSION_COOKIE_SECURE` defaults to `true`; set it to `false` only for a plain-HTTP local test.
3. HTTPS: the app does not terminate TLS. Put it behind a reverse proxy (nginx, Caddy, ...) that terminates HTTPS, sends `X-Forwarded-Proto` and `X-Forwarded-Host`, overwrites any `X-Forwarded-*` headers sent by clients and adds HSTS. Do not expose the app port directly to the network.
4. One instance only: carts and login lockouts live in memory. Do not run two copies. A restart logs everybody out, empties the carts and resets the login lockouts.
5. Backups: take a nightly `pg_dump` of the production database, keep several days, copy them off the server and test a restore once.
6. Database: Flyway creates and updates the schema on startup. Never edit an applied migration; add a new `V<n>__name.sql`.
7. Logs: watch for `Login lock started for user ...` (a locked account), `Expired N session(s) for user ...` (a user was cut off) and `Admin action: ...` (user created, password reset, user enabled or disabled).
8. Updates: Dependabot opens weekly pull requests. Merge only when CI is green and review Spring Boot upgrades by hand.
9. GitHub settings (once): protect `main` (pull request required, status check `verify` required, no force pushes) and keep CodeQL on.

## Project structure

```
src/main/java/com/example/shoppos/  (package by feature)
  common/    security config, money/date formatting, web advice, exceptions
  user/      User entity, roles, admin seed, dev sample-data seed
  product/   Product + Category: entities, services, controllers, forms
  inventory/ StockMovement, manual adjustments, movement history
  sale/      Sale/SaleItem, session cart, one-tx checkout + void, POS, receipts, history
  dashboard/ Admin dashboard (today's sales, low stock, top 5)
src/main/resources/
  db/migration/V1__init.sql  Flyway schema (portable H2/PostgreSQL SQL)
  templates/                 Thymeleaf pages + fragments (layout, icons, POS)
  static/                    app.css (local Tailwind build), app.js, vendored htmx, Inter font
scripts/build-css.sh         rebuilds CSS via the Tailwind standalone CLI (no CDN at runtime)
```

Currency: `app.currency` (default EGP); all money is BigDecimal scale 2. Sales and stock adjustments run
in one transaction with optimistic locking; every change writes a stock movement record.
