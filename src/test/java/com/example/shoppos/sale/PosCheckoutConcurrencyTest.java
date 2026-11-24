package com.example.shoppos.sale;

import com.example.shoppos.product.Category;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserPrincipal;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.HttpSessionMutexListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ExtendWith(MockitoExtension.class)
class PosCheckoutConcurrencyTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleService saleService;

    @Mock
    private CurrentUserService currentUserService;

    private CartService cartService;
    private MockMvc mockMvc;
    private MockHttpSession session;
    private UsernamePasswordAuthenticationToken auth;
    private Product activeProduct;

    @BeforeEach
    void setUp() {
        cartService = new CartService(productRepository);
        PosController controller = new PosController(cartService, saleService, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        session = new MockHttpSession();
        new HttpSessionMutexListener().sessionCreated(new HttpSessionEvent(session));

        User cashier = new User("cashier1", "hash", "Cashier One", Role.CASHIER);
        UserPrincipal principal = new UserPrincipal(cashier);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        Category category = new Category("Test Category");
        activeProduct = new Product("Active Product", "SKU-001", "BAR-001", category,
                new BigDecimal("5.00"), new BigDecimal("10.00"), 10, 2, true);
        ReflectionTestUtils.setField(activeProduct, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));
    }

    @RepeatedTest(5)
    void doubleCheckoutCreatesOnlyOneSale() throws Exception {
        User cashier = new User("cashier1", "hash", "Cashier One", Role.CASHIER);
        when(currentUserService.getCurrent(any())).thenReturn(cashier);
        cartService.add(session, 1L);

        Sale sale = mock(Sale.class);
        when(sale.getId()).thenReturn(100L);
        when(saleService.completeSale(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(300);
            return sale;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return mockMvc.perform(post("/pos/checkout")
                                .principal(auth)
                                .session(session))
                        .andReturn();
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> f : futures) {
            results.add(f.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        verify(saleService, times(1)).completeSale(any(), any());
        long redirectCount = results.stream()
                .map(MvcResult::getResponse)
                .filter(r -> r.getHeader("HX-Redirect") != null)
                .count();
        assertEquals(1, redirectCount);
    }

    @RepeatedTest(5)
    void concurrentAddsKeepAllUnits() throws Exception {
        activeProduct.setStockQuantity(100);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch readyLatch = new CountDownLatch(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return mockMvc.perform(post("/pos/cart/add/1")
                                .principal(auth)
                                .session(session))
                        .andReturn();
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<MvcResult> f : futures) {
            MvcResult res = f.get(5, TimeUnit.SECONDS);
            assertEquals(200, res.getResponse().getStatus());
        }
        executor.shutdown();

        Cart cart = cartService.getCart(session);
        assertEquals(8, cart.getItems().get(1L));
    }
}
