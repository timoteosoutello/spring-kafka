package com.soutelloit.springkafka.order.web;

import java.net.URI;
import java.util.List;

import com.soutelloit.springkafka.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Every binding annotation names its parameter explicitly. Eclipse's JDT compiler
 * does not emit {@code -parameters} by default, and without it Spring cannot infer
 * these names at runtime.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates an order. The {@code Idempotency-Key} header is mandatory: retrying
     * with the same key returns the original order instead of creating a second one.
     *
     * <p>201 on first creation, 200 when replaying a previous result.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriBuilder) {

        OrderResponse response = orderService.createOrder(idempotencyKey, request);

        if (response.replayed()) {
            return ResponseEntity.ok(response);
        }
        URI location = uriBuilder.path("/orders/{orderRef}")
                .buildAndExpand(response.orderRef())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /** Loaded through the named entity graph, so the response includes stock. */
    @GetMapping("/{orderRef}")
    public OrderDetailResponse get(@PathVariable(name = "orderRef") String orderRef) {
        return orderService.findByRef(orderRef);
    }

    /**
     * Most recent 20 orders for a customer, loaded through an ad-hoc entity graph -
     * one query for the whole page rather than one per order.
     */
    @GetMapping
    public List<OrderDetailResponse> listByCustomer(
            @RequestParam(name = "customerId") String customerId) {
        return orderService.findRecentByCustomer(customerId);
    }
}
