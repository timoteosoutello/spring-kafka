package com.soutelloit.springkafka.order.domain;

/** Mirrors the ck_orders_status CHECK constraint in V1__create_orders_table.sql. */
public enum OrderStatus {
    NEW,
    CONFIRMED,
    CANCELLED
}
