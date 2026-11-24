package com.example.shoppos.sale;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.inventory.StockMovement;
import com.example.shoppos.inventory.StockMovementRepository;
import com.example.shoppos.product.Category;
import com.example.shoppos.product.CategoryRepository;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core business rules of the register: stock checks, totals, change calculation,
 * stock decrement + movement records, and voiding. Runs against H2 (test profile)
 * with Flyway migrations; every test rolls back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class SaleServiceTest {

    @Autowired
    private SaleService sales;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private StockMovementRepository movements;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Category category;
    private User cashier;

    @BeforeEach
    void setUp() {
        category = categories.save(new Category("Test Cat " + SEQ.incrementAndGet()));
        cashier = users.save(new User("cashier_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Test Cashier", Role.CASHIER));
    }

    private Product product(String name, String sell, int stock) {
        int n = SEQ.incrementAndGet();
        return products.save(new Product(name, "SKU-" + n, "BC-" + n, category,
                new BigDecimal("1.00"), new BigDecimal(sell), stock, 5, true));
    }

    private Cart cartOf(Product p, int qty) {
        Cart cart = new Cart();
        cart.getItems().put(p.getId(), qty);
        cart.setCashTendered(new BigDecimal("9999.00")); // plenty of cash unless a test overrides it
        return cart;
    }

    @Test
    void completingSaleDecrementsStockAndWritesMovements() {
        Product p = product("Green Tea", "10.00", 10);

        Sale sale = sales.completeSale(cartOf(p, 3), cashier);

        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
        assertEquals("30.00", sale.getTotal().toPlainString());
        assertEquals(7, products.findById(p.getId()).orElseThrow().getStockQuantity());

        List<StockMovement> history = movements.findByProductIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged()).getContent();
        assertEquals(1, history.size());
        assertEquals(MovementType.SALE, history.get(0).getType());
        assertEquals(-3, history.get(0).getQuantityDelta());
        assertEquals(7, history.get(0).getStockAfter());
        assertTrue(history.get(0).getReference().startsWith("SALE #"));
    }

    @Test
    void saleSnapshotsNameAndPriceAtTimeOfSale() {
        Product p = product("Orange Soda", "12.50", 5);
        Sale sale = sales.completeSale(cartOf(p, 2), cashier);

        assertEquals(1, sale.getItems().size());
        SaleItem item = sale.getItems().get(0);
        assertEquals("Orange Soda", item.getProductName());
        assertEquals("12.50", item.getUnitPrice().toPlainString());
        assertEquals("25.00", item.getLineTotal().toPlainString());

        // Rename the product afterwards — the snapshot must not change.
        p.setName("Renamed Soda");
        products.saveAndFlush(p);
        Sale reloaded = sales.get(sale.getId());
        assertEquals("Orange Soda", reloaded.getItems().get(0).getProductName());
    }

    @Test
    void saleRejectedWhenInsufficientStock() {
        Product p = product("Chewing Gum", "3.00", 2);

        InsufficientStockException e = assertThrows(InsufficientStockException.class,
                () -> sales.completeSale(cartOf(p, 5), cashier));
        assertTrue(e.getIssues().get(0).contains("requested 5"));

        // Nothing was written: stock unchanged, no movement.
        assertEquals(2, products.findById(p.getId()).orElseThrow().getStockQuantity());
        assertTrue(movements.findByProductIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged()).isEmpty());
    }

    @Test
    void saleRejectedWhenCartEmpty() {
        assertThrows(BusinessRuleException.class, () -> sales.completeSale(new Cart(), cashier));
    }

    @Test
    void cashSaleCalculatesChangeDue() {
        Product p = product("Bread", "23.75", 10);
        Cart cart = cartOf(p, 2);
        cart.setPaymentMethod(PaymentMethod.CASH);
        cart.setCashTendered(new BigDecimal("60.00"));

        Sale sale = sales.completeSale(cart, cashier);
        assertEquals("47.50", sale.getTotal().toPlainString());
        assertEquals("60.00", sale.getCashTendered().toPlainString());
        assertEquals("12.50", sale.getChangeDue().toPlainString());
    }

    @Test
    void cashSaleRejectedWhenTenderedBelowTotal() {
        Product p = product("Bread", "23.75", 10);
        Cart cart = cartOf(p, 2);
        cart.setPaymentMethod(PaymentMethod.CASH);
        cart.setCashTendered(new BigDecimal("40.00"));

        assertThrows(BusinessRuleException.class, () -> sales.completeSale(cart, cashier));
        assertEquals(10, products.findById(p.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void cardSaleHasNoCashFields() {
        Product p = product("Battery", "18.00", 10);
        Cart cart = cartOf(p, 1);
        cart.setPaymentMethod(PaymentMethod.CARD);

        Sale sale = sales.completeSale(cart, cashier);
        assertEquals("18.00", sale.getTotal().toPlainString());
        assertNull(sale.getCashTendered());
        assertNull(sale.getChangeDue());
    }

    @Test
    void amountDiscountIsApplied() {
        Product p = product("Coffee Pack", "55.00", 10);
        Cart cart = cartOf(p, 2);
        cart.setDiscountType(DiscountType.AMOUNT);
        cart.setDiscountValue(new BigDecimal("5.00"));

        Sale sale = sales.completeSale(cart, cashier);
        assertEquals("110.00", sale.getSubtotal().toPlainString());
        assertEquals("5.00", sale.getDiscountAmount().toPlainString());
        assertEquals("105.00", sale.getTotal().toPlainString());
    }

    @Test
    void percentDiscountIsApplied() {
        Product p = product("Cooking Pot", "80.00", 10);
        Cart cart = cartOf(p, 1);
        cart.setDiscountType(DiscountType.PERCENT);
        cart.setDiscountValue(new BigDecimal("10"));

        Sale sale = sales.completeSale(cart, cashier);
        assertEquals("8.00", sale.getDiscountAmount().toPlainString());
        assertEquals("72.00", sale.getTotal().toPlainString());
    }

    @Test
    void voidSaleRestoresStockAndWritesMovements() {
        Product p = product("Instant Noodles", "7.50", 12);
        Sale sale = sales.completeSale(cartOf(p, 4), cashier);
        assertEquals(8, products.findById(p.getId()).orElseThrow().getStockQuantity());

        User admin = users.save(new User("admin_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Test Admin", Role.ADMIN));
        sales.voidSale(sale.getId(), admin);

        Sale voided = sales.get(sale.getId());
        assertEquals(SaleStatus.VOIDED, voided.getStatus());
        assertEquals(admin.getId(), voided.getVoidedBy().getId());
        assertEquals(12, products.findById(p.getId()).orElseThrow().getStockQuantity());

        List<StockMovement> history = movements.findByProductIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged()).getContent();
        assertEquals(2, history.size());
        assertEquals(MovementType.VOID_RESTORE, history.get(0).getType());
        assertEquals(4, history.get(0).getQuantityDelta());
        assertEquals(12, history.get(0).getStockAfter());
    }

    @Test
    void voidSaleIsRejectedTwice() {
        Product p = product("Soap", "9.00", 10);
        Sale sale = sales.completeSale(cartOf(p, 1), cashier);
        User admin = users.save(new User("admin2_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Admin Two", Role.ADMIN));

        sales.voidSale(sale.getId(), admin);
        assertThrows(BusinessRuleException.class, () -> sales.voidSale(sale.getId(), admin));
    }

    @Test
    void multiLineSaleTotalsEveryLine() {
        Product a = product("Item A", "10.00", 10);
        Product b = product("Item B", "2.50", 10);
        Cart cart = new Cart();
        cart.getItems().put(a.getId(), 2);
        cart.getItems().put(b.getId(), 4);
        cart.setCashTendered(new BigDecimal("9999.00"));

        Sale sale = sales.completeSale(cart, cashier);
        assertEquals("30.00", sale.getSubtotal().toPlainString());
        assertEquals(2, sale.getItems().size());
        assertEquals(6, sale.getItemCount());
        assertEquals(8, products.findById(a.getId()).orElseThrow().getStockQuantity());
        assertEquals(6, products.findById(b.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void cartContainingZeroQuantityIsRejectedAndNothingWritten() {
        Product p = product("Zero Qty Item", "10.00", 10);
        Cart cart = new Cart();
        cart.getItems().put(p.getId(), 0);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> sales.completeSale(cart, cashier));
        assertEquals("Invalid quantity in cart.", ex.getMessage());
        assertEquals(0, sales.search(null, null, null, 0).getTotalElements());
        assertEquals(10, products.findById(p.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void voidSaleByCashierThrowsAccessDenied() {
        Product p = product("Item Protected", "10.00", 10);
        Sale sale = sales.completeSale(cartOf(p, 1), cashier);
        assertThrows(AccessDeniedException.class, () -> sales.voidSale(sale.getId(), cashier));
    }

    // ── TASK-06 discount-cap tests ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierAmountDiscountAtLimitOf100IsRejected() {
        Product p = product("Expensive Item", "100.00", 10);
        Cart cart = cartOf(p, 1); // subtotal = 100.00
        cart.setDiscountType(DiscountType.AMOUNT);
        cart.setDiscountValue(new BigDecimal("100.00")); // 100% off, exceeds 20% cap
        assertThrows(BusinessRuleException.class, () -> sales.completeSale(cart, cashier));
        assertEquals(10, products.findById(p.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierAmountDiscountAt20AcceptedAt20_01Rejected() {
        Product p1 = product("Item For 20 Test", "100.00", 10);
        Cart cart1 = cartOf(p1, 1);
        cart1.setDiscountType(DiscountType.AMOUNT);
        cart1.setDiscountValue(new BigDecimal("20.00")); // exactly 20% → accepted
        Sale sale = sales.completeSale(cart1, cashier);
        assertEquals("80.00", sale.getTotal().toPlainString());

        Product p2 = product("Item For 20.01 Test", "100.00", 10);
        Cart cart2 = cartOf(p2, 1);
        cart2.setDiscountType(DiscountType.AMOUNT);
        cart2.setDiscountValue(new BigDecimal("20.01")); // just over → rejected
        assertThrows(BusinessRuleException.class, () -> sales.completeSale(cart2, cashier));
        assertEquals(10, products.findById(p2.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void adminAmountDiscount100IsAccepted() {
        // class-level @WithMockUser(roles="ADMIN") applies; use an admin User entity
        User admin = users.save(new User("admin_disc_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Test Admin", Role.ADMIN));
        Product p = product("Admin Discount Item", "100.00", 10);
        Cart cart = cartOf(p, 1);
        cart.setDiscountType(DiscountType.AMOUNT);
        cart.setDiscountValue(new BigDecimal("100.00")); // 100% off → admin can do this
        Sale sale = sales.completeSale(cart, admin);
        assertEquals("0.00", sale.getTotal().toPlainString());
    }

    // ── end TASK-06 discount-cap tests ─────────────────────────────────────

    @Test
    void staleSaleOptimisticLockThrowsException() {
        Product p = product("Optimistic Item", "10.00", 10);
        Sale sale = sales.completeSale(cartOf(p, 1), cashier);
        assertEquals(0L, sale.getVersion());

        // Evict/detach the sale from the persistence context to keep version 0
        entityManager.detach(sale);

        User admin = users.save(new User("admin_opt_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Test Admin", Role.ADMIN));
        sales.voidSale(sale.getId(), admin);

        Sale updated = sales.get(sale.getId());
        assertEquals(1L, updated.getVersion());

        // Detached 'sale' has version 0 in memory; attempting to save/merge it causes optimistic lock failure
        sale.setStatus(SaleStatus.COMPLETED);
        assertThrows(OptimisticLockingFailureException.class, () -> {
            saleRepository.saveAndFlush(sale);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void searchInitializesItemsOutsideTransaction() {
        Product a = product("Lazy Item A", "10.00", 10);
        Product b = product("Lazy Item B", "5.00", 10);
        Cart cart = new Cart();
        cart.getItems().put(a.getId(), 2);
        cart.getItems().put(b.getId(), 3);
        cart.setCashTendered(new BigDecimal("9999.00"));
        Sale sale = sales.completeSale(cart, cashier);

        try {
            Page<Sale> page = sales.search(null, null, null, 0);
            Sale found = page.getContent().stream()
                    .filter(s -> s.getId().equals(sale.getId()))
                    .findFirst().orElseThrow();

            assertEquals(2, found.getItems().size());
            assertEquals(5, found.getItemCount());
        } finally {
            saleRepository.deleteAll();
            movements.deleteAll();
            products.deleteById(a.getId());
            products.deleteById(b.getId());
        }
    }

    @Test
    void completeSaleAndVoidSaleProcessItemsInAscendingProductIdOrder() {
        Product p1 = product("Product 1", "10.00", 20);
        Product p2 = product("Product 2", "20.00", 20);
        Product p3 = product("Product 3", "30.00", 20);
        assertTrue(p1.getId() < p2.getId());
        assertTrue(p2.getId() < p3.getId());

        Cart cart = new Cart();
        cart.getItems().put(p3.getId(), 3);
        cart.getItems().put(p2.getId(), 2);
        cart.getItems().put(p1.getId(), 1);
        cart.setPaymentMethod(PaymentMethod.CASH);
        cart.setCashTendered(new BigDecimal("9999.00"));

        Sale sale = sales.completeSale(cart, cashier);

        assertEquals(19, products.findById(p1.getId()).orElseThrow().getStockQuantity());
        assertEquals(18, products.findById(p2.getId()).orElseThrow().getStockQuantity());
        assertEquals(17, products.findById(p3.getId()).orElseThrow().getStockQuantity());

        List<StockMovement> saleMovements = movements.findAll().stream()
                .filter(m -> m.getReference().equals("SALE #" + sale.getId()))
                .sorted(Comparator.comparing(StockMovement::getId))
                .toList();
        assertEquals(3, saleMovements.size());
        assertEquals(p1.getId(), saleMovements.get(0).getProduct().getId());
        assertEquals(p2.getId(), saleMovements.get(1).getProduct().getId());
        assertEquals(p3.getId(), saleMovements.get(2).getProduct().getId());

        long nonExistentId = 999999L;
        Cart invalidCart = new Cart();
        invalidCart.getItems().put(p1.getId(), 1);
        invalidCart.getItems().put(nonExistentId, 1);
        invalidCart.setCashTendered(new BigDecimal("9999.00"));
        long saleCountBefore = saleRepository.count();
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> sales.completeSale(invalidCart, cashier));
        assertEquals("A product in the cart no longer exists.", ex.getMessage());
        assertEquals(saleCountBefore, saleRepository.count());

        User admin = users.save(new User("admin_void_order_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Test Admin", Role.ADMIN));
        sales.voidSale(sale.getId(), admin);

        assertEquals(20, products.findById(p1.getId()).orElseThrow().getStockQuantity());
        assertEquals(20, products.findById(p2.getId()).orElseThrow().getStockQuantity());
        assertEquals(20, products.findById(p3.getId()).orElseThrow().getStockQuantity());

        List<StockMovement> voidMovements = movements.findAll().stream()
                .filter(m -> m.getReference().equals("VOID #" + sale.getId()))
                .sorted(Comparator.comparing(StockMovement::getId))
                .toList();
        assertEquals(3, voidMovements.size());
        assertEquals(p1.getId(), voidMovements.get(0).getProduct().getId());
        assertEquals(p2.getId(), voidMovements.get(1).getProduct().getId());
        assertEquals(p3.getId(), voidMovements.get(2).getProduct().getId());
    }
}
