# TASK-11B — Admin users screen (/admin/users)

Branch: `admin-users-screen` (from `origin/main`, which must already contain `UserAdminService` from TASK-11A). Do ONLY this task. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first.

## Problem
TASK-11A added `UserAdminService` (list, create, reset password, enable/disable) with all rules and tests, but there is no screen. The ADMIN must be able to create local users and set/reset their passwords from the app.

## Files in scope (nothing else)
- NEW `src/main/java/com/example/shoppos/user/AdminUserController.java`
- NEW `src/main/resources/templates/admin/users.html`
- `src/main/resources/templates/fragments/layout.html`: add ONE admin-only "Users" link to the sidebar, same markup pattern as the existing admin links (`th:if="${isAdmin}"`, `currentPath.startsWith('/admin/users')`)
- `src/main/resources/templates/fragments/icons.html`: only if no existing icon suits the link; then add ONE new icon fragment named `users` in the same style as the others
- NEW `src/test/java/com/example/shoppos/user/AdminUserControllerTest.java`
- `TASK-11B-admin-users-screen.md` (this file)
- NO change to `UserAdminService`, `SecurityConfig` (`/admin/**` is already ADMIN-only), entities, migrations, `pom.xml`.

## Task rules (Turbo mode: every command runs the moment you write it)
- Allowed: the git commands in the prompt, `./mvnw` commands, `curl` to `http://localhost:8099` only, read-only commands. Forbidden: rm (except a temp database file you created under the system temp folder), git reset/clean/rebase/stash, branch deletion, any push other than the one in the prompt, editing `application*.yml`.
- If a command fails twice, or `JAVA_HOME` is missing, or `git status` shows a file outside scope: STOP and report.
- Start the app only for the browser check, only on port 8099 with a temp database outside the repo (override on the command line, never `data/`). Kill every process you start.
- Never log, echo or put a password in a model attribute, flash attribute, URL or template. Password inputs are never pre-filled.
- Build in small steps in this order, running the build between steps (the Gemini copyright filter blocks big boilerplate: if blocked, STOP and report): controller, template, navbar link, tests.

## Controller (`@Controller @RequestMapping("/admin/users")`, constructor injection, thin; match `AccountController` style)
Use `@AuthenticationPrincipal UserPrincipal principal` for the acting user id (`principal.getId()`). Plain `@RequestParam` fields, no form classes. Every business error is thrown by `UserAdminService` as `BusinessRuleException`; the existing `GlobalExceptionHandler` already turns it into a red toast and redirects back, so do NOT catch it. Success = `redirectAttributes.addFlashAttribute("toastSuccess", "...")` then `redirect:/admin/users`.
- `GET /admin/users`: model `users` (from `listUsers()`), `roles` (`Role.values()`), `currentUserId`; view `admin/users`.
- `POST /admin/users`: params `username`, `displayName`, `password`, `confirmPassword`, `role`. If `password` != `confirmPassword` throw `BusinessRuleException("Passwords do not match.")`. Call `createUser`. Toast "User created."
- `POST /admin/users/{id}/password`: params `newPassword`, `confirmPassword`. Same mismatch check. Call `resetPassword(principal.getId(), id, newPassword)`. Toast "Password updated."
- `POST /admin/users/{id}/enabled`: param `enabled` (boolean). Call `setEnabled(principal.getId(), id, enabled)`. Toast "User enabled." / "User disabled."

## Template `admin/users.html`
Follow `templates/account/password.html` (layout fragment, `card`, `input`, `label`, `btn` classes, `th:action` so the CSRF token is included).
- A table of users: username, display name, role, status badge (Enabled / Disabled), created date.
- Per row: a small "Reset password" form (two password inputs `newPassword` / `confirmPassword`, `minlength="8" maxlength="72"`, `autocomplete="new-password"`) inside a `<details>` element, and an Enable/Disable button posting to `/admin/users/{id}/enabled`. For the logged-in admin's own row show "You" instead of both actions (the service rejects them anyway).
- Above or beside the table: a "New user" card with `username`, `displayName`, `password`, `confirmPassword`, and a `role` select (CASHIER first). Help text under username: "3-50 characters: lowercase letters, digits, dot, dash or underscore."
- Short plain-English labels; no inline JavaScript.

## Tests (`AdminUserControllerTest`: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`, like `AccountControllerTest`; use `.with(csrf())` on every POST)
- anonymous GET `/admin/users` -> redirected to `/login`
- CASHIER GET `/admin/users` -> 403
- ADMIN GET `/admin/users` -> 200 and the page contains the seeded usernames
- ADMIN POST create (valid CASHIER) -> redirect to `/admin/users`; the new user can authenticate with the password (check with `PasswordEncoder.matches` on the stored hash)
- CASHIER POST create -> 403 and no user created
- ADMIN POST create with mismatching passwords -> redirect back with an error flash; no user created
- ADMIN POST `/{id}/password` for a cashier -> new password matches the stored hash
- CASHIER POST `/{id}/password` -> 403 and the hash is unchanged
- ADMIN POST `/{id}/enabled=false` for a cashier -> user disabled; a cashier cannot disable anyone (403)
- ADMIN trying to reset or disable their own id -> redirect with an error flash, nothing changed
Clean up any rows a test created so other tests are not affected.

## Browser check (after the build is green; RULES.md "Manual browser check" applies; temp database, port 8099, dev profile via `./mvnw spring-boot:run`)
Report PASS/FAIL with the exact message seen for each:
- Log in as `admin`; the sidebar shows "Users" and the page lists admin and cashier1.
- Create user `sara2`, display name "Sara Two", role CASHIER, a valid password: toast "User created." and the row appears.
- Log out, log in as `sara2` with that password: works; open `/admin/users` as `sara2`: 403.
- As admin reset sara2's password; log in as sara2 with the new one: works, the old one fails.
- As admin disable sara2: sara2 can no longer log in (generic login error).
- On the admin's own row there are no reset/disable buttons.

## Done when
`./mvnw -q clean verify` passes and the report gives every browser-check result with evidence.

## Out of scope (list as follow-ups, do not do)
Ending the active session of a disabled user; editing display name or role; deleting users; password strength rules beyond 8-72 characters; repopulating the create form after an error.
