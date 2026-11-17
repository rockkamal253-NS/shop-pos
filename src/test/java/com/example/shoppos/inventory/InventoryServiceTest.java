package com.example.shoppos.inventory;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.NotFoundException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class InventoryServiceTest {

    @Autowired
    private InventoryService inventory;

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
    private User admin;

    @BeforeEach
    void setUp() {
        category = categories.save(new Category("Inv Cat " + SEQ.incrementAndGet()));
        admin = users.save(new User("inv_admin_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Inv Admin", Role.ADMIN));
    }

    private Product product(int stock) {
        int n = SEQ.incrementAndGet();
        return products.save(new Product("Inv Product " + n, "ISKU-" + n, "IBC-" + n,
                category, new BigDecimal("5.00"), new BigDecimal("8.00"), stock, 5, true));
    }

    private StockAdjustForm form(Product p, MovementType type, StockAdjustForm.Direction dir, int qty, String note) {
        StockAdjustForm f = new StockAdjustForm();
        f.setProductId(p.getId());
        f.setType(type);
        f.setDirection(dir);
        f.setQuantity(qty);
        f.setNote(note);
        return f;
    }

    @Test
    void receiveStockIncreasesQuantityAndRecordsMovement() {
        Product p = product(20);
        Product result = inventory.adjust(form(p, MovementType.RECEIVE, null, 15, "Weekly delivery"), admin);

        assertEquals(35, result.getStockQuantity());
        var history = movements.findByProductIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged()).getContent();
        assertEquals(1, history.size());
        assertEquals(MovementType.RECEIVE, history.get(0).getType());
        assertEquals(15, history.get(0).getQuantityDelta());
        assertEquals(35, history.get(0).getStockAfter());
        assertEquals("Weekly delivery", history.get(0).getNote());
        assertEquals(admin.getId(), history.get(0).getUser().getId());
    }

    @Test
    void damageStockDecreasesQuantity() {
        Product p = product(20);
        Product result = inventory.adjust(form(p, MovementType.DAMAGE, null, 4, "Crushed boxes"), admin);
        assertEquals(16, result.getStockQuantity());
    }

    @Test
    void correctionCanIncreaseAndDecrease() {
        Product p = product(10);
        inventory.adjust(form(p, MovementType.CORRECTION, StockAdjustForm.Direction.INCREASE, 3, "Recount"), admin);
        assertEquals(13, products.findById(p.getId()).orElseThrow().getStockQuantity());

        Product p2 = product(10);
        inventory.adjust(form(p2, MovementType.CORRECTION, StockAdjustForm.Direction.DECREASE, 6, "Shrinkage"), admin);
        assertEquals(4, products.findById(p2.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void adjustmentBelowZeroIsRejected() {
        Product p = product(3);
        assertThrows(BusinessRuleException.class,
                () -> inventory.adjust(form(p, MovementType.DAMAGE, null, 4, "Too many"), admin));
        assertEquals(3, products.findById(p.getId()).orElseThrow().getStockQuantity());
        assertTrue(movements.findByProductIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged()).isEmpty());
    }

    @Test
    void adjustmentWithUnknownProductFails() {
        StockAdjustForm f = form(product(1), MovementType.RECEIVE, null, 1, "x");
        f.setProductId(999_999L);
        assertThrows(NotFoundException.class, () -> inventory.adjust(f, admin));
    }
}
