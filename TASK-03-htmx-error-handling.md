# TASK-03 — Errors must not break the POS screen

## Problem
- `GlobalExceptionHandler.handleUnexpected` returns view `error/500` with no status → HTTP 200.
  htmx 2.0.4 swaps 2xx responses and ignores 4xx/5xx, so the full error page is injected into `#cart-panel`.
- `handleOptimisticLock` redirects to the Referer (`/pos`). htmx follows it and injects the whole POS page into `#cart-panel`.
- `PosController.checkout` only catches `InsufficientStockException` and `BusinessRuleException`.
  Two cashiers selling the last units of one product at once triggers `OptimisticLockingFailureException`.
- `DataIntegrityViolationException` shows "This record is referenced by other data…" for ANY constraint failure.

## Files in scope
- `src/main/java/com/example/shoppos/common/web/GlobalExceptionHandler.java`
- `src/main/java/com/example/shoppos/sale/PosController.java`
- `src/main/resources/static/js/app.js`
- new tests under `src/test/java/com/example/shoppos/sale/` and `.../common/web/`

## Steps
1. `PosController.checkout`: add `catch (OptimisticLockingFailureException e)` → keep the cart, set `checkoutError = "Stock just changed. Please review the cart and try again."`, send an error toast, return `pos/fragments :: cartInner`.
2. `GlobalExceptionHandler.handleUnexpected`: add `@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)`.
3. In all three handlers (`handleDataIntegrity`, `handleBusinessRule`, `handleOptimisticLock`), if the request has header `HX-Request: true`, do NOT redirect. Instead respond with status 200 and header `HX-Reswap: none` plus an `HX-Trigger` toast (reuse `Toasts.send`). Return an empty body (use `ResponseEntity<Void>`; change the method signatures as needed).
   Non-HTMX requests keep the current redirect-with-flash behavior.
4. `app.js`: add a listener for `htmx:responseError` that calls `showToast('Something went wrong. Please try again.', 'error')`.
5. `Toasts.send`: escape non-ASCII characters as `\uXXXX` and escape control characters (`\n`, `\r`, `\t`) in the JSON, so non-Latin product names (e.g. Arabic) survive the HTTP header.

## Tests to add
- Unit test for `Toasts.send` using `MockHttpServletResponse`: an Arabic message produces a header containing only ASCII characters; quotes and newlines are escaped.
- Test that an unexpected exception handler returns status 500 (use `@ResponseStatus` reflection check or MockMvc standalone with the advice).
- Test `checkout` with a mocked `SaleService` throwing `ObjectOptimisticLockingFailureException`: response renders `cartInner`, cart is NOT cleared.

## Done when
- A forced error during an htmx request shows a toast and leaves the cart untouched; no full page appears inside the cart panel.

## Browser check
- Open /pos in two tabs, same product with stock 1 in both carts. Complete the sale in tab A, then in tab B: a toast appears and the cart stays; no full page appears inside the cart panel.
- Add a product with an Arabic name to the cart: the toast shows the full name.
