package com.soutelloit.springkafka.order.exception;

/** An identical Idempotency-Key is already being processed and has not finished yet. */
public class DuplicateRequestInFlightException extends RuntimeException {

    public DuplicateRequestInFlightException(String idempotencyKey) {
        super("A request with Idempotency-Key '" + idempotencyKey + "' is still in flight");
    }
}
