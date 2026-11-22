package com.example.shoppos.sale;

import com.example.shoppos.common.web.NotFoundException;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserPrincipal;
import com.example.shoppos.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class SaleControllerTest {

    @Mock
    private SaleService saleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SaleController controller = new SaleController(saleService, userRepository, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cashierRequestingSomeoneElsesReceiptReturns404() throws Exception {
        User cashier1 = new User("cashier1", "hash", "Cashier One", Role.CASHIER);
        ReflectionTestUtils.setField(cashier1, "id", 10L);

        User cashier2 = new User("cashier2", "hash", "Cashier Two", Role.CASHIER);
        ReflectionTestUtils.setField(cashier2, "id", 20L);

        Sale saleByCashier2 = new Sale(cashier2, BigDecimal.TEN, DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, PaymentMethod.CASH, BigDecimal.TEN, BigDecimal.ZERO);
        ReflectionTestUtils.setField(saleByCashier2, "id", 100L);

        when(saleService.get(100L)).thenReturn(saleByCashier2);

        UserPrincipal principal = new UserPrincipal(cashier1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc.perform(get("/sales/100/receipt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cashierRequestingOwnReceiptReturns200() throws Exception {
        User cashier1 = new User("cashier1", "hash", "Cashier One", Role.CASHIER);
        ReflectionTestUtils.setField(cashier1, "id", 10L);

        Sale ownSale = new Sale(cashier1, BigDecimal.TEN, DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, PaymentMethod.CASH, BigDecimal.TEN, BigDecimal.ZERO);
        ReflectionTestUtils.setField(ownSale, "id", 100L);

        when(saleService.get(100L)).thenReturn(ownSale);

        UserPrincipal principal = new UserPrincipal(cashier1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc.perform(get("/sales/100/receipt"))
                .andExpect(status().isOk())
                .andExpect(view().name("sale/receipt"));
    }

    @Test
    void adminRequestingSomeoneElsesReceiptReturns200() throws Exception {
        User admin = new User("admin", "hash", "Admin User", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);

        User cashier2 = new User("cashier2", "hash", "Cashier Two", Role.CASHIER);
        ReflectionTestUtils.setField(cashier2, "id", 20L);

        Sale saleByCashier2 = new Sale(cashier2, BigDecimal.TEN, DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, PaymentMethod.CASH, BigDecimal.TEN, BigDecimal.ZERO);
        ReflectionTestUtils.setField(saleByCashier2, "id", 100L);

        when(saleService.get(100L)).thenReturn(saleByCashier2);

        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc.perform(get("/sales/100/receipt"))
                .andExpect(status().isOk())
                .andExpect(view().name("sale/receipt"));
    }
}
