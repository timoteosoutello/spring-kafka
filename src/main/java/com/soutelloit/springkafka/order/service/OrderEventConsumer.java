package com.soutelloit.springkafka.order.service;

import com.soutelloit.springkafka.order.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes the order-events topic. Stands in for a downstream service.
 *
 * <p>Uses Jackson 3's {@link JsonMapper} - see {@link OrderEventPublisher} for why
 * this is not {@code com.fasterxml.jackson.databind.ObjectMapper}.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final JsonMapper jsonMapper;

    public OrderEventConsumer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${app.kafka.order-topic}", groupId = "order-events-group")
    public void onOrderCreated(String payload) {
        try {
            OrderCreatedEvent event = jsonMapper.readValue(payload, OrderCreatedEvent.class);
            log.info("Order event consumed: {} x{} {} for {}",
                    event.orderRef(), event.quantity(), event.product(), event.customerId());
        } catch (RuntimeException e) {
            // Jackson 3 exceptions are unchecked. A real consumer would route the
            // poison message to a DLT via DefaultErrorHandler instead of swallowing it.
            log.error("Unparseable order event: {}", payload, e);
        }
    }
}
