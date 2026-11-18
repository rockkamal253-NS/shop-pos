package com.example.shoppos.sale;

import com.example.shoppos.product.Product;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class PosControllerTest {

    @Mock
    private CartService cartService;

    @Mock
    private SaleService saleService;

    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PosController controller = new PosController(cartService, saleService, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void checkoutOptimisticLockFailureRendersCartInnerAndDoesNotClearCart() throws Exception {
        Cart cart = new Cart();
        User cashier = new User("cashier1", "hash", "Cashier One", Role.CASHIER);
        UserPrincipal principal = new UserPrincipal(cashier);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        MockHttpSession session = new MockHttpSession();

        CartService.CartView cartView = new CartService.CartView(
                Collections.emptyList(),
                BigDecimal.ZERO,
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                PaymentMethod.CASH,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        when(cartService.getCart(any())).thenReturn(cart);
        when(currentUserService.getCurrent(any())).thenReturn(cashier);
        when(cartService.view(any())).thenReturn(cartView);
        when(saleService.completeSale(any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        mockMvc.perform(post("/pos/checkout")
                        .principal(auth)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("pos/fragments :: cartInner"))
                .andExpect(model().attribute("checkoutError", "Stock just changed. Please review the cart and try again."))
                .andExpect(header().string("HX-Trigger", containsString("Stock just changed. Please review the cart and try again.")));

        verify(cartService, never()).clear(any());
    }
}
