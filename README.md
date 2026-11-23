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

`./mvnw clean verify` — 39 tests covering pricing math, the sale transaction, stock rules and the dashboard.

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
