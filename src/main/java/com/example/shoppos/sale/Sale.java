package com.example.shoppos.sale;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed (or voided) sale. Product name and price are snapshotted on the
 * sale items so later product edits/deletions never distort old receipts.
 */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "cashier_id", nullable = false)
    private com.example.shoppos.user.User cashier;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(name = "cash_tendered", precision = 12, scale = 2)
    private BigDecimal cashTendered;

    @Column(name = "change_due", precision = 12, scale = 2)
    private BigDecimal changeDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "voided_by_id")
    private com.example.shoppos.user.User voidedBy;

    @OneToMany(mappedBy = "sale", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true)
    @BatchSize(size = 50)
    private List<SaleItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Sale() {
    }

    public Sale(com.example.shoppos.user.User cashier, BigDecimal subtotal,
                DiscountType discountType, BigDecimal discountValue, BigDecimal discountAmount,
                BigDecimal total, PaymentMethod paymentMethod,
                BigDecimal cashTendered, BigDecimal changeDue) {
        this.cashier = cashier;
        this.subtotal = subtotal;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountAmount = discountAmount;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.cashTendered = cashTendered;
        this.changeDue = changeDue;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }

    public boolean isVoided() {
        return status == SaleStatus.VOIDED;
    }

    /** Total number of units across all items (display helper for templates). */
    @jakarta.persistence.Transient
    public int getItemCount() {
        return items.stream().mapToInt(SaleItem::getQuantity).sum();
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public com.example.shoppos.user.User getCashier() {
        return cashier;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getCashTendered() {
        return cashTendered;
    }

    public BigDecimal getChangeDue() {
        return changeDue;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public void setStatus(SaleStatus status) {
        this.status = status;
    }

    public LocalDateTime getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(LocalDateTime voidedAt) {
        this.voidedAt = voidedAt;
    }

    public com.example.shoppos.user.User getVoidedBy() {
        return voidedBy;
    }

    public void setVoidedBy(com.example.shoppos.user.User voidedBy) {
        this.voidedBy = voidedBy;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
