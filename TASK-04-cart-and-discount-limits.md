# TASK-04 — Zero-quantity cart lines and unbounded discount/cash values

## Problems
A. `CartService.setQuantity`: if stock dropped to 0 while the item is in the cart and the cashier edits the quantity,
   `cap = min(requested, 0) = 0` is stored → cart line with quantity 0 → checkout violates `chk_item_quantity`
   and shows a misleading database error.
B. `setDiscount` and `setCashTendered` have no upper bound. Values ≥ 10^10 overflow `NUMERIC(12,2)` at insert.
C. Any cashier can apply a 100% discount (business decision — make the limit configurable).

## Files in scope
- `src/main/java/com/example/shoppos/sale/CartService.java`
- `src/main/java/com/example/shoppos/sale/PosController.java` (only if a message needs wiring)
- `src/main/resources/application.yml` (new property)
- `src/main/java/com/example/shoppos/sale/SaleService.java` (defensive check only)
- new/extended tests under `src/test/java/com/example/shoppos/sale/`

## Steps
1. `CartService.setQuantity`: if the product exists and `getStockQuantity() <= 0`, remove the line and return `"\"<name>\" is out of stock and was removed."`. Never store a quantity < 1.
2. `SaleService.completeSale`: before building the sale, throw `BusinessRuleException("Invalid quantity in cart.")` if any cart quantity is < 1 (defense in depth).
3. Add constant `MAX_AMOUNT = new BigDecimal("9999999.99")` in `CartService`. `setDiscount` (AMOUNT type) and `setCashTendered` throw `BusinessRuleException("Amount is too large.")` above it.
4. New property `app.max-cashier-discount-percent` (default `20`). In `CartService.setDiscount`, for PERCENT above the limit throw `BusinessRuleException("Discounts above 20% need an admin.")` (use the configured number in the message). Inject it with `@Value`. Admin-role handling is a follow-up: for now the limit applies to everyone, so make the property default to `100` in `application-dev.yml` to avoid changing dev behavior, and `20` in `application.yml`.
   - Fixed-AMOUNT discounts: cap at the cart subtotal as today; no new rule.

## Tests to add
- `CartService` test: add item (stock 3), set stock to 0, `setQuantity(..., 2)` → line removed, message returned, cart empty.
- `SaleServiceTest`: a cart containing quantity 0 is rejected with `BusinessRuleException`, nothing written.
- Discount over the limit rejected; at the limit accepted; amount 1e10 rejected for both discount and tendered cash.

## Done when
- No code path can put quantity < 1 into a cart or a sale.
- Oversized numbers produce a clear toast instead of a database error.

## Browser check
- Put a product in the cart, set its stock to 0 from the admin screen in another tab, then change its quantity in the cart: the line is removed with an "out of stock" message.
- Apply a 50% discount as cashier1: rejected with the limit message. Enter a cash amount of 99999999999: rejected with "Amount is too large."
