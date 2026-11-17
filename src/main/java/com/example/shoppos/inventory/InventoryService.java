package com.example.shoppos.inventory;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.NotFoundException;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final StockMovementRepository movements;
    private final ProductRepository products;

    public InventoryService(StockMovementRepository movements, ProductRepository products) {
        this.movements = movements;
        this.products = products;
    }

    /** Manual adjustment (receive / correction / damage) with a mandatory reason. */
    @Transactional
    public Product adjust(StockAdjustForm form, User user) {
        Product product = products.findById(form.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found."));

        int delta = switch (form.getType()) {
            case RECEIVE -> form.getQuantity();
            case DAMAGE -> -form.getQuantity();
            case CORRECTION -> form.getDirection() == StockAdjustForm.Direction.INCREASE
                    ? form.getQuantity() : -form.getQuantity();
            case SALE, VOID_RESTORE -> throw new BusinessRuleException(
                    "This adjustment type is reserved for sales.");
        };

        int newStock = product.getStockQuantity() + delta;
        if (newStock < 0) {
            throw new BusinessRuleException(
                    "Adjustment would leave " + product.getName() + " with negative stock (current: "
                            + product.getStockQuantity() + ").");
        }
        product.setStockQuantity(newStock);
        movements.save(new StockMovement(product, form.getType(), delta, newStock, "MANUAL", user, form.getNote()));
        return product;
    }

    /** System-side movement record (sale, void restore). Must be called within the caller's transaction. */
    @Transactional
    public void record(Product product, MovementType type, int delta, String reference, User user, String note) {
        movements.save(new StockMovement(product, type, delta, product.getStockQuantity(), reference, user, note));
    }
}
