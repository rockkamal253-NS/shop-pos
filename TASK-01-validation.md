# TASK-01 — Make form validation actually run

## Problem
No controller uses `@Valid`, so every Bean Validation annotation on the forms is ignored.
`binding.hasErrors()` only sees type-conversion errors. Results:
- Stock adjust accepts negative quantity (DAMAGE with -5 ADDS stock), blank reason, null type/quantity (NPE).
- Product create/update: missing name → NPE (`ProductService.create`, `form.getName().trim()`);
  price with >2 decimals → `ArithmeticException` (`Product` calls `setScale(2)` with no rounding mode);
  negative values hit DB CHECK constraints and show a misleading toast.

## Files in scope
- `src/main/java/com/example/shoppos/product/ProductController.java`
- `src/main/java/com/example/shoppos/product/CategoryController.java`
- `src/main/java/com/example/shoppos/inventory/InventoryController.java`
- `src/main/java/com/example/shoppos/inventory/InventoryService.java`
- `src/main/java/com/example/shoppos/product/Product.java`
- `src/test/java/...` (new test classes only)

## Steps
1. Add `@Valid` (jakarta.validation.Valid) in front of every `@ModelAttribute("form") ...Form form` parameter in the three controllers. `BindingResult` must stay immediately after the form parameter.
2. In `InventoryController.adjust`, the `binding.hasErrors()` branch must re-add `products` and `selected` to the model (it already does; keep it and make sure `form.getProductId()` null is handled).
3. In `Product.java`, change the three `setScale(2)` calls (constructor and the two setters) to `setScale(2, RoundingMode.HALF_UP)`.
4. In `InventoryService.adjust`, add a service-level guard at the top: if `form.getQuantity() == null || form.getQuantity() < 1` throw `BusinessRuleException("Quantity must be at least 1.")`. Also throw `BusinessRuleException` if `form.getType()` is null. Do not rely on the controller alone.
5. In `ProductService.create`/`update`, no change needed once `@Valid` is in place, but make sure `categoryId` null cannot reach `categories.findById`.

## Tests to add
- `InventoryServiceTest`: quantity -5 with DAMAGE throws `BusinessRuleException` and stock is unchanged; quantity 0 and null throw.
- New MockMvc test (needs `spring-security-test`: **ask me before adding the dependency**, otherwise use `MockMvcBuilders.standaloneSetup` and set the principal manually):
  - POST `/inventory/adjust` with blank note → returns the `inventory/adjust` view with a field error, stock unchanged.
  - POST `/products` with blank name → returns `product/form` view, no product created.
- `ProductServiceTest`: sell price `1.234` does not throw `ArithmeticException` (rounds to 1.23).

## Done when
- A negative or blank adjust never changes stock or writes a movement.
- A product form with errors re-renders with messages instead of a 500 page.
