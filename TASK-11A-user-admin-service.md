# TASK-11A — Admin user management: service layer (no UI yet)

Branch: `admin-users-service` (from `origin/main`). Do ONLY this task. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first.

## Problem
In production there is only the seeded admin. `DevSeedRunner` (dev only) is the only place cashiers are created, and nobody can set or reset another user's password. The owner wants the ADMIN to manage local users: create accounts and set/reset their passwords. This task adds the service logic and its tests. The screen comes in TASK-11B.

## Files in scope (nothing else)
- NEW `src/main/java/com/example/shoppos/user/UserAdminService.java`
- `src/main/java/com/example/shoppos/user/User.java`: add ONLY `public void setEnabled(boolean enabled)`
- NEW `src/test/java/com/example/shoppos/user/UserAdminServiceTest.java`
- `TASK-11A-user-admin-service.md` (this file)
- NO controller, template, SecurityConfig, migration, pom.xml or repository change. Use the existing `UserRepository` methods (`findById`, `findAll(Sort)`, `existsByUsername`, `findByRoleOrderByIdAsc`, `save`), the existing `PasswordEncoder` and `LoginAttemptService` beans, and the existing `BusinessRuleException` (common.web) for every user-facing error.

## Task rules (Turbo mode: every command runs the moment you write it)
- Allowed: the git commands in the prompt, `./mvnw -q clean verify`, read-only commands. Forbidden: rm, git reset/clean/rebase/stash, branch deletion, any push other than the one in the prompt.
- If a command fails twice, or `JAVA_HOME` is missing, or `git status` shows a file outside scope: STOP and report.
- Do not start the app. Do not touch `data/`. No browser check.
- Never log or return a password or hash. Never weaken a test.
- Build the test class in small steps (the Gemini copyright filter blocks big boilerplate: if blocked, STOP and report).

## `UserAdminService` (constructor injection, `@Service`, `@Transactional` on writes, `@Transactional(readOnly = true)` on reads; match the style of `AccountService`)
Messages are short plain English. Password rule everywhere: 8 to 72 characters.
1. `List<User> listUsers()`: all users ordered by id.
2. `User createUser(String username, String displayName, String rawPassword, Role role)`:
   - username: trimmed, must match `^[a-z0-9._-]{3,50}$` else "Username must be 3-50 characters: lowercase letters, digits, dot, dash or underscore."
   - displayName: trimmed, not blank, max 100.
   - password 8-72, role not null.
   - username already exists -> "That username is already taken."
   - store `passwordEncoder.encode(rawPassword)`; return the saved user.
3. `void resetPassword(Long actingUserId, Long targetUserId, String newPassword)`:
   - target must exist ("User not found.").
   - target == acting user -> "Use Change password for your own account." (keeps the current-password check of TASK-07 for self changes).
   - password 8-72.
   - set the new hash, save, and clear the target's login lock (`loginAttemptService.loginSucceeded(target.getUsername())`).
4. `void setEnabled(Long actingUserId, Long targetUserId, boolean enabled)`:
   - target must exist.
   - acting user cannot change their own enabled flag -> "You cannot disable your own account."
   - disabling the last enabled ADMIN is rejected -> "At least one enabled admin is required."
   - otherwise set and save.

## Tests (`UserAdminServiceTest`, Mockito `UserRepository` + real `BCryptPasswordEncoder` + a real `LoginAttemptService` with a fixed `Clock`, like `AccountServiceTest`)
- createUser: valid CASHIER saved with a BCrypt hash that matches the raw password; duplicate username rejected; username too short / uppercase / with a space rejected; 7-char and 73-char passwords rejected; blank display name rejected; null role rejected; nothing saved on any rejection.
- resetPassword: success changes the hash and the new password matches; unknown target rejected; target == acting user rejected and hash unchanged; 7-char password rejected; a locked target (5 failed logins on the `LoginAttemptService`) is unlocked after the reset.
- setEnabled: disabling a cashier works; disabling yourself rejected; disabling the only enabled admin rejected; disabling an admin when another enabled admin exists works; re-enabling works.
- listUsers returns what the repository returns, ordered by id.

## Done when
`./mvnw -q clean verify` passes, and the report lists each rule above with the test that proves it.

## Out of scope (list as follow-ups, do not do)
The admin screen (TASK-11B); ending the active session of a disabled user (Spring Security checks `enabled` only at login, so an already logged-in user stays in until logout or session expiry); forced password change on first login; edit display name or role.
