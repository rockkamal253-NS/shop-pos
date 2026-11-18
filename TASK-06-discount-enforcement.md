# TASK-06 — Close the discount-cap bypass and clean up review findings

Branch: review-hardening (continue on it). Do ONLY this task.

## Problems (found in code review of the pushed branch)
A. The cashier discount cap (`app.max-cashier-discount-percent`) only checks PERCENT discounts in `CartService.setDiscount`.
   A fixed-AMOUNT discount is only limited to the cart subtotal (`SalePricing`: `clamp(value).min(subtotal)`),
   so a cashier can type an amount equal to the whole subtotal and get 100% off, bypassing the cap.
B. The cap also applies to ADMIN users. In prod (limit 20) the owner cannot give more than 20% either.
C. `application-prod.yml` has an unrequested change: `driver-class-name: ${PROD_DB_DRIVER:org.postgresql.Driver}`.
   It was added only to make a prod-profile browser check run on H2. It must not stay in prod config.
D. `AdminSeedRunner` throws "APP_ADMIN_PASSWORD must be set" even when the prod password is set but equals `admin123`. Misleading.

## Files in scope
- `src/main/java/com/example/shoppos/sale/SaleService.java`
- `src/main/java/com/example/shoppos/sale/CartService.java`
- `src/main/java/com/example/shoppos/sale/PosController.java`
- `src/main/java/com/example/shoppos/user/AdminSeedRunner.java`
- `src/main/resources/application-prod.yml`
- tests under `src/test/java/com/example/shoppos/sale/` and `.../user/`

## Steps
1. **Enforce the cap where the subtotal is known.** In `SaleService.completeSale`, after the totals are computed:
   if the cashier's role is NOT `ADMIN` and `totals.discountAmount()` > `totals.subtotal() * maxPercent / 100`
   (scale 2, HALF_UP), throw `BusinessRuleException("Discounts above " + maxPercent + "% need an admin.")` BEFORE anything is written.
   Inject the limit with `@Value("${app.max-cashier-discount-percent:20}") int`. Keep any existing constructor that tests use
   (add an overload with default 20) or update the tests that construct `SaleService`.
2. **Exempt admins at cart time.** Change `CartService.setDiscount` to take a `boolean admin` parameter (or the role).
   The 20% PERCENT cap applies only when `admin` is false. The hard 100% maximum, negative check and MAX_AMOUNT check stay for everyone.
   Update `PosController` to pass `principal.getRole() == Role.ADMIN`. Update the existing CartService tests accordingly.
3. **Revert C.** In `application-prod.yml` set `driver-class-name: org.postgresql.Driver` again.
   If you still need a prod-profile startup check on H2, override on the command line instead:
   `--spring.datasource.url=jdbc:h2:file:<temp path outside the repo> --spring.datasource.driver-class-name=org.h2.Driver`
4. **Fix D.** If prod and password equals `admin123`: throw `IllegalStateException("APP_ADMIN_PASSWORD must not be the default value")`.
   Keep "APP_ADMIN_PASSWORD must be set" for the blank case. Update `AdminSeedRunnerTest`.

## Tests to add
- `SaleServiceTest`:
  - cashier, subtotal 100, AMOUNT discount 100, limit 20 → `BusinessRuleException`, no sale and no stock change.
  - cashier, subtotal 100, AMOUNT discount 20 → accepted. AMOUNT discount 20.01 → rejected.
  - admin, same 100 discount → accepted.
- `CartServiceTest`: PERCENT 50 rejected when `admin=false`, accepted when `admin=true`; PERCENT 101 rejected for both.
- `AdminSeedRunnerTest`: prod + `admin123` message differs from the blank-password message.

## Browser check
Start the app with the limit overridden: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.max-cashier-discount-percent=20`
- Log in as `cashier1`, put one product (price 10.00) in the cart, choose a fixed-amount discount of 10.00, click Complete sale: rejected with "Discounts above 20% need an admin." and the cart is untouched.
- Same cart, fixed-amount discount 2.00: sale completes.
- Log in as `admin`, 50% discount on a new cart: accepted.

## Done when
`./mvnw -q clean verify` passes and `git diff origin/main -- src/main/resources/application-prod.yml` shows no `PROD_DB_DRIVER`.
