package com.example.shoppos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryForm {

    private Long id;

    @NotBlank(message = "Category name is required.")
    @Size(max = 100, message = "Name must be at most 100 characters.")
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
