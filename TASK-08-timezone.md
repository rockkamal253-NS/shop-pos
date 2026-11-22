# TASK-08 — One timezone for all timestamps

Branch: `timezone-fix` (created from updated `main`, separate from `account-security`). Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md first.

## Problem
TASK-05 added a `Clock` bean (zone `app.timezone`, default `Africa/Cairo`) used by `DashboardService` for "today".
But `@PrePersist` hooks and other code still call `LocalDateTime.now()` with the server's own timezone. If the server is not set to Cairo time,
sales made near midnight can be stored on one day and counted on another, so the dashboard totals are wrong.

## Files in scope
- every class that calls `LocalDateTime.now()` / `LocalDate.now()` / `Instant.now()` in `src/main/java` (find them with grep first and list them in your report)
- `src/main/java/com/example/shoppos/ShopPosApplication.java` (the main class) only if needed
- `src/main/resources/application.yml` (only the existing `app.timezone` property)
- tests

## Steps
1. Grep `src/main/java` for `.now()` calls. List each one with its class.
2. Make every timestamp use the app timezone:
   - Preferred (smallest change): in the main class set the JVM default timezone from `app.timezone` before the application starts, for example `TimeZone.setDefault(TimeZone.getTimeZone("<zone>"))` read from the property (default `Africa/Cairo`), so `LocalDateTime.now()` everywhere uses it. Put a comment explaining why.
   - Do not rewrite every entity to take a `Clock` unless the preferred way cannot work. State which you chose and why.
3. `DashboardService` keeps using the `Clock` bean; make sure the `Clock` and the JVM default use the SAME property.
4. Do not change stored data or add a migration. Old rows keep their values.

## Tests to add
- A test proving the default timezone is the configured one after the application context starts (`ZoneId.systemDefault()` equals the `app.timezone` value).
- A dashboard test with a fixed `Clock` at 23:50 Cairo time: a sale created 10 minutes earlier counts as "today", one created at 00:10 the next day does not.

## Browser check
- Start the app, make a sale, open the dashboard: today's total includes it.
- Report the time shown on the sale detail page and say whether it matches Cairo time now.

## Done when
`./mvnw -q clean verify` passes and your report lists every `.now()` call you found and how each one is now covered.
