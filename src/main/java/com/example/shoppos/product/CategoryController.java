package com.example.shoppos.product;

import com.example.shoppos.common.web.DuplicateValueException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categories.listWithCounts());
        return "category/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new CategoryForm());
        return "category/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CategoryForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return "category/form";
        }
        try {
            Category saved = categories.create(form);
            ra.addFlashAttribute("toastSuccess", "Category \"" + saved.getName() + "\" created.");
            return "redirect:/categories";
        } catch (DuplicateValueException e) {
            binding.rejectValue(e.getField(), "duplicate", e.getMessage());
            return "category/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Category category = categories.get(id);
        CategoryForm form = new CategoryForm();
        form.setId(category.getId());
        form.setName(category.getName());
        model.addAttribute("form", form);
        return "category/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") CategoryForm form,
                         BindingResult binding,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return "category/form";
        }
        try {
            Category saved = categories.update(id, form);
            ra.addFlashAttribute("toastSuccess", "Category \"" + saved.getName() + "\" updated.");
            return "redirect:/categories";
        } catch (DuplicateValueException e) {
            binding.rejectValue(e.getField(), "duplicate", e.getMessage());
            return "category/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        categories.delete(id);
        ra.addFlashAttribute("toastSuccess", "Category deleted.");
        return "redirect:/categories";
    }
}
