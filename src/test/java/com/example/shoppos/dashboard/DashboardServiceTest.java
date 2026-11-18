package com.example.shoppos.dashboard;

import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.sale.Cart;
import com.example.shoppos.sale.Sale;
import com.example.shoppos.sale.SaleItemRepository;
import com.example.shoppos.sale.SaleRepository;
import com.example.shoppos.sale.SaleService;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the dashboard queries, including the top-products aggregation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboard;

    @Autowired
    private SaleRepository sales;

    @Autowired
    private SaleItemRepository saleItems;

    @Autowired
    private ProductRepository products;

    @Autowired
    private UserRepository users;

    @Autowired
    private SaleService saleService;

    @Autowired
    private com.example.shoppos.product.CategoryRepository categories;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private User cashier;
    private com.example.shoppos.product.Category category;

    @BeforeEach
    void setUp() {
        cashier = users.save(new User("dash_cashier_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Dash Cashier", Role.CASHIER));
        category = categories.save(new com.example.shoppos.product.Category("Dash Cat " + SEQ.incrementAndGet()));
    }

    private Product product(String name, String sell, int stock, int threshold) {
        int n = SEQ.incrementAndGet();
        return products.save(new Product(name, "DSKU-" + n, "DBC-" + n, category,
                new BigDecimal("1.00"), new BigDecimal(sell), stock, threshold, true));
    }

    @Test
    void statsReflectTodaySalesTopProductsAndLowStock() {
        Product a = product("Dash Product A", "10.00", 30, 5);
        Product b = product("Dash Product B", "5.00", 3, 5); // low stock
        Product c = product("Dash Product C", "2.50", 0, 5); // out of stock

        Cart cart = new Cart();
        cart.getItems().put(a.getId(), 4); // 40.00
        cart.getItems().put(b.getId(), 2); // 10.00
        cart.setCashTendered(new BigDecimal("9999.00"));
        Sale sale = saleService.completeSale(cart, cashier);

        DashboardService.DashboardStats stats = dashboard.stats();

        assertEquals(1, stats.todayTransactions());
        assertEquals("50.00", stats.todayRevenue().toPlainString());
        assertTrue(stats.topProducts().size() >= 2);
        assertEquals("Dash Product A", stats.topProducts().get(0).getProductName());
        assertEquals(4, stats.topProducts().get(0).getQuantitySold());
        assertTrue(stats.lowStock().stream().anyMatch(p -> p.getId().equals(b.getId())));
        assertTrue(stats.lowStock().stream().anyMatch(p -> p.getId().equals(c.getId())));
    }

    @Test
    void emptyDashboardIsSafe() {
        DashboardService.DashboardStats stats = dashboard.stats();
        assertEquals("0", stats.todayRevenue().unscaledValue().toString());
        assertEquals(0, stats.todayTransactions());
    }

    @Test
    void statsUsesConfiguredClock() {
        Product a = product("Clock Product", "20.00", 10, 5);
        Cart cart = new Cart();
        cart.getItems().put(a.getId(), 1);
        cart.setCashTendered(new BigDecimal("100.00"));
        saleService.completeSale(cart, cashier);

        // Fixed clock in the future (3 days ahead)
        Clock futureClock = Clock.fixed(Instant.now().plus(3, ChronoUnit.DAYS), ZoneId.of("Africa/Cairo"));
        DashboardService serviceWithFutureClock = new DashboardService(sales, saleItems, products, futureClock);

        DashboardService.DashboardStats futureStats = serviceWithFutureClock.stats();
        // Today's sale should not appear on the dashboard when viewed through future clock
        assertEquals(0, futureStats.todayTransactions());
        assertEquals("0", futureStats.todayRevenue().unscaledValue().toString());

        // And standard dashboard (configured clock) sees today's sale
        DashboardService.DashboardStats currentStats = dashboard.stats();
        assertEquals(1, currentStats.todayTransactions());
        assertEquals("20.00", currentStats.todayRevenue().toPlainString());
    }
}
