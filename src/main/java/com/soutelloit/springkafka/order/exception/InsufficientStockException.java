package com.soutelloit.springkafka.order.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String product, int requested, int available) {
        super("Insufficient stock for '" + product + "': requested " + requested
                + ", available " + available);
    }
}
