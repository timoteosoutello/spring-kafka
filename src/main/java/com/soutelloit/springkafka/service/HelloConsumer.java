package com.soutelloit.springkafka.service;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class HelloConsumer {

    private static final Logger log = LoggerFactory.getLogger(HelloConsumer.class);

    private final AtomicReference<String> lastMessage = new AtomicReference<>();

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String message) {
        log.info("Consumed: {}", message);
        lastMessage.set(message);
    }

    public String lastMessage() {
        return lastMessage.get();
    }
}
