package com.example.shoppos;

import com.example.shoppos.inventory.InventoryController;
import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.product.Category;
import com.example.shoppos.product.CategoryRepository;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductController;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserPrincipal;
import com.example.shoppos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FormValidationTest {

    @Autowired
    private InventoryController inventoryController;

    @Autowired
    private ProductController productController;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Validator validator;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private MockMvc mockMvcInventory;
    private MockMvc mockMvcProduct;
    private User admin;
    private UserPrincipal adminPrincipal;
    private Category category;

    @BeforeEach
    void setUp() {
        mockMvcInventory = MockMvcBuilders.standaloneSetup(inventoryController)
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mockMvcProduct = MockMvcBuilders.standaloneSetup(productController)
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        admin = users.save(new User("val_admin_" + SEQ.incrementAndGet(),
                passwordEncoder.encode("x"), "Validation Admin", Role.ADMIN));
        adminPrincipal = new UserPrincipal(admin);

        category = categories.save(new Category("Val Cat " + SEQ.incrementAndGet()));
    }

    @Test
    void postInventoryAdjustWithBlankNoteReturnsViewWithFieldErrorAndLeavesStockUnchanged() throws Exception {
        int n = SEQ.incrementAndGet();
        Product p = products.save(new Product("Adj Test " + n, "ASKU-" + n, "ABC-" + n,
                category, new BigDecimal("10.00"), new BigDecimal("15.00"), 20, 5, true));

        mockMvcInventory.perform(post("/inventory/adjust")
                .principal(new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()))
                .param("productId", p.getId().toString())
                .param("type", MovementType.DAMAGE.name())
                .param("quantity", "5")
                .param("note", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("inventory/adjust"))
                .andExpect(model().attributeHasFieldErrors("form", "note"));

        Product reloaded = products.findById(p.getId()).orElseThrow();
        assertEquals(20, reloaded.getStockQuantity());
    }

    @Test
    void postProductsWithBlankNameReturnsFormViewAndCreatesNoProduct() throws Exception {
        long countBefore = products.count();

        mockMvcProduct.perform(post("/products")
                .principal(new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()))
                .param("name", "  ")
                .param("sku", "BLANK-NAME-SKU")
                .param("barcode", "BLANK-NAME-BC")
                .param("categoryId", category.getId().toString())
                .param("costPrice", "5.00")
                .param("sellPrice", "8.00")
                .param("stockQuantity", "10")
                .param("lowStockThreshold", "2")
                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("product/form"))
                .andExpect(model().attributeHasFieldErrors("form", "name"));

        assertEquals(countBefore, products.count());
    }

    @Test
    void postProductsWithOversizedSellPriceReturnsFormViewWithFieldErrorAndCreatesNoProduct() throws Exception {
        long countBefore = products.count();

        mockMvcProduct.perform(post("/products")
                .principal(new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()))
                .param("name", "Oversized Price Item")
                .param("sku", "OVER-PRICE-SKU")
                .param("barcode", "OVER-PRICE-BC")
                .param("categoryId", category.getId().toString())
                .param("costPrice", "5.00")
                .param("sellPrice", "99999999999")
                .param("stockQuantity", "10")
                .param("lowStockThreshold", "2")
                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("product/form"))
                .andExpect(model().attributeHasFieldErrors("form", "sellPrice"));

        assertEquals(countBefore, products.count());
    }
}
