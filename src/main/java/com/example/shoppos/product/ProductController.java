package com.example.shoppos.product;

import com.example.shoppos.common.web.DuplicateValueException;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService products;
    private final CategoryService categories;
    private final CurrentUserService currentUser;

    public ProductController(ProductService products, CategoryService categories, CurrentUserService currentUser) {
        this.products = products;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Product> result = products.search(q, page);
        model.addAttribute("page", result);
        model.addAttribute("q", q);
        return "product/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ProductForm());
        model.addAttribute("categories", categories.listAll());
        model.addAttribute("isEdit", false);
        return "product/form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") ProductForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal UserPrincipal principal,
                         Model model,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("categories", categories.listAll());
            model.addAttribute("isEdit", false);
            return "product/form";
        }
        try {
            Product saved = products.create(form, currentUser.getCurrent(principal));
            ra.addFlashAttribute("toastSuccess", "Product \"" + saved.getName() + "\" created.");
            return "redirect:/products";
        } catch (DuplicateValueException e) {
            binding.rejectValue(e.getField(), "duplicate", e.getMessage());
            model.addAttribute("categories", categories.listAll());
            model.addAttribute("isEdit", false);
            return "product/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = products.get(id);
        ProductForm form = new ProductForm();
        form.setId(product.getId());
        form.setName(product.getName());
        form.setSku(product.getSku());
        form.setBarcode(product.getBarcode());
        form.setCategoryId(product.getCategory().getId());
        form.setCostPrice(product.getCostPrice());
        form.setSellPrice(product.getSellPrice());
        form.setStockQuantity(product.getStockQuantity());
        form.setLowStockThreshold(product.getLowStockThreshold());
        form.setActive(product.isActive());
        model.addAttribute("form", form);
        model.addAttribute("product", product);
        model.addAttribute("categories", categories.listAll());
        model.addAttribute("isEdit", true);
        return "product/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") ProductForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("product", products.get(id));
            model.addAttribute("categories", categories.listAll());
            model.addAttribute("isEdit", true);
            return "product/form";
        }
        try {
            Product saved = products.update(id, form);
            ra.addFlashAttribute("toastSuccess", "Product \"" + saved.getName() + "\" updated.");
            return "redirect:/products";
        } catch (DuplicateValueException e) {
            binding.rejectValue(e.getField(), "duplicate", e.getMessage());
            model.addAttribute("product", products.get(id));
            model.addAttribute("categories", categories.listAll());
            model.addAttribute("isEdit", true);
            return "product/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Product product = products.get(id);
        products.delete(id);
        ra.addFlashAttribute("toastSuccess", "Product \"" + product.getName() + "\" deleted.");
        return "redirect:/products";
    }

}
