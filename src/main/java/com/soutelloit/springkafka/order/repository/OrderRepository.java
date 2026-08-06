package com.soutelloit.springkafka.order.repository;

import java.util.List;
import java.util.Optional;

import com.soutelloit.springkafka.order.domain.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Four flavours of fetching, so the difference is visible side by side.
 *
 * <p>Reminder on the two graph types:
 * <ul>
 *   <li><b>FETCH</b> (the default) - attributes named in the graph are {@code EAGER},
 *       and <em>everything not named is treated as LAZY</em>, overriding the mapping.</li>
 *   <li><b>LOAD</b> - attributes named in the graph are {@code EAGER}, and everything
 *       else keeps whatever the entity mapping says.</li>
 * </ul>
 * With a single association the two behave identically; the distinction starts to
 * matter once an entity has several, some of them mapped {@code EAGER}.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /**
     * (1) No graph. {@code productStock} stays an uninitialised proxy, so this is the
     * cheapest read - one SELECT against {@code orders} only. Safe as long as nothing
     * downstream touches the association after the transaction ends.
     */
    Optional<OrderEntity> findByOrderRef(String orderRef);

    /**
     * (2) <b>Named</b> graph, declared with {@code @NamedEntityGraph} on the entity.
     * Emits a single SELECT with a LEFT JOIN onto {@code product_stock}, so the
     * returned entity is safe to read outside the transaction.
     *
     * <p>Prefer this form when the same fetch plan is reused across repositories -
     * the plan is defined once, next to the mapping it describes.
     */
    @EntityGraph(value = "OrderEntity.withProductStock", type = EntityGraphType.FETCH)
    Optional<OrderEntity> findWithStockByOrderRef(String orderRef);

    /**
     * (3) <b>Ad-hoc</b> graph via {@code attributePaths} - no entity-level declaration
     * needed. This is the N+1 killer: without it, mapping 20 orders to DTOs that read
     * {@code order.getProductStock()} fires 1 + 20 queries. With it, exactly one.
     *
     * <p>{@code LOAD} here to show the alternative: {@code productStock} is forced
     * EAGER, and any other association would keep its mapped fetch type rather than
     * being silently downgraded to LAZY.
     */
    @EntityGraph(attributePaths = "productStock", type = EntityGraphType.LOAD)
    List<OrderEntity> findTop20ByCustomerIdOrderByCreatedAtDesc(String customerId);

    /**
     * (4) Graph applied to an explicit {@code @Query} rather than a derived one -
     * they compose fine. Used on the Redis-miss recovery path, which reports stock
     * back to the caller.
     */
    @EntityGraph(attributePaths = "productStock")
    @Query("select o from OrderEntity o where o.idempotencyKey = :idempotencyKey")
    Optional<OrderEntity> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
