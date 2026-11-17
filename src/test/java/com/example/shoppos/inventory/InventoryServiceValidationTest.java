package com.example.shoppos.inventory;

import com.example.shoppos.common.web.BusinessRuleException;
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
class InventoryServiceValidationTest {

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
        category = categories.save(new Category("Inv Val Cat " + SEQ.incrementAndGet()));
        admin = users.save(new User("inv_val_admin_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Inv Val Admin", Role.ADMIN));
    }

    private Product product(int stock) {
        int n = SEQ.incrementAndGet();
        return products.save(new Product("Inv Val Product " + n, "IVSKU-" + n, "IVBC-" + n,
                category, new BigDecimal("5.00"), new BigDecimal("8.00"), stock, 5, true));
    }

    private StockAdjustForm form(Product p, MovementType type, StockAdjustForm.Direction dir, Integer qty, String note) {
        StockAdjustForm f = new StockAdjustForm();
        f.setProductId(p.getId());
        f.setType(type);
        f.setDirection(dir);
        f.setQuantity(qty);
        f.setNote(note);
        return f;
    }

    @Test
    void negativeQuantityWithDamageThrowsAndLeavesStockUnchanged() {
        Product p = product(20);
        StockAdjustForm f = form(p, MovementType.DAMAGE, null, -5, "Damaged items");
        assertThrows(BusinessRuleException.class, () -> inventory.adjust(f, admin));
        assertEquals(20, products.findById(p.getId()).orElseThrow().getStockQuantity());
        assertTrue(movements.findByProductIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged()).isEmpty());
    }

    @Test
    void zeroAndNullQuantityThrow() {
        Product p = product(20);
        StockAdjustForm fZero = form(p, MovementType.DAMAGE, null, 0, "Zero qty");
        assertThrows(BusinessRuleException.class, () -> inventory.adjust(fZero, admin));

        StockAdjustForm fNull = form(p, MovementType.DAMAGE, null, null, "Null qty");
        assertThrows(BusinessRuleException.class, () -> inventory.adjust(fNull, admin));
    }
}
