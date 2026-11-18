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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
