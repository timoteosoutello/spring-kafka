package com.soutelloit.springkafka.order.service;

import com.soutelloit.springkafka.order.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes order events to Kafka, keyed by order ref so all events for one order
 * land on the same partition and stay ordered.
 *
 * <p><b>Jackson 3, not 2.</b> Spring Boot 4 ships Jackson 3: the package moved from
 * {@code com.fasterxml.jackson} to {@code tools.jackson}, the auto-configured bean is
 * {@link JsonMapper} rather than {@code ObjectMapper}, and every Jackson exception is
 * now unchecked ({@link JacksonException} extends {@code RuntimeException}). Adding
 * {@code com.fasterxml.jackson.core:jackson-databind} would put a second, unmanaged
 * JSON stack on the classpath - use the Jackson 3 types instead.
 *
 * <p>Called <em>after</em> the transaction commits. That ordering matters: publish
 * inside the transaction and a later rollback leaves a phantom event describing an
 * order that does not exist. The trade-off is the opposite failure - a commit
 * followed by a publish failure loses the event. Production systems close that gap
 * with the transactional outbox pattern (write the event to an outbox table in the
 * same transaction, relay it separately); this sample keeps it simple and just logs.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               JsonMapper jsonMapper,
                               @Value("${app.kafka.order-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        String payload;
        try {
            payload = jsonMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            // A serialisation failure is a bug in the event type, not a transient
            // fault - fail loudly rather than silently dropping the event.
            throw new IllegalStateException("Could not serialise " + event, e);
        }

        try {
            kafkaTemplate.send(topic, event.orderRef(), payload);
            log.info("Published order-created for {}", event.orderRef());
        } catch (RuntimeException e) {
            // The order is already committed - do not fail the HTTP response over
            // a broker hiccup. An outbox would make this recoverable.
            log.error("Failed to publish order-created for {}", event.orderRef(), e);
        }
    }
}
