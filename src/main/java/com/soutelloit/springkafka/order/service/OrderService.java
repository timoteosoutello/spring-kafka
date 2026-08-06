package com.soutelloit.springkafka.order.service;

import java.util.List;

import com.soutelloit.springkafka.order.domain.OrderEntity;
import com.soutelloit.springkafka.order.event.OrderCreatedEvent;
import com.soutelloit.springkafka.order.exception.DuplicateRequestInFlightException;
import com.soutelloit.springkafka.order.exception.OrderNotFoundException;
import com.soutelloit.springkafka.order.repository.OrderRepository;
import com.soutelloit.springkafka.order.web.CreateOrderRequest;
import com.soutelloit.springkafka.order.web.OrderDetailResponse;
import com.soutelloit.springkafka.order.web.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates order creation. Deliberately <b>not</b> {@code @Transactional}:
 * it coordinates Redis, an in-JVM lock and Kafka around a database transaction
 * that lives in {@link OrderTransactionalService}.
 *
 * <p>Order of operations, and why:
 * <pre>
 *   1. Redis SET NX on the Idempotency-Key   - cheapest possible duplicate rejection
 *   2. ReentrantLock on the product          - serialise same-product work in this JVM
 *   3. TX: SELECT ... FOR UPDATE + INSERT    - the real cross-instance guarantee
 *   4. commit, release ReentrantLock
 *   5. Redis key -> order ref                - later replays answered without touching PG
 *   6. publish to Kafka                      - only after the data is durable
 * </pre>
 *
 * Steps 2 and 3 are nested that way on purpose: the lock must wrap the transaction,
 * never the other way round, or it is released before the commit becomes visible.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final IdempotencyService idempotency;
    private final ProductLockRegistry productLocks;
    private final OrderTransactionalService orderTx;
    private final OrderEventPublisher publisher;
    private final OrderRepository orders;

    public OrderService(IdempotencyService idempotency,
                        ProductLockRegistry productLocks,
                        OrderTransactionalService orderTx,
                        OrderEventPublisher publisher,
                        OrderRepository orders) {
        this.idempotency = idempotency;
        this.productLocks = productLocks;
        this.orderTx = orderTx;
        this.publisher = publisher;
        this.orders = orders;
    }

    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {

        // 1. Idempotency gate.
        IdempotencyService.Claim claim = idempotency.claim(idempotencyKey);

        if (claim instanceof IdempotencyService.Claim.AlreadyCompleted completed) {
            log.info("Replaying order {} for idempotency key {}",
                    completed.orderRef(), idempotencyKey);
            return OrderResponse.of(orderTx.requireByOrderRef(completed.orderRef()), true);
        }
        if (claim instanceof IdempotencyService.Claim.InFlight) {
            throw new DuplicateRequestInFlightException(idempotencyKey);
        }

        try {
            // 2 + 3. In-JVM lock wrapping the database transaction.
            OrderEntity order = productLocks.withLock(
                    request.product(), () -> orderTx.createOrder(idempotencyKey, request));

            // 5. Record the outcome so replays are cheap.
            idempotency.complete(idempotencyKey, order.getOrderRef());

            // 6. Only now, after commit.
            publisher.publishOrderCreated(OrderCreatedEvent.from(order));

            log.info("Created order {} ({} x{})",
                    order.getOrderRef(), order.getProduct(), order.getQuantity());
            return OrderResponse.of(order, false);

        } catch (DataIntegrityViolationException e) {
            // Redis lost the key (flushed, evicted, restarted) but PostgreSQL's
            // uk_orders_idempotency_key caught the duplicate. Recover by returning
            // the order the first request already created.
            return orders.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> {
                        log.warn("Redis missed idempotency key {}; recovered order {} from PG",
                                idempotencyKey, existing.getOrderRef());
                        idempotency.complete(idempotencyKey, existing.getOrderRef());
                        return OrderResponse.of(existing, true);
                    })
                    .orElseThrow(() -> {
                        idempotency.release(idempotencyKey);
                        return e;
                    });

        } catch (RuntimeException e) {
            // Free the key so the client can retry the same request.
            idempotency.release(idempotencyKey);
            throw e;
        }
    }

    /**
     * Named entity graph: the order and its stock row come back in one SELECT, so
     * {@link OrderDetailResponse} can read the association after the transaction has
     * closed. Swap in {@code orders.findByOrderRef(...)} and this method throws
     * {@code LazyInitializationException} instead.
     */
    public OrderDetailResponse findByRef(String orderRef) {
        return orders.findWithStockByOrderRef(orderRef)
                .map(OrderDetailResponse::of)
                .orElseThrow(() -> new OrderNotFoundException(orderRef));
    }

    /**
     * Ad-hoc entity graph on the derived query: 1 SELECT for 20 orders + their stock
     * rows, instead of the 21 a lazy association would produce.
     */
    public List<OrderDetailResponse> findRecentByCustomer(String customerId) {
        return orders.findTop20ByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderDetailResponse::of)
                .toList();
    }
}
