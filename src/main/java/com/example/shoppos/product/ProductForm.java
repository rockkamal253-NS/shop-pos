package com.example.shoppos.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Create/edit product form. Stock is only set on create; edits go through Stock adjustments. */
public class ProductForm {

    private Long id;

    @NotBlank(message = "Product name is required.")
    @Size(max = 150, message = "Name must be at most 150 characters.")
    private String name;

    @NotBlank(message = "SKU is required.")
    @Size(max = 50, message = "SKU must be at most 50 characters.")
    private String sku;

    @NotBlank(message = "Barcode is required.")
    @Size(max = 50, message = "Barcode must be at most 50 characters.")
    private String barcode;

    @NotNull(message = "Choose a category.")
    private Long categoryId;

    @NotNull(message = "Cost price is required.")
    @DecimalMin(value = "0.00", message = "Cost price cannot be negative.")
    @Digits(integer = 10, fraction = 2, message = "Use up to 10 digits and 2 decimals.")
    private BigDecimal costPrice;

    @NotNull(message = "Sell price is required.")
    @DecimalMin(value = "0.00", message = "Sell price cannot be negative.")
    @Digits(integer = 10, fraction = 2, message = "Use up to 10 digits and 2 decimals.")
    private BigDecimal sellPrice;

    @NotNull(message = "Initial stock is required.")
    @Min(value = 0, message = "Stock cannot be negative.")
    @Max(value = 999999, message = "Stock is too large.")
    private Integer stockQuantity;

    @NotNull(message = "Low-stock threshold is required.")
    @Min(value = 0, message = "Threshold cannot be negative.")
    @Max(value = 999999, message = "Threshold is too large.")
    private Integer lowStockThreshold;

    private boolean active = true;

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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(Integer lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
