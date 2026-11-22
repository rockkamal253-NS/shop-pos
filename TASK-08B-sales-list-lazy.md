TASK RULES (TASK-08B, Turbo mode: every command runs the moment you write it)
- Allowed commands only: git switch/pull/status/add/commit/push as written in the steps, ./mvnw -q clean verify, and read-only commands (ls, cat, grep).
- Forbidden: rm, git reset/clean/rebase/stash, branch deletion, any push other than the one in step 5, editing application*.yml, pom.xml, templates, or any file outside the scope list.
- If a command fails twice, or git status shows anything outside scope, STOP and report. Do not work around it.
- Do not start the app, and do not touch data/. This task needs no browser check.
- The new test must be seen failing before the fix and passing after. Report both results.



Project: shop-pos. Read RULES.md, DB-RULES.md, WORKING-PRINCIPLES.md and AWAY-MODE.md first. I am away. AWAY-MODE overrides RULES.md for the push in step 5 only.
TASK-08B: GET /sales returns HTTP 500 (LazyInitializationException on Sale.items, read by s.itemCount in templates/sale/list.html) whenever at least one sale exists, because open-in-view is false.
1. git switch main && git pull origin main && git switch -c sales-list-fix
2. Save this task text as TASK-08B-sales-list-lazy.md in the repo root (in scope).
3. Scope: SaleService.java, one test class under src/test/java/com/example/shoppos/sale/, TASK-08B-sales-list-lazy.md. Nothing else.
   - In SaleService.search (already @Transactional(readOnly = true)): after loading the page, initialize each sale's items inside the transaction (for example org.hibernate.Hibernate.initialize(sale.getItems()) for each sale on the page). Do NOT add a collection fetch to the repository query and do NOT combine it with Pageable.
   - Test: save a sale with 2 items, then call the service OUTSIDE any test transaction (use @Transactional(propagation = NOT_SUPPORTED) on the test or a MockMvc GET /sales as ADMIN) and assert items are readable / status 200 and the item count is shown. The test must FAIL on current main.
4. ./mvnw -q clean verify
5. git status shows only in-scope files. Then: git add -A && git commit -m "TASK-08B: load sale items inside the service transaction for the sales list" && git push origin sales-list-fix
Never push to main. No force push. Report 8 lines max: build + test count, proof the new test fails without the fix, files changed, anything not verified.
