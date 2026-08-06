package com.soutelloit.springkafka.order.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed idempotency keys.
 *
 * <p>Two-phase, so a crash mid-request cannot permanently block the key:
 * <ol>
 *   <li>{@link #claim(String)} does an atomic SET NX with a short TTL. Winning the
 *       SET means this request owns the key; losing means someone else does.</li>
 *   <li>{@link #complete(String, String)} overwrites the marker with the resulting
 *       order ref under a long TTL, so later replays can be answered directly.</li>
 *   <li>{@link #release(String)} deletes the marker when processing failed, letting
 *       the client retry the same key.</li>
 * </ol>
 *
 * <p>Redis here is an optimisation, not the source of truth. The
 * {@code uk_orders_idempotency_key} unique constraint in PostgreSQL is what
 * guarantees correctness if Redis is flushed or unreachable.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String KEY_PREFIX = "idem:order:";
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private final StringRedisTemplate redis;
    private final Duration inProgressTtl;
    private final Duration completedTtl;

    public IdempotencyService(StringRedisTemplate redis,
                              @Value("${app.idempotency.in-progress-ttl}") Duration inProgressTtl,
                              @Value("${app.idempotency.completed-ttl}") Duration completedTtl) {
        this.redis = redis;
        this.inProgressTtl = inProgressTtl;
        this.completedTtl = completedTtl;
    }

    /** Outcome of trying to claim a key. */
    public sealed interface Claim {

        /** Nobody held the key - this request may proceed. */
        record Acquired() implements Claim {
        }

        /** A previous request with this key already produced an order. */
        record AlreadyCompleted(String orderRef) implements Claim {
        }

        /** Another request with this key is running right now. */
        record InFlight() implements Claim {
        }
    }

    public Claim claim(String idempotencyKey) {
        String key = KEY_PREFIX + idempotencyKey;

        Boolean acquired = redis.opsForValue().setIfAbsent(key, IN_PROGRESS, inProgressTtl);
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Claimed idempotency key {}", idempotencyKey);
            return new Claim.Acquired();
        }

        String current = redis.opsForValue().get(key);
        if (current == null) {
            // Expired between SET NX and GET. Treat as in-flight rather than
            // racing again; the client can retry.
            return new Claim.InFlight();
        }
        return IN_PROGRESS.equals(current)
                ? new Claim.InFlight()
                : new Claim.AlreadyCompleted(current);
    }

    public void complete(String idempotencyKey, String orderRef) {
        redis.opsForValue().set(KEY_PREFIX + idempotencyKey, orderRef, completedTtl);
        log.debug("Idempotency key {} -> {}", idempotencyKey, orderRef);
    }

    public void release(String idempotencyKey) {
        redis.delete(KEY_PREFIX + idempotencyKey);
        log.debug("Released idempotency key {}", idempotencyKey);
    }
}
