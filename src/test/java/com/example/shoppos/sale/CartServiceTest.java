package com.example.shoppos.sale;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.product.Category;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private ProductRepository productRepository;

    private CartService cartService;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        cartService = new CartService(productRepository, 20);
        session = new MockHttpSession();
    }

    @Test
    void setQuantityWhenStockDropsToZeroRemovesLineAndReturnsMessage() {
        Category category = new Category("Test Category");
        Product product = new Product("Soap Bar", "SKU-001", "1234567890", category,
                new BigDecimal("5.00"), new BigDecimal("10.00"), 3, 1, true);
        ReflectionTestUtils.setField(product, "id", 101L);

        when(productRepository.findById(101L)).thenReturn(Optional.of(product));

        // Add item to cart when stock was 3
        cartService.add(session, 101L);
        assertEquals(1, cartService.getCart(session).getItems().size());

        // Stock drops to 0 in inventory
        product.setStockQuantity(0);

        // Cashier edits quantity to 2
        String message = cartService.setQuantity(session, 101L, 2);

        assertEquals("\"Soap Bar\" is out of stock and was removed.", message);
        assertTrue(cartService.getCart(session).getItems().isEmpty());
    }

    @Test
    void discountPercentOverLimitRejectedAndAtLimitAccepted() {
        // Limit is configured to 20%
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("20.01")));
        assertEquals("Discounts above 20% need an admin.", ex.getMessage());

        BusinessRuleException ex2 = assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("50")));
        assertEquals("Discounts above 20% need an admin.", ex2.getMessage());

        // At limit (20%) is accepted
        cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("20.00"));
        assertEquals(new BigDecimal("20.00"), cartService.getCart(session).getDiscountValue());
    }

    @Test
    void discountAmount1e10Rejected() {
        BigDecimal huge = new BigDecimal("10000000000"); // 1e10
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.AMOUNT, huge));
        assertEquals("Amount is too large.", ex.getMessage());

        // At or below MAX_AMOUNT is accepted
        cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("9999999.99"));
        assertEquals(new BigDecimal("9999999.99"), cartService.getCart(session).getDiscountValue());
    }

    @Test
    void cashTendered1e10Rejected() {
        BigDecimal huge = new BigDecimal("10000000000"); // 1e10
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> cartService.setCashTendered(session, huge));
        assertEquals("Amount is too large.", ex.getMessage());

        // At or below MAX_AMOUNT is accepted
        cartService.setCashTendered(session, new BigDecimal("9999999.99"));
        assertEquals(new BigDecimal("9999999.99"), cartService.getCart(session).getCashTendered());
    }

    // ── TASK-06 admin-flag tests ────────────────────────────────────────────

    @Test
    void percentDiscount50RejectedForNonAdminAcceptedForAdmin() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("50"), false));
        assertEquals("Discounts above 20% need an admin.", ex.getMessage());

        // admin: accepted
        cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("50"), true);
        assertEquals(new BigDecimal("50.00"), cartService.getCart(session).getDiscountValue());
    }

    @Test
    void percentDiscount101RejectedForBothAdminAndNonAdmin() {
        assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("101"), false));
        assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("101"), true));
    }

    @Test
    void amountDiscountCappedForCashierSkippedForEmptyCartAndAdmin() {
        Category category = new Category("Test Category");
        Product product = new Product("Item", "SKU-1", "BC-1", category,
                new BigDecimal("50.00"), new BigDecimal("100.00"), 10, 2, true);
        ReflectionTestUtils.setField(product, "id", 1L);

        // Subtotal = 100.00, max cashier percent = 20% -> max amount = 20.00
        Cart cart = cartService.getCart(session);
        cart.getItems().put(1L, 1);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        // Cashier AMOUNT 20.00 accepted
        cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("20.00"), false);
        assertEquals(new BigDecimal("20.00"), cartService.getCart(session).getDiscountValue());

        // Cashier AMOUNT 20.01 rejected with "need an admin" message
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("20.01"), false));
        assertEquals("Discounts above 20% need an admin.", ex.getMessage());

        // Admin AMOUNT 50.00 accepted
        cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("50.00"), true);
        assertEquals(new BigDecimal("50.00"), cartService.getCart(session).getDiscountValue());

        // Cashier AMOUNT 500.00 on an EMPTY cart accepted (early check skipped)
        MockHttpSession emptySession = new MockHttpSession();
        cartService.setDiscount(emptySession, DiscountType.AMOUNT, new BigDecimal("500.00"), false);
        assertEquals(new BigDecimal("500.00"), cartService.getCart(emptySession).getDiscountValue());
    }

    @Test
    void viewOnCartWith3ProductsCallsFindAllByIdOnceAndFindByIdNever() {
        Category category = new Category("Test Category");
        Product p1 = new Product("P1", "SKU-1", "BC-1", category, new BigDecimal("10.00"), new BigDecimal("15.00"), 5, 1, true);
        Product p2 = new Product("P2", "SKU-2", "BC-2", category, new BigDecimal("20.00"), new BigDecimal("25.00"), 5, 1, true);
        Product p3 = new Product("P3", "SKU-3", "BC-3", category, new BigDecimal("30.00"), new BigDecimal("35.00"), 5, 1, true);
        ReflectionTestUtils.setField(p1, "id", 1L);
        ReflectionTestUtils.setField(p2, "id", 2L);
        ReflectionTestUtils.setField(p3, "id", 3L);

        Cart cart = cartService.getCart(session);
        cart.getItems().put(1L, 1);
        cart.getItems().put(2L, 2);
        cart.getItems().put(3L, 3);

        when(productRepository.findAllById(cart.getItems().keySet())).thenReturn(List.of(p1, p2, p3));

        CartService.CartView view = cartService.view(session);

        assertEquals(3, view.lines().size());
        verify(productRepository, times(1)).findAllById(cart.getItems().keySet());
        verify(productRepository, never()).findById(any());
    }

    @Test
    void discountTypeAndValuePreservedWhenValidationFails() {
        Category category = new Category("Test Category");
        Product product = new Product("Item", "SKU-1", "BC-1", category,
                new BigDecimal("50.00"), new BigDecimal("100.00"), 10, 2, true);
        ReflectionTestUtils.setField(product, "id", 1L);

        Cart cart = cartService.getCart(session);
        cart.getItems().put(1L, 1);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        // Existing discount: PERCENT 10
        cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("10.00"), false);
        assertEquals(DiscountType.PERCENT, cart.getDiscountType());
        assertEquals(new BigDecimal("10.00"), cart.getDiscountValue());

        // Cashier submits AMOUNT 500.00 -> BusinessRuleException AND cart still has type PERCENT and value 10.00
        assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("500.00"), false));
        assertEquals(DiscountType.PERCENT, cart.getDiscountType());
        assertEquals(new BigDecimal("10.00"), cart.getDiscountValue());

        // Setup cart with AMOUNT 5.00
        cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("5.00"), false);
        assertEquals(DiscountType.AMOUNT, cart.getDiscountType());
        assertEquals(new BigDecimal("5.00"), cart.getDiscountValue());

        // Cashier submits PERCENT 50 (over 20% cap) -> exception and old AMOUNT 5.00 is kept
        assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("50.00"), false));
        assertEquals(DiscountType.AMOUNT, cart.getDiscountType());
        assertEquals(new BigDecimal("5.00"), cart.getDiscountValue());

        // Valid change updates both type and value
        cartService.setDiscount(session, DiscountType.PERCENT, new BigDecimal("15.00"), false);
        assertEquals(DiscountType.PERCENT, cart.getDiscountType());
        assertEquals(new BigDecimal("15.00"), cart.getDiscountValue());

        // Negative value is rejected and changes nothing
        assertThrows(BusinessRuleException.class,
                () -> cartService.setDiscount(session, DiscountType.AMOUNT, new BigDecimal("-5.00"), false));
        assertEquals(DiscountType.PERCENT, cart.getDiscountType());
        assertEquals(new BigDecimal("15.00"), cart.getDiscountValue());
    }

    // ── end TASK-06 admin-flag tests ───────────────────────────────────────
}
