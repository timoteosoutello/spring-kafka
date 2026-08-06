package com.soutelloit.springkafka.order.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.soutelloit.springkafka.order.exception.LockTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One {@link ReentrantLock} per product, held for the duration of an order.
 *
 * <p><b>What this buys you:</b> within a single JVM, concurrent requests for the
 * same product queue up here instead of at the database, so they never contend for
 * the row lock. That keeps PostgreSQL transactions short and avoids lock-wait
 * pile-ups under a burst of same-product traffic.
 *
 * <p><b>What it does NOT buy you:</b> anything across instances. Run two copies of
 * this app and each has its own registry. Correctness comes from the
 * {@code SELECT ... FOR UPDATE} in {@code ProductStockRepository}; this is a
 * latency optimisation layered on top. For a cluster-wide lock you would reach for
 * Redis (Redisson / SET NX with a fencing token) instead.
 *
 * <p>The lock is acquired <em>outside</em> the transaction and released after it
 * commits. Locking inside the transactional method would release the lock before
 * the commit is visible, which defeats the purpose.
 */
@Component
public class ProductLockRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProductLockRegistry.class);

    /**
     * Unbounded by design: the product catalogue is small and bounded. If keys were
     * user-supplied and unbounded, this map would need eviction (e.g. Guava striped
     * locks or a size-capped cache) to avoid growing without limit.
     */
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final Duration acquireTimeout;

    public ProductLockRegistry(@Value("${app.lock.acquire-timeout}") Duration acquireTimeout) {
        this.acquireTimeout = acquireTimeout;
    }

    public <T> T withLock(String key, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(true));
        boolean acquired;
        try {
            acquired = lock.tryLock(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockTimeoutException(key, acquireTimeout.toMillis());
        }
        if (!acquired) {
            throw new LockTimeoutException(key, acquireTimeout.toMillis());
        }
        try {
            log.debug("Holding in-JVM lock for '{}' (queued: {})", key, lock.getQueueLength());
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
