package com.soutelloit.springkafka.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class HelloProducer {

    private static final Logger log = LoggerFactory.getLogger(HelloProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public HelloProducer(KafkaTemplate<String, String> kafkaTemplate,
                         @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void send(String message) {
        log.info("Producing to topic '{}': {}", topic, message);
        kafkaTemplate.send(topic, message);
    }
}
