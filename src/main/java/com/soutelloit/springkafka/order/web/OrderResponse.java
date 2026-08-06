package com.soutelloit.springkafka.order.web;

import java.math.BigDecimal;
import java.time.Instant;

import com.soutelloit.springkafka.order.domain.OrderEntity;

public record OrderResponse(
        String orderRef,
        String customerId,
        String product,
        int quantity,
        BigDecimal amount,
        String status,
        Instant createdAt,
        /** true when this response replays a previously created order (idempotent hit). */
        boolean replayed) {

    public static OrderResponse of(OrderEntity order, boolean replayed) {
        return new OrderResponse(
                order.getOrderRef(),
                order.getCustomerId(),
                order.getProduct(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus().name(),
                order.getCreatedAt(),
                replayed);
    }
}
