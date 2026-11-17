package com.example.shoppos.product;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.DuplicateValueException;
import com.example.shoppos.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryRepository.CategoryWithCount> listWithCounts() {
        return categories.findAllWithCounts();
    }

    @Transactional(readOnly = true)
    public List<Category> listAll() {
        return categories.findAll();
    }

    @Transactional
    public Category create(CategoryForm form) {
        if (categories.existsByNameIgnoreCase(form.getName())) {
            throw new DuplicateValueException("name", "A category with this name already exists.");
        }
        return categories.save(new Category(form.getName().trim()));
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found."));
    }

    @Transactional
    public Category update(Long id, CategoryForm form) {
        if (categories.existsByNameIgnoreCaseAndIdNot(form.getName(), id)) {
            throw new DuplicateValueException("name", "A category with this name already exists.");
        }
        Category category = get(id);
        category.setName(form.getName().trim());
        return categories.save(category);
    }

    @Transactional
    public void delete(Long id) {
        if (products.existsByCategoryId(id)) {
            throw new BusinessRuleException("This category still has products. Move them to another category first.");
        }
        Category category = get(id);
        categories.delete(category);
    }
}
