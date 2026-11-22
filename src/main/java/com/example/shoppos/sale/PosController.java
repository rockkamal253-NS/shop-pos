package com.example.shoppos.sale;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.Toasts;
import com.example.shoppos.product.Product;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The register (POS). All interactions are HTMX partial swaps against the
 * session-backed cart; the page itself is rendered once by {@link #register}.
 */
@Controller
@RequestMapping("/pos")
public class PosController {

    private final CartService carts;
    private final SaleService sales;
    private final CurrentUserService currentUser;

    public PosController(CartService carts, SaleService sales, CurrentUserService currentUser) {
        this.carts = carts;
        this.sales = sales;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String register(HttpSession session, Model model) {
        fillModel(model, session);
        model.addAttribute("results", carts.searchResults(""));
        return "pos/pos";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("results", carts.searchResults(q));
        model.addAttribute("q", q);
        return "pos/fragments :: results";
    }

    /** Enter key on the scan box: exact barcode/SKU match goes straight to the cart. */
    @PostMapping("/scan")
    public String scan(@RequestParam(required = false) String q,
                       HttpSession session,
                       Model model,
                       HttpServletResponse response) {
        String term = q == null ? "" : q.trim();
        if (!term.isEmpty()) {
            Optional<Product> exact = carts.findByBarcodeOrSku(term);
            if (exact.isPresent()) {
                try {
                    String message = carts.add(session, exact.get().getId());
                    Toasts.send(response, message, "info");
                } catch (BusinessRuleException e) {
                    Toasts.send(response, e.getMessage(), "error");
                }
            } else {
                Toasts.send(response, "No exact match for \"" + term + "\" — use the search below.", "info");
            }
        }
        fillModel(model, session);
        // Keep the full product grid visible after a scan; the toast confirms the add.
        model.addAttribute("results", carts.searchResults(null));
        return "pos/fragments :: scan";
    }

    @PostMapping("/cart/add/{id}")
    public String addToCart(@PathVariable Long id, HttpSession session, Model model, HttpServletResponse response) {
        try {
            String message = carts.add(session, id);
            Toasts.send(response, message, "info");
        } catch (BusinessRuleException e) {
            Toasts.send(response, e.getMessage(), "error");
        }
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    @PostMapping("/cart/qty/{id}")
    public String setQty(@PathVariable Long id,
                         @RequestParam(defaultValue = "1") int qty,
                         HttpSession session,
                         Model model,
                         HttpServletResponse response) {
        String message = carts.setQuantity(session, id, qty);
        if (message != null) {
            Toasts.send(response, message, "info");
        }
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    @PostMapping("/cart/remove/{id}")
    public String removeFromCart(@PathVariable Long id, HttpSession session, Model model) {
        carts.remove(session, id);
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    @PostMapping("/cart/clear")
    public String clearCart(HttpSession session, RedirectAttributes ra) {
        carts.clear(session);
        ra.addFlashAttribute("toastInfo", "Cart cleared.");
        return "redirect:/pos";
    }

    @PostMapping("/cart/discount")
    public String setDiscount(@RequestParam DiscountType discountType,
                              @RequestParam(required = false) BigDecimal discountValue,
                              HttpSession session,
                              Model model,
                              HttpServletResponse response,
                              @AuthenticationPrincipal UserPrincipal principal) {
        try {
            boolean admin = principal != null && principal.getRole() == Role.ADMIN;
            carts.setDiscount(session, discountType, discountValue, admin);
        } catch (BusinessRuleException e) {
            Toasts.send(response, e.getMessage(), "error");
        }
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    @PostMapping("/cart/payment/{method}")
    public String setPayment(@PathVariable PaymentMethod method, HttpSession session, Model model) {
        carts.setPaymentMethod(session, method);
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    @PostMapping("/cart/tender")
    public String setTender(@RequestParam(required = false) BigDecimal amount,
                            HttpSession session,
                            Model model,
                            HttpServletResponse response) {
        try {
            carts.setCashTendered(session, amount);
        } catch (BusinessRuleException e) {
            Toasts.send(response, e.getMessage(), "error");
        }
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    /** Completes the sale in one transaction, then HTMX-redirects to the printable receipt. */
    @PostMapping("/checkout")
    public String checkout(HttpSession session,
                           Model model,
                           HttpServletResponse response,
                           @AuthenticationPrincipal UserPrincipal principal) {
        try {
            Cart cart = carts.getCart(session);
            User cashier = currentUser.getCurrent(principal);
            Sale sale = sales.completeSale(cart, cashier);
            carts.clear(session);
            response.setHeader("HX-Redirect", "/sales/" + sale.getId() + "/receipt?print=1");
            fillModel(model, session);
            return "pos/fragments :: cartInner";
        } catch (InsufficientStockException e) {
            model.addAttribute("checkoutError", "Sale rejected — not enough stock for: " + String.join("; ", e.getIssues()));
            Toasts.send(response, "Sale rejected — not enough stock.", "error");
        } catch (OptimisticLockingFailureException e) {
            model.addAttribute("checkoutError", "Stock just changed. Please review the cart and try again.");
            Toasts.send(response, "Stock just changed. Please review the cart and try again.", "error");
        } catch (BusinessRuleException e) {
            model.addAttribute("checkoutError", e.getMessage());
            Toasts.send(response, e.getMessage(), "error");
        }
        fillModel(model, session);
        return "pos/fragments :: cartInner";
    }

    private void fillModel(Model model, HttpSession session) {
        model.addAttribute("cart", carts.view(session));
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("paymentMethods", PaymentMethod.values());
    }
}
