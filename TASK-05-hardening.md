# TASK-05 — Hardening (lower priority)

Do this only after TASK-01 to TASK-04 are committed and green.

## Files in scope
- `src/main/resources/templates/product/list.html`
- `src/main/java/com/example/shoppos/sale/SaleController.java`
- `src/main/java/com/example/shoppos/sale/Sale.java`
- `src/main/java/com/example/shoppos/sale/SaleRepository.java`
- `src/main/java/com/example/shoppos/sale/SaleService.java`
- `src/main/java/com/example/shoppos/common/config/SecurityConfig.java`
- `src/main/resources/db/migration/V2__sale_version.sql` (new)
- `src/main/resources/application.yml`
- tests

## Steps
1. **Hide cost from cashiers:** in `product/list.html`, wrap the "Cost" `<th>` (line ~29) and its `<td>` (line ~44) in `th:if="${isAdmin}"`.
2. **Receipt ownership:** in `SaleController.receipt`, if the current user is not ADMIN and `sale.getCashier().getId()` differs from the current user id, throw `NotFoundException`. Use `@AuthenticationPrincipal UserPrincipal`.
3. **Optimistic lock on Sale:** add `@Version private Long version;` to `Sale`, plus `V2__sale_version.sql`:
   `ALTER TABLE sales ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` (portable H2/PostgreSQL).
4. **Role check in the service:** add `@EnableMethodSecurity` to `SecurityConfig` and `@PreAuthorize("hasRole('ADMIN')")` on `SaleService.voidSale`. Make sure `SaleServiceTest` still passes (tests call the service without a security context: use a test-only approach, e.g. `@WithMockUser(roles="ADMIN")` if `spring-security-test` is available, otherwise ask me before adding it).
5. **Sales list pagination:** the `@EntityGraph(attributePaths = {"cashier", "items"})` on `SaleRepository.search` with `Pageable` paginates in memory. Remove `"items"` from that entity graph and make the list page not need the full items (use `@BatchSize(size = 50)` on `Sale.items`, or a count query). Confirm the Hibernate warning `HHH90003004` no longer appears in the test log.
6. **Timezone:** add `spring.jackson`-independent fix: declare a `Clock` bean (`Clock.system(ZoneId.of("Africa/Cairo"))`, zone from property `app.timezone`, default `Africa/Cairo`) and use it in `DashboardService` instead of `LocalDate.now()`. Do not change `@PrePersist` timestamps in this task.

## Tests to add
- Cashier requesting someone else's receipt → 404; admin → 200.
- Concurrent/duplicate `voidSale` on a stale `Sale` raises an optimistic-lock exception.
- `DashboardServiceTest`: uses a fixed `Clock`.

## Done when
`./mvnw -q clean verify` is green and `V2__sale_version.sql` applies on a fresh H2 database.

## Browser check
- Log in as cashier1: the product list has no Cost column.
- cashier1 opens a receipt of a sale made by admin: 404.
- The dashboard "today" total matches sales made just now.
