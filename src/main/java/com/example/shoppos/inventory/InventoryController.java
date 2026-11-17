package com.example.shoppos.inventory;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.product.Product;
import com.example.shoppos.product.ProductRepository;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private static final int PAGE_SIZE = 25;

    private final InventoryService inventory;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final CurrentUserService currentUser;

    public InventoryController(InventoryService inventory, ProductRepository products,
                               StockMovementRepository movements, CurrentUserService currentUser) {
        this.inventory = inventory;
        this.products = products;
        this.movements = movements;
        this.currentUser = currentUser;
    }

    @GetMapping("/adjust")
    public String adjustForm(@RequestParam(required = false) Long productId, Model model) {
        model.addAttribute("form", new StockAdjustForm());
        model.addAttribute("products", products.findAll());
        model.addAttribute("selected", productId != null ? products.findById(productId).orElse(null) : null);
        if (productId != null) {
            StockAdjustForm form = new StockAdjustForm();
            form.setProductId(productId);
            model.addAttribute("form", form);
        }
        return "inventory/adjust";
    }

    @PostMapping("/adjust")
    public String adjust(@Valid @ModelAttribute("form") StockAdjustForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal UserPrincipal principal,
                         Model model,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("products", products.findAll());
            model.addAttribute("selected", form.getProductId() != null ? products.findById(form.getProductId()).orElse(null) : null);
            return "inventory/adjust";
        }
        try {
            Product product = inventory.adjust(form, currentUser.getCurrent(principal));
            ra.addFlashAttribute("toastSuccess", "Stock adjusted for \"" + product.getName()
                    + "\". New quantity: " + product.getStockQuantity() + ".");
            return "redirect:/inventory/movements?productId=" + product.getId();
        } catch (BusinessRuleException e) {
            model.addAttribute("adjustError", e.getMessage());
            model.addAttribute("products", products.findAll());
            model.addAttribute("selected", form.getProductId() != null ? products.findById(form.getProductId()).orElse(null) : null);
            return "inventory/adjust";
        }
    }

    @GetMapping("/movements")
    public String movements(@RequestParam Long productId,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new com.example.shoppos.common.web.NotFoundException("Product not found."));
        Page<StockMovement> result = movements.findByProductIdOrderByCreatedAtDesc(productId,
                PageRequest.of(page, PAGE_SIZE));
        model.addAttribute("product", product);
        model.addAttribute("page", result);
        return "inventory/movements";
    }

}
