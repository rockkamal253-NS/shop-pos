package com.example.shoppos.sale;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.NotFoundException;
import com.example.shoppos.inventory.InventoryService;
import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sale completion and voiding. Both run in ONE transaction:
 * sale + snapshot items are written, stock is decremented (optimistic locking
 * on Product prevents overselling under concurrency), and a stock movement is
 * recorded for every affected product.
 */
@Service
public class SaleService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final int maxCashierDiscountPercent;

    @Autowired
    public SaleService(SaleRepository sales, ProductRepository products, InventoryService inventory,
                       @Value("${app.max-cashier-discount-percent:20}") int maxCashierDiscountPercent) {
        this.sales = sales;
        this.products = products;
        this.inventory = inventory;
        this.maxCashierDiscountPercent = maxCashierDiscountPercent;
    }

    /** Convenience constructor for tests that don't need to override the limit. */
    public SaleService(SaleRepository sales, ProductRepository products, InventoryService inventory) {
        this(sales, products, inventory, 20);
    }

    @Transactional
    public Sale completeSale(Cart cart, User cashier) {
        if (cart.isEmpty()) {
            throw new BusinessRuleException("The cart is empty.");
        }
        for (var entry : cart.getItems().entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 1) {
                throw new BusinessRuleException("Invalid quantity in cart.");
            }
        }

        // 1. Load and validate everything BEFORE writing anything.
        List<Product> sold = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (var entry : cart.getItems().entrySet()) {
            Product product = products.findById(entry.getKey())
                    .orElseThrow(() -> new BusinessRuleException("A product in the cart no longer exists."));
            if (!product.isActive()) {
                throw new BusinessRuleException("\"" + product.getName() + "\" is inactive and cannot be sold.");
            }
            int qty = entry.getValue();
            if (product.getStockQuantity() < qty) {
                shortages.add(product.getName() + " (available " + product.getStockQuantity()
                        + ", requested " + qty + ")");
            }
            sold.add(product);
            subtotal = subtotal.add(SalePricing.lineTotal(product.getSellPrice(), qty));
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }

        boolean cash = cart.getPaymentMethod() == PaymentMethod.CASH;
        SalePricing.Totals totals = SalePricing.compute(subtotal, cart.getDiscountType(),
                cart.getDiscountValue(), cash ? cart.getCashTendered() : null);
        if (cash && (cart.getCashTendered() == null || totals.changeDue().signum() < 0)) {
            throw new BusinessRuleException("Cash tendered is less than the total.");
        }

        // Enforce cashier discount cap (admins are exempt).
        if (cashier.getRole() != Role.ADMIN) {
            BigDecimal maxAllowed = subtotal
                    .multiply(BigDecimal.valueOf(maxCashierDiscountPercent))
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (totals.discountAmount().compareTo(maxAllowed) > 0) {
                throw new BusinessRuleException(
                        "Discounts above " + maxCashierDiscountPercent + "% need an admin.");
            }
        }

        // 2. Build the sale with price/name snapshots, then persist to obtain the ID.
        Sale sale = new Sale(cashier, totals.subtotal(), cart.getDiscountType(), cart.getDiscountValue(),
                totals.discountAmount(), totals.total(), cart.getPaymentMethod(),
                cash ? cart.getCashTendered() : null, cash ? totals.changeDue() : null);
        for (var entry : cart.getItems().entrySet()) {
            Product product = sold.stream().filter(p -> p.getId().equals(entry.getKey())).findFirst().orElseThrow();
            sale.addItem(new SaleItem(product, entry.getValue(),
                    SalePricing.lineTotal(product.getSellPrice(), entry.getValue())));
        }
        sale = sales.save(sale);
        sales.flush();

        // 3. Decrement stock (version bump => concurrent sales cannot oversell) and record movements.
        String reference = "SALE #" + sale.getId();
        for (var entry : cart.getItems().entrySet()) {
            Product product = sold.stream().filter(p -> p.getId().equals(entry.getKey())).findFirst().orElseThrow();
            int qty = entry.getValue();
            product.setStockQuantity(product.getStockQuantity() - qty);
            products.save(product);
            inventory.record(product, MovementType.SALE, -qty, reference, cashier, "Sold at register");
        }
        return sale;
    }

    /** ADMIN: undo a sale — restores stock and writes a VOID_RESTORE movement per item. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Sale voidSale(Long saleId, User admin) {
        Sale sale = sales.findWithDetailsById(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found."));
        if (sale.isVoided()) {
            throw new BusinessRuleException("This sale is already voided.");
        }
        for (SaleItem item : sale.getItems()) {
            Product product = products.findById(item.getProductId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Product \"" + item.getProductName() + "\" no longer exists — stock cannot be restored."));
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            products.save(product);
            inventory.record(product, MovementType.VOID_RESTORE, item.getQuantity(),
                    "VOID #" + sale.getId(), admin, "Sale voided by admin");
        }
        sale.setStatus(SaleStatus.VOIDED);
        sale.setVoidedAt(LocalDateTime.now());
        sale.setVoidedBy(admin);
        return sales.save(sale);
    }

    @Transactional(readOnly = true)
    public Sale get(Long id) {
        return sales.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Sale not found."));
    }

    @Transactional(readOnly = true)
    public Page<Sale> search(LocalDateTime from, LocalDateTime to, Long cashierId, int page) {
        Page<Sale> result = sales.search(from, to, cashierId, PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        result.forEach(sale -> Hibernate.initialize(sale.getItems()));
        return result;
    }
}
