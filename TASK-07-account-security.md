# TASK-07 — Change-password screen and login throttling

Branch: `account-security` (created from updated `main`). Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md (and AWAY-MODE.md if the prompt says I am away) first.

## Problems
A. There is no way to change a password. The seeded admin keeps whatever password it was created with.
B. There is no login throttling. Anyone can guess passwords without limit.

## Files in scope
- `src/main/java/com/example/shoppos/user/` (new `AccountController`, `PasswordChangeForm`, `AccountService`, `LoginAttemptService`, listener class; small edits to `AppUserDetailsService` / `UserPrincipal` only if needed)
- `src/main/java/com/example/shoppos/common/config/SecurityConfig.java` (only if a matcher change is truly needed)
- `src/main/resources/templates/account/password.html` (new) and the layout/navbar template (one link only)
- `src/main/resources/application.yml`
- new tests under `src/test/java/com/example/shoppos/user/`
- NO database migration (no schema change). Do not touch `V*.sql`.

## Part A — Change password (`/account/password`, any logged-in user, ADMIN and CASHIER)
1. `GET /account/password` shows the form; `POST /account/password` changes the password.
2. Form fields: current password, new password, confirm new password. Use `@Valid` + `BindingResult`.
3. Rules, enforced in `AccountService` (server side, not only in the form):
   - current password must match the stored hash (`PasswordEncoder.matches`), else a field error "Current password is incorrect."
   - new password at least 8 characters and at most 72 (BCrypt limit), else a field error.
   - new password must differ from the current one.
   - new and confirm must match.
4. Store the new hash with `PasswordEncoder.encode`. Never log or flash any password value.
5. On success: redirect to `/` with a success toast "Password changed." Keep the user logged in.
6. The user changed is always the logged-in user (from `@AuthenticationPrincipal`), never an id from the request.
7. Add one link "Change password" in the navbar/user menu for both roles. Cashiers must be able to open the page (`/account/**` only needs authentication; confirm `SecurityConfig` does not block it).

## Part B — Login throttling
1. New `LoginAttemptService` (in-memory is fine for a single-instance shop), using the existing `Clock` bean. Key = submitted username, lower-cased and trimmed, whether or not the user exists (so it never reveals which usernames exist).
2. After `app.security.max-login-attempts` (default 5) consecutive failures for a key, lock that key for `app.security.lock-minutes` (default 10). A successful login resets the counter. After the lock time passes, the key unlocks.
3. Wire it into Spring Security with the least code: a listener for `AuthenticationFailureBadCredentialsEvent` (count) and `AuthenticationSuccessEvent` (reset), and make a locked key fail authentication BEFORE the password is checked (for example `isAccountNonLocked()` returning false in the `UserDetails` returned by `AppUserDetailsService`, or an equivalent that results in a `LockedException`). While locked, even the correct password must fail.
4. The login page shows one generic message for bad credentials and for locked ("Invalid username or password, or too many attempts. Try again later."). Do not say which one happened.
5. Add the two properties to `application.yml` with those defaults.

## Tests to add
- `AccountService`: wrong current password rejected and hash unchanged; 7-char and 73-char new password rejected; same-as-current rejected; mismatch rejected; success changes the hash and the new password matches.
- MockMvc: a CASHIER can GET `/account/password` (200); anonymous is redirected to `/login`; POST with valid data redirects and the user can authenticate with the new password in the same test.
- `LoginAttemptService` with a controllable `Clock`: 4 failures not locked; 5th failure locks; still locked just before the lock time ends; unlocked after; success resets the count; `Admin` and `admin ` count as the same key; an unknown username locks the same way.
- Integration: 5 failed logins for `admin`, then the correct password still fails; no user data changed.

## Browser check
Use a temp or dev DB, never `data/` for destructive steps. Start with `./mvnw spring-boot:run`.
- Log in as `cashier1`, open Change password from the navbar, change it to `Newpass123`, log out, log in with the new password: works. Change it back.
- Enter a wrong current password: error shown, password unchanged.
- Log out. Enter a wrong password for `admin` 5 times: the 6th attempt with the CORRECT password still fails with the generic message. (Use a short lock time for the check: `--app.security.lock-minutes=1`, then confirm login works again after it passes.)

## Done when
`./mvnw -q clean verify` passes, and every item above has a test or a reported browser result. List anything you could not verify.

## Out of scope (report as follow-ups, do not do)
Forced password change on first login; account unlock screen; email reset; distributed (multi-instance) throttling.
