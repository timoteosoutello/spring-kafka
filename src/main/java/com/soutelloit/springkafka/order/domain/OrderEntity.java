package com.soutelloit.springkafka.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Maps the {@code orders} table created by V1/V3.
 *
 * <p>Named {@code OrderEntity} rather than {@code Order} because {@code ORDER} is
 * awkward in SQL and {@code Order} clashes with several framework types.
 *
 * <p><b>Entity graphs.</b> {@link #productStock} is {@code LAZY}, which is the right
 * default: most reads never need it, and {@code EAGER} would fire an extra SELECT on
 * every single load. But {@code spring.jpa.open-in-view} is {@code false} here, so the
 * persistence context closes when the transaction ends - touching a lazy association
 * afterwards throws {@code LazyInitializationException}.
 *
 * <p>An entity graph resolves that tension: it says "for <em>this</em> query, fetch
 * these associations in the same SELECT". The named graph below is applied in
 * {@code OrderRepository} via {@code @EntityGraph("OrderEntity.withProductStock")};
 * see that class for the ad-hoc ({@code attributePaths}) form and for the
 * FETCH-vs-LOAD distinction.
 *
 * <p>Deeper structures use {@code @NamedSubgraph} to keep walking the object tree
 * (e.g. order → productStock → warehouse). This model is only one level deep, so a
 * plain {@code @NamedAttributeNode} is enough.
 */
@Entity
@Table(name = "orders")
@NamedEntityGraph(
        name = "OrderEntity.withProductStock",
        attributeNodes = @NamedAttributeNode("productStock"))
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_ref", nullable = false, length = 64, updatable = false)
    private String orderRef;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "product", nullable = false, length = 120)
    private String product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_stock_id")
    private ProductStock productStock;

    /** Optimistic locking. Complements - does not replace - the pessimistic lock on stock. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected OrderEntity() {
        // for JPA
    }

    public OrderEntity(String idempotencyKey,
                       String customerId,
                       String product,
                       int quantity,
                       BigDecimal amount,
                       ProductStock productStock) {
        this.orderRef = "ORD-" + UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
        this.amount = amount;
        this.productStock = productStock;
        this.status = OrderStatus.NEW;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public ProductStock getProductStock() {
        return productStock;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
