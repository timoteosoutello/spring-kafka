package com.soutelloit.springkafka.order.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Stock level for one product.
 *
 * <p>This is the row {@code OrderTransactionalService} locks with
 * {@code PESSIMISTIC_WRITE} (SELECT ... FOR UPDATE) before decrementing, so two
 * concurrent orders for the same product cannot both read the same "available"
 * value and oversell.
 */
@Entity
@Table(name = "product_stock")
public class ProductStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product", nullable = false, length = 120)
    private String product;

    @Column(name = "available", nullable = false)
    private int available;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProductStock() {
        // for JPA
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void reserve(int quantity) {
        if (quantity > available) {
            throw new IllegalStateException("not enough stock for " + product);
        }
        this.available -= quantity;
        this.reserved += quantity;
    }

    public Long getId() {
        return id;
    }

    public String getProduct() {
        return product;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
