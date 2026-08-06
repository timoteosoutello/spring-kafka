package com.soutelloit.springkafka.order.repository;

import java.util.Optional;

import com.soutelloit.springkafka.order.domain.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProduct(String product);

    /**
     * Pessimistic write lock: emits {@code SELECT ... FOR UPDATE}, so a second
     * transaction asking for the same product blocks here until the first commits
     * or rolls back. This is what actually prevents overselling across JVMs -
     * the ReentrantLock in ProductLockRegistry only helps within one instance.
     *
     * <p>How long a blocked transaction waits is bounded by the PostgreSQL
     * {@code lock_timeout} set on every connection (see spring.datasource.hikari
     * .connection-init-sql). Exceeding it surfaces as a
     * {@link org.springframework.dao.CannotAcquireLockException}.
     *
     * <p>Must be called inside a transaction - a pessimistic lock outside one is
     * released immediately and buys you nothing.
     *
     * <p>Deliberately <b>no</b> {@code @EntityGraph} here. A graph turns the query
     * into a join, and {@code FOR UPDATE} over a join asks the database to lock the
     * joined rows too - locking more than you meant to, and on some databases failing
     * outright. Keep locking queries narrow; fetch associations in a separate read.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ps from ProductStock ps where ps.product = :product")
    Optional<ProductStock> findByProductForUpdate(@Param("product") String product);
}
