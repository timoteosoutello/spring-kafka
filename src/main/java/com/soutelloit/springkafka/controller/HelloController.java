package com.soutelloit.springkafka.controller;

import java.util.Map;

import com.soutelloit.springkafka.service.HelloConsumer;
import com.soutelloit.springkafka.service.HelloProducer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class HelloController {

    private final HelloProducer producer;
    private final HelloConsumer consumer;

    public HelloController(HelloProducer producer, HelloConsumer consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }

    @GetMapping
    public String hello() {
        return "Hello from Spring Boot 4 + Kafka";
    }

    /** Publishes a message to Kafka; the consumer picks it up asynchronously. */
    @PostMapping
    public Map<String, String> publish(@RequestParam(defaultValue = "hello world") String message) {
        producer.send(message);
        return Map.of("status", "sent", "message", message);
    }

    /** Last message seen by the @KafkaListener consumer. */
    @GetMapping("/last")
    public Map<String, String> last() {
        String last = consumer.lastMessage();
        return Map.of("lastConsumed", last == null ? "" : last);
    }
}
