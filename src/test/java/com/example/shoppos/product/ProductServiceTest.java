package com.example.shoppos.product;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.DuplicateValueException;
import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.inventory.StockMovementRepository;
import com.example.shoppos.sale.SaleItemRepository;
import com.example.shoppos.sale.Cart;
import com.example.shoppos.sale.Sale;
import com.example.shoppos.sale.SaleService;
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
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private UserRepository users;

    @Autowired
    private StockMovementRepository movements;

    @Autowired
    private SaleService saleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Category category;
    private User admin;

    @BeforeEach
    void setUp() {
        category = categories.save(new Category("Prod Cat " + SEQ.incrementAndGet()));
        admin = users.save(new User("prod_admin_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Prod Admin", Role.ADMIN));
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
    void createPersistsProductAndRecordsInitialReceiveMovement() {
        Product created = productService.create(form("Test Rice", "TSKU-1", "TBC-1"), admin);

        assertTrue(products.existsBySkuIgnoreCase("TSKU-1"));
        assertEquals(25, created.getStockQuantity());
        var history = movements.findByProductIdOrderByCreatedAtDesc(created.getId(), Pageable.unpaged()).getContent();
        assertEquals(1, history.size());
        assertEquals(MovementType.RECEIVE, history.get(0).getType());
        assertEquals(25, history.get(0).getQuantityDelta());
    }

    @Test
    void createWithZeroStockRecordsNoMovement() {
        ProductForm f = form("Zero Stock Item", "TSKU-2", "TBC-2");
        f.setStockQuantity(0);
        Product created = productService.create(f, admin);
        assertTrue(movements.findByProductIdOrderByCreatedAtDesc(created.getId(), Pageable.unpaged()).isEmpty());
    }

    @Test
    void duplicateSkuIsRejected() {
        productService.create(form("First", "DUP-SKU", "DUP-BC-1"), admin);
        DuplicateValueException e = assertThrows(DuplicateValueException.class,
                () -> productService.create(form("Second", "DUP-SKU", "DUP-BC-2"), admin));
        assertEquals("sku", e.getField());
    }

    @Test
    void duplicateBarcodeIsRejected() {
        productService.create(form("First", "DUP-SKU-A", "DUP-BC-A"), admin);
        DuplicateValueException e = assertThrows(DuplicateValueException.class,
                () -> productService.create(form("Second", "DUP-SKU-B", "DUP-BC-A"), admin));
        assertEquals("barcode", e.getField());
    }

    @Test
    void updateKeepsStockUntouched() {
        Product created = productService.create(form("Keeper", "KEEP-SKU", "KEEP-BC"), admin);
        ProductForm edit = form("Keeper", "KEEP-SKU", "KEEP-BC");
        edit.setSellPrice(new BigDecimal("9.50"));
        edit.setStockQuantity(9999); // must be ignored
        productService.update(created.getId(), edit);

        Product reloaded = products.findById(created.getId()).orElseThrow();
        assertEquals("9.50", reloaded.getSellPrice().toPlainString());
        assertEquals(25, reloaded.getStockQuantity());
    }

    @Test
    void deleteIsBlockedWhenProductHasSales() {
        Product created = productService.create(form("Sold Out", "SOLD-SKU", "SOLD-BC"), admin);

        Cart cart = new Cart();
        cart.getItems().put(created.getId(), 1);
        cart.setCashTendered(new BigDecimal("999.00"));
        saleService.completeSale(cart, admin);

        assertThrows(BusinessRuleException.class, () -> productService.delete(created.getId()));
        assertTrue(products.existsById(created.getId()));
        assertTrue(products.findById(created.getId()).orElseThrow().isActive());
    }

    @Test
    void deleteWorksWhenNoHistoryExists() {
        ProductForm f = form("Disposable", "DEL-SKU", "DEL-BC");
        f.setStockQuantity(0); // no initial movement
        Product created = productService.create(f, admin);
        productService.delete(created.getId());
        assertTrue(products.findById(created.getId()).isEmpty());
    }

    @Test
    void searchMatchesNameSkuAndBarcode() {
        productService.create(form("Premium Basmati Rice", "SRC-100", "SRC-BC-100"), admin);

        assertEquals(1, productService.search("basmati", 0).getTotalElements());
        assertEquals(1, productService.search("SRC-100", 0).getTotalElements());
        assertEquals(1, productService.search("SRC-BC-100", 0).getTotalElements());
        assertEquals(0, productService.search("nonexistent-product-xyz", 0).getTotalElements());
    }
}
