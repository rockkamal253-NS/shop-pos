# WORKING-PRINCIPLES — scope and testing (inspired by the Karpathy-style coding guidelines)

Applies to every task, in addition to RULES.md and DB-RULES.md.

## 1. Think before coding
- State your assumptions in one or two lines before editing. If something in the task is unclear or two readings are possible, STOP and ask. Do not pick silently.
- If the task file looks wrong or contradicts the code (a wrong file name, a conflicting setting), say so instead of working around it.

## 2. Simplicity first
- Do exactly what the task asks. No extra features, no new abstractions for one use, no handling for cases that cannot happen.
- No new dependencies unless the prompt or task file allows it by name.

## 3. Surgical changes
- Touch only the files in "Files in scope". If you believe another file must change, STOP and report why; do not edit it.
- Match the existing code style. Do not reformat or "improve" unrelated code.
- Mention unrelated bugs or dead code in your report. Do not fix or delete them.
- Never change production config (`application-prod.yml`, prod env settings) to make a test, build or browser check pass. Use command-line overrides.
- Never delete, skip, weaken or comment out an existing test to get a green build. If a test is wrong, say so and ask.

## 4. Goal-driven execution and testing
- Turn each step into something checkable. Every behavior change needs a test that FAILS without the change and passes with it.
- Test the server, not just the UI. HTML5 or JavaScript validation does not count as a test of server rules. Prove server rules with a unit/MockMvc test or by sending a request that bypasses the browser (curl).
- Test the dangerous inputs: zero, negative, null, blank, too large, wrong role, someone else's data, two users acting at once.
- For anything involving money, stock, roles or discounts, also test the way around the rule (a different input type, a different endpoint, a different role) before saying it is enforced.
- A browser check proves only what you saw on screen. Report the URL, the exact message or value you saw, and whether the data changed. Never write PASS without evidence.

## Report honesty
- In your final report, list every file you changed that is NOT in "Files in scope" and why. If there are none, say "none".
- List anything you could not verify (for example PostgreSQL, a role you did not log in as, a step you simulated) in plain words.
- Do not claim a test ran if it did not run.
