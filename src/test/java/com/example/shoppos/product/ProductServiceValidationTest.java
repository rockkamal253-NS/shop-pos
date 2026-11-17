package com.example.shoppos.product;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ProductServiceValidationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Category category;
    private User admin;

    @BeforeEach
    void setUp() {
        category = categories.save(new Category("Prod Val Cat " + SEQ.incrementAndGet()));
        admin = users.save(new User("prod_val_admin_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Prod Val Admin", Role.ADMIN));
    }

    private ProductForm form(String name, String sku, String barcode) {
        ProductForm f = new ProductForm();
        f.setName(name);
        f.setSku(sku);
        f.setBarcode(barcode);
        f.setCategoryId(category.getId());
        f.setCostPrice(new BigDecimal("5.00"));
        f.setSellPrice(new BigDecimal("8.00"));
        f.setStockQuantity(25);
        f.setLowStockThreshold(5);
        f.setActive(true);
        return f;
    }

    @Test
    void sellPriceWithMoreThanTwoDecimalsRoundsHalfUp() {
        ProductForm f = form("Decimal Product", "DEC-SKU", "DEC-BC");
        f.setSellPrice(new BigDecimal("1.234"));
        Product created = productService.create(f, admin);
        assertEquals(new BigDecimal("1.23"), created.getSellPrice());
    }
}
