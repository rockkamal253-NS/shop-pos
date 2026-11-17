package com.example.shoppos.user;

import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.inventory.StockMovement;
import com.example.shoppos.inventory.StockMovementRepository;
import com.example.shoppos.product.Category;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.CategoryRepository;
import com.example.shoppos.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dev-only sample data: one cashier account, 4 categories and 15 realistic
 * products (with initial RECEIVE movements so the stock history is consistent).
 * Runs only when the "dev" profile is active and only when the database is empty.
 */
@Component
@Profile("dev")
@org.springframework.core.annotation.Order(2) // runs after AdminSeedRunner so the admin user exists
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final PasswordEncoder passwordEncoder;

    public DevSeedRunner(UserRepository users, CategoryRepository categories,
                         ProductRepository products, StockMovementRepository movements,
                         PasswordEncoder passwordEncoder) {
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.movements = movements;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!users.existsByUsername("cashier1")) {
            users.save(new User("cashier1", passwordEncoder.encode("cashier123"), "Sara Hassan", Role.CASHIER));
        }
        if (categories.count() > 0) {
            return;
        }

        Category beverages = categories.save(new Category("Beverages"));
        Category grocery = categories.save(new Category("Grocery"));
        Category snacks = categories.save(new Category("Snacks"));
        Category household = categories.save(new Category("Household"));

        List<Object[]> rows = List.of(
                // name, sku, barcode, category, cost, sell, stock, threshold
                new Object[]{"Bottled Water 1.5L", "BEV-001", "6221031000013", beverages, bd("4.50"), bd("7.00"), 120, 20},
                new Object[]{"Cola Soft Drink 330ml", "BEV-002", "6221031000020", beverages, bd("8.00"), bd("12.00"), 64, 20},
                new Object[]{"Orange Juice 1L", "BEV-003", "6221031000037", beverages, bd("22.00"), bd("29.00"), 0, 10},
                new Object[]{"Whole Milk 1L", "BEV-004", "6221031000044", beverages, bd("21.00"), bd("27.00"), 38, 15},
                new Object[]{"Black Tea 100g", "BEV-005", "6221031000051", beverages, bd("28.00"), bd("36.50"), 25, 10},
                new Object[]{"Basmati Rice 1kg", "GRC-001", "6221041000012", grocery, bd("38.00"), bd("46.00"), 40, 10},
                new Object[]{"Sunflower Oil 1L", "GRC-002", "6221041000029", grocery, bd("62.00"), bd("75.00"), 18, 8},
                new Object[]{"White Sugar 1kg", "GRC-003", "6221041000036", grocery, bd("27.50"), bd("33.00"), 45, 12},
                new Object[]{"Pasta Elbows 400g", "GRC-004", "6221041000043", grocery, bd("12.00"), bd("17.50"), 8, 12},
                new Object[]{"Fine Salt 500g", "GRC-005", "6221041000050", grocery, bd("4.00"), bd("6.50"), 30, 10},
                new Object[]{"Potato Chips 35g", "SNK-001", "6221051000011", snacks, bd("7.50"), bd("11.00"), 6, 15},
                new Object[]{"Tea Biscuits 250g", "SNK-002", "6221051000028", snacks, bd("10.00"), bd("15.00"), 22, 10},
                new Object[]{"Chocolate Wafer 40g", "SNK-003", "6221051000035", snacks, bd("9.00"), bd("14.00"), 35, 15},
                new Object[]{"Laundry Soap Bar 200g", "HSD-001", "6221061000010", household, bd("14.00"), bd("19.00"), 26, 8},
                new Object[]{"Facial Tissues 200s", "HSD-002", "6221061000027", household, bd("12.50"), bd("18.00"), 14, 10});

        User admin = users.findByUsername("admin").orElseThrow();
        for (Object[] row : rows) {
            Product product = new Product((String) row[0], (String) row[1], (String) row[2],
                    (Category) row[3], (BigDecimal) row[4], (BigDecimal) row[5],
                    (Integer) row[6], (Integer) row[7], true);
            product = products.save(product);
            if (product.getStockQuantity() > 0) {
                movements.save(new StockMovement(product, MovementType.RECEIVE, product.getStockQuantity(),
                        product.getStockQuantity(), "INITIAL", admin, "Initial stock (sample data)"));
            }
        }
        log.info("Seeded {} sample products across 4 categories", rows.size());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
