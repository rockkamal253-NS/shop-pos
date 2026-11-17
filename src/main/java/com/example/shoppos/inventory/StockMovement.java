package com.example.shoppos.inventory;

import com.example.shoppos.product.Product;
import com.example.shoppos.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Immutable audit record of a stock change. Written in the same transaction
 * as the change itself, so history always matches reality.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType type;

    /** Signed change in stock quantity: +received, -sold/damaged. */
    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** Cross-reference, e.g. "SALE #42" or "VOID #42". */
    @Column(nullable = false, length = 30)
    private String reference;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StockMovement() {
    }

    public StockMovement(Product product, MovementType type, int quantityDelta,
                         int stockAfter, String reference, User user, String note) {
        this.product = product;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.stockAfter = stockAfter;
        this.reference = reference;
        this.user = user;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public int getStockAfter() {
        return stockAfter;
    }

    public String getReference() {
        return reference;
    }

    public User getUser() {
        return user;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
