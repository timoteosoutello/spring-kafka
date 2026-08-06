package com.soutelloit.springkafka.order.web;

import java.math.BigDecimal;
import java.time.Instant;

import com.soutelloit.springkafka.order.domain.OrderEntity;
import com.soutelloit.springkafka.order.domain.ProductStock;

/**
 * Order plus a snapshot of the stock row it drew down.
 *
 * <p>Building this reads {@code order.getProductStock()}, which happens <em>after</em>
 * the transaction has closed ({@code open-in-view: false}). It only works because the
 * query that loaded the order applied an entity graph. Map one of these from an order
 * fetched without a graph and you get {@code LazyInitializationException} - which is
 * exactly the failure entity graphs exist to prevent.
 */
public record OrderDetailResponse(
        String orderRef,
        String customerId,
        String product,
        int quantity,
        BigDecimal amount,
        String status,
        Instant createdAt,
        StockSnapshot stock) {

    public record StockSnapshot(String product, int available, int reserved) {

        static StockSnapshot of(ProductStock stock) {
            return new StockSnapshot(stock.getProduct(), stock.getAvailable(), stock.getReserved());
        }
    }

    public static OrderDetailResponse of(OrderEntity order) {
        ProductStock stock = order.getProductStock();
        return new OrderDetailResponse(
                order.getOrderRef(),
                order.getCustomerId(),
                order.getProduct(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus().name(),
                order.getCreatedAt(),
                stock == null ? null : StockSnapshot.of(stock));
    }
}
