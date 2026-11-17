package com.example.shoppos.product;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.DuplicateValueException;
import com.example.shoppos.common.web.NotFoundException;
import com.example.shoppos.inventory.InventoryService;
import com.example.shoppos.inventory.MovementType;
import com.example.shoppos.inventory.StockMovementRepository;
import com.example.shoppos.sale.SaleItemRepository;
import com.example.shoppos.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    public static final int PAGE_SIZE = 20;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryService inventory;
    private final SaleItemRepository saleItems;
    private final com.example.shoppos.inventory.StockMovementRepository movements;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          InventoryService inventory, SaleItemRepository saleItems,
                          StockMovementRepository movements) {
        this.products = products;
        this.categories = categories;
        this.inventory = inventory;
        this.saleItems = saleItems;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String query, int page) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        return products.search(q, PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "name")));
    }

    @Transactional
    public Product create(ProductForm form, User currentUser) {
        validateUniqueness(form, null);
        Category category = categories.findById(form.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found."));

        Product product = new Product(form.getName().trim(), form.getSku().trim(), form.getBarcode().trim(),
                category, form.getCostPrice(), form.getSellPrice(),
                form.getStockQuantity(), form.getLowStockThreshold(), form.isActive());
        product = products.save(product);

        if (product.getStockQuantity() > 0) {
            inventory.record(product, MovementType.RECEIVE, product.getStockQuantity(), "INITIAL",
                    currentUser, "Initial stock on product creation");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product not found."));
    }

    @Transactional
    public Product update(Long id, ProductForm form) {
        validateUniqueness(form, id);
        Product product = get(id);
        Category category = categories.findById(form.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found."));

        product.setName(form.getName().trim());
        product.setSku(form.getSku().trim());
        product.setBarcode(form.getBarcode().trim());
        product.setCategory(category);
        product.setCostPrice(form.getCostPrice());
        product.setSellPrice(form.getSellPrice());
        product.setLowStockThreshold(form.getLowStockThreshold());
        product.setActive(form.isActive());
        // Stock is intentionally NOT changed here — adjustments keep the movement history accurate.
        return products.save(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = get(id);
        if (saleItems.existsByProductId(id)) {
            throw new BusinessRuleException(
                    "\"" + product.getName() + "\" has recorded sales. Deactivate it instead of deleting.");
        }
        if (movements.existsByProductId(id)) {
            throw new BusinessRuleException(
                    "\"" + product.getName() + "\" has stock movement history. Deactivate it instead of deleting.");
        }
        products.delete(product);
        products.flush(); // force FK check inside this method so we can give a friendly error
    }

    private void validateUniqueness(ProductForm form, Long excludeId) {
        if (excludeId == null ? products.existsBySkuIgnoreCase(form.getSku())
                              : products.existsBySkuIgnoreCaseAndIdNot(form.getSku(), excludeId)) {
            throw new DuplicateValueException("sku", "This SKU is already used by another product.");
        }
        if (excludeId == null ? products.existsByBarcodeIgnoreCase(form.getBarcode())
                              : products.existsByBarcodeIgnoreCaseAndIdNot(form.getBarcode(), excludeId)) {
            throw new DuplicateValueException("barcode", "This barcode is already used by another product.");
        }
    }
}
