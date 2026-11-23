package com.example.shoppos.common;

import com.example.shoppos.product.Category;
import com.example.shoppos.product.CategoryRepository;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.sale.DiscountType;
import com.example.shoppos.sale.PaymentMethod;
import com.example.shoppos.sale.Sale;
import com.example.shoppos.sale.SaleRepository;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PostgresFilterQueriesTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private UserRepository userRepository;

    private User cashier1;
    private User cashier2;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        saleRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        cashier1 = userRepository.save(new User("cashier1", "hash1", "Cashier One", Role.CASHIER));
        cashier2 = userRepository.save(new User("cashier2", "hash2", "Cashier Two", Role.CASHIER));

        Category cat = categoryRepository.save(new Category("Groceries"));

        productRepository.save(new Product("Milk 1L", "SKU-MILK", "BAR-MILK-123", cat,
                new BigDecimal("10.00"), new BigDecimal("15.00"), 50, 5, true));
        productRepository.save(new Product("Whole Bread", "SKU-BREAD", "BAR-BREAD-456", cat,
                new BigDecimal("5.00"), new BigDecimal("8.00"), 30, 5, true));
        productRepository.save(new Product("Butter 200g", "SKU-BUTTER", "BAR-BUTTER-789", cat,
                new BigDecimal("20.00"), new BigDecimal("25.00"), 20, 5, true));

        baseTime = LocalDateTime.of(2026, 11, 23, 10, 0, 0);

        Sale sale1 = new Sale(cashier1, new BigDecimal("15.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("15.00"),
                PaymentMethod.CASH, new BigDecimal("20.00"), new BigDecimal("5.00"));
        sale1.setCreatedAt(baseTime.minusDays(2));
        saleRepository.save(sale1);

        Sale sale2 = new Sale(cashier2, new BigDecimal("8.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("8.00"),
                PaymentMethod.CASH, new BigDecimal("10.00"), new BigDecimal("2.00"));
        sale2.setCreatedAt(baseTime.minusDays(1));
        saleRepository.save(sale2);

        Sale sale3 = new Sale(cashier1, new BigDecimal("25.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("25.00"),
                PaymentMethod.CASH, new BigDecimal("30.00"), new BigDecimal("5.00"));
        sale3.setCreatedAt(baseTime);
        saleRepository.save(sale3);
    }

    // --- ProductRepository.search ---

    @Test
    void productSearch_qNull_returnsAll() {
        Page<Product> result = productRepository.search(null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void productSearch_fragmentMatchesNameCaseInsensitively() {
        Page<Product> result = productRepository.search("milk", PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(Product::getName).contains("Milk 1L");
    }

    @Test
    void productSearch_fragmentMatchesSkuCaseInsensitively() {
        Page<Product> result = productRepository.search("sku-bread", PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(Product::getSku).contains("SKU-BREAD");
    }

    @Test
    void productSearch_fragmentMatchesBarcodeCaseInsensitively() {
        Page<Product> result = productRepository.search("bar-butter", PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(Product::getBarcode).contains("BAR-BUTTER-789");
    }

    @Test
    void productSearch_matchingNothing_returnsEmpty() {
        Page<Product> result = productRepository.search("nonexistentfragmentxyz", PageRequest.of(0, 10));
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void productSearch_pagingWorks() {
        Page<Product> page0 = productRepository.search(null, PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);

        Page<Product> page1 = productRepository.search(null, PageRequest.of(1, 2));
        assertThat(page1.getContent()).hasSize(1);
    }

    // --- SaleRepository.search ---

    @Test
    void saleSearch_allFiltersNull_returnsAll() {
        Page<Sale> result = saleRepository.search(null, null, null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void saleSearch_onlyFrom_filtersCorrectly() {
        Page<Sale> result = saleRepository.search(baseTime.minusDays(1), null, null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void saleSearch_onlyTo_filtersCorrectly() {
        Page<Sale> result = saleRepository.search(null, baseTime, null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void saleSearch_onlyCashierId_filtersCorrectly() {
        Page<Sale> result = saleRepository.search(null, null, cashier2.getId(), PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCashier().getId()).isEqualTo(cashier2.getId());
    }

    @Test
    void saleSearch_allThreeFilters_filtersCorrectly() {
        Page<Sale> result = saleRepository.search(baseTime.minusDays(3), baseTime.plusDays(1), cashier1.getId(), PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void saleSearch_pagingWorks() {
        Page<Sale> page0 = saleRepository.search(null, null, null, PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);

        Page<Sale> page1 = saleRepository.search(null, null, null, PageRequest.of(1, 2));
        assertThat(page1.getContent()).hasSize(1);
    }
}
