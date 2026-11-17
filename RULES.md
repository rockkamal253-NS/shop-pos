# RULES — shop-pos (Antigravity / Flash, TURBO mode)

You are running in **Turbo mode**: terminal commands execute automatically without asking me.
Treat every command as if it runs the instant you write it.

## Hard limits (never do, even if a task seems to need it)
- No `git push`, `git push --force`, `git reset --hard`, `git clean`, `git rebase`, branch deletion.
- No `rm -rf` on anything except `target/`. Never touch `data/` (the dev H2 database).
- No editing `V1__init.sql` (Flyway checksum breaks). Schema changes go in a new `V2__*.sql`.
- No new dependencies in `pom.xml` unless the task file says so.
- No network downloads other than what `./mvnw` already does.
- Do not weaken or delete existing tests to make the build pass. Add new tests; fix the code.
- Do not edit files outside the "Files in scope" list of the current task.

## Workflow
1. Do ONE task file at a time, in numeric order (TASK-01 → TASK-05). Do not start the next until the current one is done.
2. Read every file in scope before editing it.
3. Make the change, add the tests the task lists, then run: `./mvnw -q clean verify`
4. If the build fails, fix and re-run. If it still fails after 2 attempts, STOP and report the error. Do not guess further.
5. When green, commit locally: `git add -A && git commit -m "<task id>: <short summary>"`. Never push.
6. Reply with: files changed, tests added, build result. Max 10 lines. No recap of the task.

## Code conventions
- Java 21, Spring Boot 3.5, package-by-feature (keep the existing layout).
- Money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Never use `double`/`float` for money.
- Keep controllers thin; business rules live in services.
- Error messages shown to users are short, plain English, no stack traces.
- Match the existing code style (constructor injection, no Lombok, no field injection).

## Definition of done (every task)
- `./mvnw -q clean verify` passes.
- New behavior has at least one test that fails without the change.
- No unrelated formatting churn in the diff.

## Manual browser check (after the build is green)
Run this only when the task file has a "Browser check" section.
1. Start the app in dev: ./mvnw spring-boot:run (dev profile, port from application.yml). Run it in the background and wait until the log shows it has started.
2. Open it only at http://localhost:<port>. Never browse to any external site and never sign in to any real account.
3. Log in with the dev accounts only (admin / cashier1; passwords are in application-dev.yml and README).
4. Do exactly the steps in the task's "Browser check" list and report PASS or FAIL for each. A FAIL means fix it and rerun the build, not skip it.
5. Stop the app when done (kill the process you started). Do not leave it running.
6. Do not commit screenshots, recordings, or the data/ folder.
7. If the app fails to start or the browser step is blocked, report it and stop. Do not change config to work around it.
