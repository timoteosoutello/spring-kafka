package com.soutelloit.springkafka.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderRef) {
        super("No order with ref " + orderRef);
    }
}
