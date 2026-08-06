package com.soutelloit.springkafka.order.service;

import com.soutelloit.springkafka.order.domain.OrderEntity;
import com.soutelloit.springkafka.order.domain.ProductStock;
import com.soutelloit.springkafka.order.exception.InsufficientStockException;
import com.soutelloit.springkafka.order.exception.OrderNotFoundException;
import com.soutelloit.springkafka.order.exception.ProductNotFoundException;
import com.soutelloit.springkafka.order.repository.OrderRepository;
import com.soutelloit.springkafka.order.repository.ProductStockRepository;
import com.soutelloit.springkafka.order.web.CreateOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of order creation, deliberately kept in its own bean.
 *
 * <p>Spring's {@code @Transactional} works through a proxy, so a call from
 * {@code OrderService} to a {@code @Transactional} method <em>on itself</em> would
 * bypass the proxy entirely and run with no transaction at all - and a pessimistic
 * lock without a transaction is released immediately, silently doing nothing.
 * Splitting the transactional work into a separate bean is what makes the lock real.
 */
@Service
public class OrderTransactionalService {

    private static final Logger log = LoggerFactory.getLogger(OrderTransactionalService.class);

    private final OrderRepository orders;
    private final ProductStockRepository stocks;

    public OrderTransactionalService(OrderRepository orders, ProductStockRepository stocks) {
        this.orders = orders;
        this.stocks = stocks;
    }

    /**
     * Reserves stock and writes the order in one transaction.
     *
     * <p>{@code REQUIRES_NEW} guarantees a fresh transaction even if a caller
     * already started one, so the row lock is taken and released on a predictable
     * boundary. Keep this method short: every statement here runs while the
     * {@code product_stock} row is locked against all other writers.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderEntity createOrder(String idempotencyKey, CreateOrderRequest request) {

        // SELECT ... FOR UPDATE - blocks concurrent writers of this product row.
        ProductStock stock = stocks.findByProductForUpdate(request.product())
                .orElseThrow(() -> new ProductNotFoundException(request.product()));

        log.debug("Locked stock row for '{}' (available={})", stock.getProduct(), stock.getAvailable());

        if (stock.getAvailable() < request.quantity()) {
            throw new InsufficientStockException(
                    request.product(), request.quantity(), stock.getAvailable());
        }
        stock.reserve(request.quantity());

        OrderEntity order = new OrderEntity(
                idempotencyKey,
                request.customerId(),
                request.product(),
                request.quantity(),
                request.amount(),
                stock);

        // saveAndFlush so a unique-constraint violation on idempotency_key surfaces
        // here, inside the try/catch in OrderService, rather than at commit time.
        return orders.saveAndFlush(order);
    }

    @Transactional(readOnly = true)
    public OrderEntity requireByOrderRef(String orderRef) {
        return orders.findByOrderRef(orderRef)
                .orElseThrow(() -> new OrderNotFoundException(orderRef));
    }
}
