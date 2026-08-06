package com.soutelloit.springkafka.order.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String product) {
        super("Unknown product '" + product + "'");
    }
}
