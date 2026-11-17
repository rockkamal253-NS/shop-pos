# TASK-02 — No default admin credentials outside dev

## Problem
`application.yml` commits `admin / admin123`. `AdminSeedRunner` runs in every profile, and
`application-prod.yml` does not override it. If `APP_ADMIN_PASSWORD` is unset in prod, `admin/admin123` is created.
The default profile is `dev`, so forgetting to select `prod` also seeds `cashier1/cashier123`.

## Files in scope
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- `src/test/resources/application-test.yml` (keep working)
- `src/main/java/com/example/shoppos/user/AdminSeedRunner.java`
- `README.md` (update the credentials section)
- new test class under `src/test/java/com/example/shoppos/user/`

## Steps
1. `application.yml`: remove `app.admin.password`. Keep `username` and `display-name` defaults. Password comes from `${APP_ADMIN_PASSWORD:}` (empty by default).
2. `application-dev.yml`: add `app.admin.password: admin123` so local dev still works exactly as before.
3. `application-test.yml`: already sets its own admin password; leave as is.
4. `AdminSeedRunner`: inject `Environment`. Before creating the admin, if the password is blank → throw `IllegalStateException("APP_ADMIN_PASSWORD must be set")` when the `dev` and `test` profiles are NOT active. If the admin already exists, skip everything (current behavior).
   - In prod also reject the value `admin123` with the same exception.
5. `README.md`: say that dev uses `admin/admin123`, and prod requires `APP_ADMIN_PASSWORD` (app refuses to start otherwise).

## Tests to add
- Unit test for `AdminSeedRunner` (plain constructor + mock `UserRepository`, `PasswordEncoder`, `MockEnvironment`):
  - prod profile + blank password → throws.
  - prod profile + `admin123` → throws.
  - prod profile + strong password → saves user.
  - dev profile + `admin123` → saves user.
  Use `ReflectionTestUtils.setField` for the `@Value` fields.

## Done when
- `./mvnw spring-boot:run` (dev) still logs "Seeded admin user 'admin'".
- Starting with `-Dspring-boot.run.profiles=prod` and no `APP_ADMIN_PASSWORD` on an empty DB fails fast with a clear message.

## Out of scope (do NOT do now)
Change-password screen and login lockout. Note them in your reply as follow-ups.
