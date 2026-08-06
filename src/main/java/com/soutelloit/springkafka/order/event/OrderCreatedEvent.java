package com.soutelloit.springkafka.order.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.soutelloit.springkafka.order.domain.OrderEntity;

public record OrderCreatedEvent(
        String orderRef,
        String customerId,
        String product,
        int quantity,
        BigDecimal amount,
        Instant createdAt) {

    public static OrderCreatedEvent from(OrderEntity order) {
        return new OrderCreatedEvent(
                order.getOrderRef(),
                order.getCustomerId(),
                order.getProduct(),
                order.getQuantity(),
                order.getAmount(),
                order.getCreatedAt());
    }
}
