# TASK-10 — Refuse to start without an explicit profile

Branch: `profile-guard` (from `origin/main`). Do ONLY this task. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first.

## Problem
`application.yml` has `spring.profiles.default: dev`. A deployment that forgets `SPRING_PROFILES_ACTIVE=prod` silently runs with dev settings: H2 file database, admin password `admin123`, 100% cashier discount cap, dev sample data. Production must be safe by default.

## Design (decided, do not change)
1. Remove `spring.profiles.default: dev` from `application.yml`.
2. Keep `./mvnw spring-boot:run` starting the dev profile: add the Maven property `<spring-boot.run.profiles>dev</spring-boot.run.profiles>` to the `<properties>` block of `pom.xml`. (A `-Dspring-boot.run.profiles=prod` on the command line must still override it. Do NOT put `<profiles>` inside the plugin `<configuration>`, that would block the override.)
3. In `ShopPosApplication.main()` only (NOT in the class constructor, NOT in a bean, so tests and slice tests are unaffected): build a `SpringApplication`, add an `ApplicationListener<ApplicationEnvironmentPreparedEvent>` that calls a small static method `requireKnownProfile(Environment env)`. If `env.acceptsProfiles(Profiles.of("dev", "test", "prod"))` is false, throw `IllegalStateException("No Spring profile is active. Set SPRING_PROFILES_ACTIVE=prod for production (or dev for local development).")`.
4. README: in the production line add one short deploy note: production must set `SPRING_PROFILES_ACTIVE=prod`; the app refuses to start with no profile.

## Files in scope (nothing else)
- `src/main/java/com/example/shoppos/ShopPosApplication.java` (main method + the static check only; keep the timezone constructor exactly as it is)
- `src/main/resources/application.yml` (remove the one default-profile line only)
- `pom.xml` (the one property only)
- `README.md` (the deploy note only)
- one new test class `src/test/java/com/example/shoppos/ShopPosApplicationProfileTest.java`
- existing test classes ONLY if they fail because they relied on the implicit `dev` profile: add `@ActiveProfiles("test")` to them and list every one in your report. Never change assertions.
- `TASK-10-profile-guard.md` (this file)

## Task rules (Turbo mode: every command runs the moment you write it)
- Allowed: the git commands in the prompt, `./mvnw` commands written in this task, `java -jar` for the startup check below, read-only commands. Forbidden: rm (except deleting the temp database file you created yourself under the system temp folder), git reset/clean/rebase/stash, branch deletion, editing `application-prod.yml` or `application-dev.yml`, any push other than the one in the prompt.
- If a command fails twice, or `JAVA_HOME` is missing, or `git status` shows a file outside scope: STOP and report.
- Never run the app against `data/`. Use a temp database outside the repo and port 8099 for every start. Kill every process you start.
- Do not weaken or delete any test.

## Tests to add (`ShopPosApplicationProfileTest`, plain unit test with `org.springframework.mock.env.MockEnvironment`, no Spring context)
- no active profile -> `requireKnownProfile` throws `IllegalStateException` and the message contains `SPRING_PROFILES_ACTIVE`
- `dev`, `test`, `prod` each -> no exception
- an unknown profile only (for example `staging`) -> throws

## Startup check (after the build is green, report PASS/FAIL with the exact log line)
1. `./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8099 --spring.datasource.url=jdbc:h2:file:<temp path outside the repo>;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver"`: the log shows `The following 1 profile is active: "dev"`. Then stop it.
2. `./mvnw -q -DskipTests package`, then `java -jar target/shop-pos-1.0.0.jar --server.port=8099` with NO profile: the app refuses to start and the log shows the `No Spring profile is active...` message. Nothing may keep running afterwards.

## Done when
`./mvnw -q clean verify` passes and both startup checks are reported with evidence.
