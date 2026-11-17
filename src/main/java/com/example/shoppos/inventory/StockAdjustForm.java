package com.example.shoppos.inventory;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Manual stock adjustment form: receive stock, correction, damage. */
public class StockAdjustForm {

    public enum Direction { INCREASE, DECREASE }

    @NotNull(message = "Select a product.")
    private Long productId;

    @NotNull(message = "Choose an adjustment type.")
    private MovementType type;

    private Direction direction;

    @NotNull(message = "Enter a quantity.")
    @Min(value = 1, message = "Quantity must be at least 1.")
    @Max(value = 999999, message = "Quantity is too large.")
    private Integer quantity;

    @NotBlank(message = "A reason is required.")
    @Size(max = 255, message = "Reason must be at most 255 characters.")
    private String note;

    @AssertTrue(message = "Choose increase or decrease for a correction.")
    public boolean isDirectionValid() {
        return type != MovementType.CORRECTION || direction != null;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public MovementType getType() {
        return type;
    }

    public void setType(MovementType type) {
        this.type = type;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
