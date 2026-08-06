package com.soutelloit.springkafka.order.exception;

/** Could not acquire the in-JVM ReentrantLock for a product within the configured wait. */
public class LockTimeoutException extends RuntimeException {

    public LockTimeoutException(String key, long millis) {
        super("Timed out after " + millis + "ms waiting for the lock on '" + key + "'");
    }
}
