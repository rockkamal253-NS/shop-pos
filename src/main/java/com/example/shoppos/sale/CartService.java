package com.example.shoppos.sale;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Session cart operations and the computed view used by the POS screen. */
@Service
public class CartService {

    public static final String SESSION_KEY = "posCart";
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    private static final int MAX_RESULTS = 24;

    private final ProductRepository products;
    private final int maxDiscountPercent;

    @Autowired
    public CartService(ProductRepository products,
                       @Value("${app.max-cashier-discount-percent:20}") int maxDiscountPercent) {
        this.products = products;
        this.maxDiscountPercent = maxDiscountPercent;
    }

    public CartService(ProductRepository products) {
        this(products, 20);
    }

    public Cart getCart(HttpSession session) {
        Cart cart = (Cart) session.getAttribute(SESSION_KEY);
        if (cart == null) {
            cart = new Cart();
            session.setAttribute(SESSION_KEY, cart);
        }
        return cart;
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    /** Product grid for the register: newest first match, limited to a page. */
    @Transactional(readOnly = true)
    public List<Product> searchResults(String query) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        return products.search(q, PageRequest.of(0, MAX_RESULTS, Sort.by(Sort.Direction.ASC, "name"))).getContent();
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByBarcodeOrSku(String term) {
        String t = term == null ? "" : term.trim();
        if (t.isEmpty()) {
            return Optional.empty();
        }
        return products.findByBarcode(t).or(() -> products.findBySkuIgnoreCase(t));
    }

    /** Adds one unit; returns a human message for the toast. Caps at available stock. */
    @Transactional(readOnly = true)
    public String add(HttpSession session, Long productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new BusinessRuleException("Product not found."));
        if (!product.isActive()) {
            throw new BusinessRuleException("\"" + product.getName() + "\" is inactive.");
        }
        if (product.isOutOfStock()) {
            throw new BusinessRuleException("\"" + product.getName() + "\" is out of stock.");
        }
        Cart cart = getCart(session);
        int current = cart.getItems().getOrDefault(productId, 0);
        if (current >= product.getStockQuantity()) {
            return "Only " + product.getStockQuantity() + " in stock — cart quantity not increased.";
        }
        cart.getItems().put(productId, current + 1);
        return "Added " + product.getName();
    }

    /** Sets an absolute quantity (0 removes). Caps at available stock. Returns a message or null. */
    @Transactional(readOnly = true)
    public String setQuantity(HttpSession session, Long productId, int requestedQty) {
        Cart cart = getCart(session);
        if (!cart.getItems().containsKey(productId)) {
            return null;
        }
        if (requestedQty <= 0) {
            cart.getItems().remove(productId);
            return "Item removed.";
        }
        Product product = products.findById(productId).orElse(null);
        if (product != null && product.getStockQuantity() <= 0) {
            cart.getItems().remove(productId);
            return "\"" + product.getName() + "\" is out of stock and was removed.";
        }
        int cap = product == null ? requestedQty : Math.min(requestedQty, product.getStockQuantity());
        if (product != null && requestedQty > product.getStockQuantity()) {
            cart.getItems().put(productId, cap);
            return "Only " + product.getStockQuantity() + " in stock.";
        }
        cart.getItems().put(productId, requestedQty);
        return null;
    }

    public void remove(HttpSession session, Long productId) {
        getCart(session).getItems().remove(productId);
    }

    public void setDiscount(HttpSession session, DiscountType type, BigDecimal value, boolean admin) {
        Cart cart = getCart(session);
        cart.setDiscountType(type == null ? DiscountType.NONE : type);
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        if (v.signum() < 0) {
            throw new BusinessRuleException("Discount cannot be negative.");
        }
        if (cart.getDiscountType() == DiscountType.AMOUNT && v.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("Amount is too large.");
        }
        if (cart.getDiscountType() == DiscountType.PERCENT) {
            if (v.compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessRuleException("Percentage discount cannot exceed 100%.");
            }
            if (!admin && v.compareTo(BigDecimal.valueOf(maxDiscountPercent)) > 0) {
                throw new BusinessRuleException("Discounts above " + maxDiscountPercent + "% need an admin.");
            }
        }
        cart.setDiscountValue(v.setScale(2, RoundingMode.HALF_UP));
    }

    /** Backwards-compatible overload: assumes non-admin (cashier). */
    public void setDiscount(HttpSession session, DiscountType type, BigDecimal value) {
        setDiscount(session, type, value, false);
    }

    public void setPaymentMethod(HttpSession session, PaymentMethod method) {
        Cart cart = getCart(session);
        cart.setPaymentMethod(method == null ? PaymentMethod.CASH : method);
        if (cart.getPaymentMethod() == PaymentMethod.CARD) {
            cart.setCashTendered(null);
        }
    }

    public void setCashTendered(HttpSession session, BigDecimal amount) {
        BigDecimal v = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
        if (v != null) {
            if (v.signum() < 0) {
                throw new BusinessRuleException("Cash tendered cannot be negative.");
            }
            if (v.compareTo(MAX_AMOUNT) > 0) {
                throw new BusinessRuleException("Amount is too large.");
            }
        }
        getCart(session).setCashTendered(v);
    }

    /** Loads products for the cart and computes all totals. Read-only. */
    @Transactional(readOnly = true)
    public CartView view(HttpSession session) {
        Cart cart = getCart(session);
        List<CartLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : cart.getItems().entrySet()) {
            Product product = products.findById(entry.getKey()).orElse(null);
            if (product == null) {
                continue; // product was deleted while in cart — skip it
            }
            int qty = entry.getValue();
            BigDecimal lineTotal = SalePricing.lineTotal(product.getSellPrice(), qty);
            lines.add(new CartLine(product, qty, product.getSellPrice(), lineTotal));
            subtotal = subtotal.add(lineTotal);
        }

        SalePricing.Totals totals = SalePricing.compute(subtotal, cart.getDiscountType(),
                cart.getDiscountValue(), cart.getPaymentMethod() == PaymentMethod.CASH ? cart.getCashTendered() : null);

        return new CartView(lines, totals.subtotal(), cart.getDiscountType(), cart.getDiscountValue(),
                totals.discountAmount(), totals.total(), cart.getPaymentMethod(), cart.getCashTendered(),
                totals.changeDue());
    }

    public record CartLine(Product product, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record CartView(List<CartLine> lines,
                           BigDecimal subtotal,
                           DiscountType discountType,
                           BigDecimal discountValue,
                           BigDecimal discountAmount,
                           BigDecimal total,
                           PaymentMethod paymentMethod,
                           BigDecimal cashTendered,
                           BigDecimal changeDue) {
    }
}
