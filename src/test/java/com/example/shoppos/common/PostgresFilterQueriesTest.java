package com.example.shoppos.common;

import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.inventory.StockMovement;
import com.example.shoppos.inventory.StockMovementRepository;
import com.example.shoppos.product.Category;
import com.example.shoppos.product.CategoryRepository;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.sale.DiscountType;
import com.example.shoppos.sale.PaymentMethod;
import com.example.shoppos.sale.Sale;
import com.example.shoppos.sale.SaleItem;
import com.example.shoppos.sale.SaleItemRepository;
import com.example.shoppos.sale.SaleRepository;
import com.example.shoppos.sale.SaleStatus;
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
import java.util.List;
import java.util.Optional;

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

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

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

    // --- CategoryRepository.findAllWithCounts ---

    @Test
    void findAllWithCounts_returnsCategoriesWithCountOrderedByName() {
        categoryRepository.save(new Category("Zzz Empty"));
        List<CategoryRepository.CategoryWithCount> list = categoryRepository.findAllWithCounts();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getName()).isEqualTo("Groceries");
        assertThat(list.get(0).getProductCount()).isEqualTo(3L);
        assertThat(list.get(1).getName()).isEqualTo("Zzz Empty");
        assertThat(list.get(1).getProductCount()).isEqualTo(0L);
    }

    // --- ProductRepository.findLowStock ---

    @Test
    void findLowStock_returnsActiveProductsAtOrBelowThresholdOrderedByStock() {
        Category cat = categoryRepository.findAll().get(0);
        Product p0 = productRepository.save(new Product("P0", "SKU-P0", "BAR-P0", cat,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 0, 5, true));
        Product p2 = productRepository.save(new Product("P2", "SKU-P2", "BAR-P2", cat,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 2, 5, true));
        Product p4 = productRepository.save(new Product("P4", "SKU-P4", "BAR-P4", cat,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 4, 5, true));
        productRepository.save(new Product("P-Inactive", "SKU-INACT", "BAR-INACT", cat,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 0, 5, false));
        productRepository.save(new Product("P-Healthy", "SKU-HLTH", "BAR-HLTH", cat,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 10, 5, true));

        List<Product> lowStock = productRepository.findLowStock(PageRequest.of(0, 10));
        assertThat(lowStock).extracting(Product::getId).containsExactly(p0.getId(), p2.getId(), p4.getId());

        List<Product> lowStockPage = productRepository.findLowStock(PageRequest.of(0, 2));
        assertThat(lowStockPage).extracting(Product::getId).containsExactly(p0.getId(), p2.getId());
    }

    // --- SaleItemRepository.findTopProducts ---

    @Test
    void findTopProducts_returnsTopSellersOrderedByQuantitySold() {
        List<Product> prods = productRepository.findAll();
        Product prodA = prods.get(0);
        Product prodB = prods.get(1);

        LocalDateTime since = baseTime.plusHours(1);

        // Completed sale 1 (after since)
        Sale s1 = new Sale(cashier1, new BigDecimal("91.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("91.00"),
                PaymentMethod.CASH, new BigDecimal("100.00"), new BigDecimal("9.00"));
        s1.setCreatedAt(since.plusHours(1));
        s1.addItem(new SaleItem(prodA, 5, new BigDecimal("75.00")));
        s1.addItem(new SaleItem(prodB, 2, new BigDecimal("16.00")));
        saleRepository.save(s1);

        // Completed sale 2 (after since)
        Sale s2 = new Sale(cashier1, new BigDecimal("53.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("53.00"),
                PaymentMethod.CASH, new BigDecimal("60.00"), new BigDecimal("7.00"));
        s2.setCreatedAt(since.plusHours(2));
        s2.addItem(new SaleItem(prodA, 3, new BigDecimal("45.00")));
        s2.addItem(new SaleItem(prodB, 1, new BigDecimal("8.00")));
        saleRepository.save(s2);

        // Voided sale (must NOT count)
        Sale sVoided = new Sale(cashier1, new BigDecimal("160.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("160.00"),
                PaymentMethod.CASH, new BigDecimal("160.00"), BigDecimal.ZERO);
        sVoided.setStatus(SaleStatus.VOIDED);
        sVoided.setCreatedAt(since.plusHours(3));
        sVoided.addItem(new SaleItem(prodB, 20, new BigDecimal("160.00")));
        saleRepository.save(sVoided);

        // Old completed sale before since (must NOT count)
        Sale sOld = new Sale(cashier1, new BigDecimal("160.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("160.00"),
                PaymentMethod.CASH, new BigDecimal("160.00"), BigDecimal.ZERO);
        sOld.setCreatedAt(since.minusDays(1));
        sOld.addItem(new SaleItem(prodB, 20, new BigDecimal("160.00")));
        saleRepository.save(sOld);

        List<SaleItemRepository.TopProduct> top = saleItemRepository.findTopProducts(since, PageRequest.of(0, 10));
        assertThat(top).hasSize(2);

        SaleItemRepository.TopProduct first = top.get(0);
        assertThat(first.getProductId()).isEqualTo(prodA.getId());
        assertThat(first.getProductName()).isEqualTo(prodA.getName());
        assertThat(first.getQuantitySold()).isEqualTo(8L);
        assertThat(first.getRevenue()).isEqualByComparingTo("120.00");

        SaleItemRepository.TopProduct second = top.get(1);
        assertThat(second.getProductId()).isEqualTo(prodB.getId());
        assertThat(second.getProductName()).isEqualTo(prodB.getName());
        assertThat(second.getQuantitySold()).isEqualTo(3L);
        assertThat(second.getRevenue()).isEqualByComparingTo("24.00");

        List<SaleItemRepository.TopProduct> page1 = saleItemRepository.findTopProducts(since, PageRequest.of(0, 1));
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).getProductId()).isEqualTo(prodA.getId());
    }

    // --- SaleRepository.revenueBetween ---

    @Test
    void revenueBetween_sumsCompletedSalesAndExcludesVoidedOrOutOfRange() {
        LocalDateTime from = baseTime.plusDays(10);
        LocalDateTime to = baseTime.plusDays(12);

        // Sale exactly at from -> included (createdAt >= from)
        Sale sAtFrom = new Sale(cashier1, new BigDecimal("50.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"),
                PaymentMethod.CASH, new BigDecimal("50.00"), BigDecimal.ZERO);
        sAtFrom.setCreatedAt(from);
        saleRepository.save(sAtFrom);

        // Sale inside range -> included
        Sale sInside = new Sale(cashier1, new BigDecimal("30.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00"),
                PaymentMethod.CASH, new BigDecimal("30.00"), BigDecimal.ZERO);
        sInside.setCreatedAt(from.plusDays(1));
        saleRepository.save(sInside);

        // Voided sale inside range -> excluded
        Sale sVoided = new Sale(cashier1, new BigDecimal("100.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"),
                PaymentMethod.CASH, new BigDecimal("100.00"), BigDecimal.ZERO);
        sVoided.setStatus(SaleStatus.VOIDED);
        sVoided.setCreatedAt(from.plusHours(12));
        saleRepository.save(sVoided);

        // Sale exactly at to -> excluded (createdAt < to)
        Sale sAtTo = new Sale(cashier1, new BigDecimal("70.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("70.00"),
                PaymentMethod.CASH, new BigDecimal("70.00"), BigDecimal.ZERO);
        sAtTo.setCreatedAt(to);
        saleRepository.save(sAtTo);

        // Sale before from -> excluded
        Sale sBefore = new Sale(cashier1, new BigDecimal("20.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"),
                PaymentMethod.CASH, new BigDecimal("20.00"), BigDecimal.ZERO);
        sBefore.setCreatedAt(from.minusHours(1));
        saleRepository.save(sBefore);

        BigDecimal revenue = saleRepository.revenueBetween(from, to);
        assertThat(revenue).isEqualByComparingTo("80.00");

        // Empty range returns zero (not null)
        BigDecimal emptyRev = saleRepository.revenueBetween(to.plusDays(5), to.plusDays(6));
        assertThat(emptyRev).isNotNull();
        assertThat(emptyRev.compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    // --- SaleRepository.countCompletedBetween ---

    @Test
    void countCompletedBetween_countsCompletedSalesInBoundary() {
        LocalDateTime from = baseTime.plusDays(20);
        LocalDateTime to = baseTime.plusDays(22);

        Sale sAtFrom = new Sale(cashier1, new BigDecimal("10.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"),
                PaymentMethod.CASH, new BigDecimal("10.00"), BigDecimal.ZERO);
        sAtFrom.setCreatedAt(from);
        saleRepository.save(sAtFrom);

        Sale sInside = new Sale(cashier1, new BigDecimal("20.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"),
                PaymentMethod.CASH, new BigDecimal("20.00"), BigDecimal.ZERO);
        sInside.setCreatedAt(from.plusDays(1));
        saleRepository.save(sInside);

        Sale sVoided = new Sale(cashier1, new BigDecimal("30.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00"),
                PaymentMethod.CASH, new BigDecimal("30.00"), BigDecimal.ZERO);
        sVoided.setStatus(SaleStatus.VOIDED);
        sVoided.setCreatedAt(from.plusHours(12));
        saleRepository.save(sVoided);

        Sale sAtTo = new Sale(cashier1, new BigDecimal("40.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40.00"),
                PaymentMethod.CASH, new BigDecimal("40.00"), BigDecimal.ZERO);
        sAtTo.setCreatedAt(to);
        saleRepository.save(sAtTo);

        long count = saleRepository.countCompletedBetween(from, to);
        assertThat(count).isEqualTo(2L);

        long emptyCount = saleRepository.countCompletedBetween(to.plusDays(5), to.plusDays(6));
        assertThat(emptyCount).isEqualTo(0L);
    }

    // --- SaleRepository.findWithDetailsById ---

    @Test
    void sale_findWithDetailsById_loadsAssociatedDetailsOrEmpty() {
        User admin = userRepository.save(new User("admin_test", "hash", "Admin Test", Role.ADMIN));
        Product prod = productRepository.findAll().get(0);

        Sale s = new Sale(cashier1, new BigDecimal("15.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("15.00"),
                PaymentMethod.CASH, new BigDecimal("20.00"), new BigDecimal("5.00"));
        s.setStatus(SaleStatus.VOIDED);
        s.setVoidedBy(admin);
        s.setVoidedAt(baseTime);
        s.addItem(new SaleItem(prod, 1, new BigDecimal("15.00")));
        Sale saved = saleRepository.save(s);

        Optional<Sale> loaded = saleRepository.findWithDetailsById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getCashier().getUsername()).isEqualTo("cashier1");
        assertThat(loaded.get().getVoidedBy().getUsername()).isEqualTo("admin_test");
        assertThat(loaded.get().getItems()).hasSize(1);
        assertThat(loaded.get().getItems().get(0).getProductName()).isEqualTo("Milk 1L");

        assertThat(saleRepository.findWithDetailsById(999999L)).isEmpty();
    }

    // --- ProductRepository: findByBarcode, findBySkuIgnoreCase, existsBy* ---

    @Test
    void product_findByBarcode_returnsProductOrEmpty() {
        Optional<Product> found = productRepository.findByBarcode("BAR-MILK-123");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Milk 1L");

        assertThat(productRepository.findByBarcode("BAR-NONEXISTENT")).isEmpty();
    }

    @Test
    void product_findBySkuIgnoreCase_matchesCaseInsensitively() {
        Optional<Product> found = productRepository.findBySkuIgnoreCase("sku-milk");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Milk 1L");

        assertThat(productRepository.findBySkuIgnoreCase("sku-nonexistent")).isEmpty();
    }

    @Test
    void product_existsBySkuIgnoreCase_matchesCaseInsensitively() {
        assertThat(productRepository.existsBySkuIgnoreCase("sku-milk")).isTrue();
        assertThat(productRepository.existsBySkuIgnoreCase("SKU-MILK")).isTrue();
        assertThat(productRepository.existsBySkuIgnoreCase("sku-nonexistent")).isFalse();
    }

    @Test
    void product_existsByBarcodeIgnoreCase_matchesCaseInsensitively() {
        assertThat(productRepository.existsByBarcodeIgnoreCase("bar-milk-123")).isTrue();
        assertThat(productRepository.existsByBarcodeIgnoreCase("BAR-MILK-123")).isTrue();
        assertThat(productRepository.existsByBarcodeIgnoreCase("bar-nonexistent")).isFalse();
    }

    @Test
    void product_existsBySkuIgnoreCaseAndIdNot_excludesGivenId() {
        Product p = productRepository.findAll().get(0);
        assertThat(productRepository.existsBySkuIgnoreCaseAndIdNot("sku-milk", p.getId())).isFalse();
        assertThat(productRepository.existsBySkuIgnoreCaseAndIdNot("sku-milk", p.getId() + 999L)).isTrue();
        assertThat(productRepository.existsBySkuIgnoreCaseAndIdNot("sku-nonexistent", p.getId() + 999L)).isFalse();
    }

    @Test
    void product_existsByBarcodeIgnoreCaseAndIdNot_excludesGivenId() {
        Product p = productRepository.findAll().get(0);
        assertThat(productRepository.existsByBarcodeIgnoreCaseAndIdNot("bar-milk-123", p.getId())).isFalse();
        assertThat(productRepository.existsByBarcodeIgnoreCaseAndIdNot("bar-milk-123", p.getId() + 999L)).isTrue();
        assertThat(productRepository.existsByBarcodeIgnoreCaseAndIdNot("bar-nonexistent", p.getId() + 999L)).isFalse();
    }

    @Test
    void product_existsByCategoryId_returnsTrueWhenCategoryHasProducts() {
        Category cat = categoryRepository.findAll().get(0);
        assertThat(productRepository.existsByCategoryId(cat.getId())).isTrue();
        assertThat(productRepository.existsByCategoryId(cat.getId() + 999L)).isFalse();
    }

    // --- CategoryRepository: existsByNameIgnoreCase, existsByNameIgnoreCaseAndIdNot ---

    @Test
    void category_existsByNameIgnoreCase_checksCaseInsensitively() {
        assertThat(categoryRepository.existsByNameIgnoreCase("groceries")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("GROCERIES")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("NonExistent")).isFalse();
    }

    @Test
    void category_existsByNameIgnoreCaseAndIdNot_excludesGivenId() {
        Category cat = categoryRepository.findAll().get(0);
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("groceries", cat.getId())).isFalse();
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("groceries", cat.getId() + 999L)).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("nonexistent", cat.getId() + 999L)).isFalse();
    }

    // --- SaleItemRepository: existsByProductId ---

    @Test
    void saleItem_existsByProductId_returnsTrueWhenItemExists() {
        Product prod = productRepository.findAll().get(0);
        Sale sale = new Sale(cashier1, new BigDecimal("15.00"), DiscountType.NONE,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("15.00"),
                PaymentMethod.CASH, new BigDecimal("20.00"), new BigDecimal("5.00"));
        sale.addItem(new SaleItem(prod, 1, new BigDecimal("15.00")));
        saleRepository.save(sale);

        assertThat(saleItemRepository.existsByProductId(prod.getId())).isTrue();
        assertThat(saleItemRepository.existsByProductId(prod.getId() + 999L)).isFalse();
    }

    // --- StockMovementRepository: existsByProductId, findByProductIdOrderByCreatedAtDesc, findTop50ByOrderByCreatedAtDesc ---

    @Test
    void stockMovement_existsByProductId_returnsTrueWhenMovementExists() {
        Product prod = productRepository.findAll().get(0);
        StockMovement movement = new StockMovement(prod, MovementType.RECEIVE, 10, 60,
                "PO-100", cashier1, "Initial stock");
        stockMovementRepository.save(movement);

        assertThat(stockMovementRepository.existsByProductId(prod.getId())).isTrue();
        assertThat(stockMovementRepository.existsByProductId(prod.getId() + 999L)).isFalse();
    }

    @Test
    void stockMovement_findByProductIdOrderByCreatedAtDesc_returnsOrderedPagedMovements() {
        Product prod = productRepository.findAll().get(0);
        StockMovement m1 = new StockMovement(prod, MovementType.RECEIVE, 10, 60,
                "PO-1", cashier1, "First");
        stockMovementRepository.save(m1);

        StockMovement m2 = new StockMovement(prod, MovementType.SALE, -2, 58,
                "SALE-1", cashier1, "Second");
        stockMovementRepository.save(m2);

        Page<StockMovement> page = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(
                prod.getId(), PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getReference()).isEqualTo("SALE-1");
        assertThat(page.getContent().get(1).getReference()).isEqualTo("PO-1");

        Page<StockMovement> emptyPage = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(
                prod.getId() + 999L, PageRequest.of(0, 10));
        assertThat(emptyPage.getContent()).isEmpty();
    }

    @Test
    void stockMovement_findTop50ByOrderByCreatedAtDesc_returnsRecentMovements() {
        Product prod = productRepository.findAll().get(0);
        StockMovement m1 = new StockMovement(prod, MovementType.RECEIVE, 5, 55,
                "REF-1", cashier1, "Note 1");
        stockMovementRepository.save(m1);

        StockMovement m2 = new StockMovement(prod, MovementType.DAMAGE, -1, 54,
                "REF-2", cashier1, "Note 2");
        stockMovementRepository.save(m2);

        List<StockMovement> top = stockMovementRepository.findTop50ByOrderByCreatedAtDesc();
        assertThat(top).isNotEmpty();
        assertThat(top.get(0).getReference()).isEqualTo("REF-2");
        assertThat(top.get(1).getReference()).isEqualTo("REF-1");
    }

    // --- UserRepository: findByUsername, existsByUsername, findByRoleOrderByIdAsc ---

    @Test
    void user_findByUsername_returnsUserOrEmpty() {
        Optional<User> found = userRepository.findByUsername("cashier1");
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Cashier One");

        assertThat(userRepository.findByUsername("nonexistent")).isEmpty();
    }

    @Test
    void user_existsByUsername_returnsTrueForExistingUser() {
        assertThat(userRepository.existsByUsername("cashier1")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void user_findByRoleOrderByIdAsc_returnsUsersOrderedById() {
        List<User> cashiers = userRepository.findByRoleOrderByIdAsc(Role.CASHIER);
        assertThat(cashiers).hasSize(2);
        assertThat(cashiers.get(0).getUsername()).isEqualTo("cashier1");
        assertThat(cashiers.get(1).getUsername()).isEqualTo("cashier2");
        assertThat(cashiers.get(0).getId()).isLessThan(cashiers.get(1).getId());

        List<User> admins = userRepository.findByRoleOrderByIdAsc(Role.ADMIN);
        assertThat(admins).isEmpty();
    }
}
