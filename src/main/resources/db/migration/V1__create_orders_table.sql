-- Orders written by OrderService. One row per accepted order.
CREATE TABLE orders (
    id              BIGSERIAL      PRIMARY KEY,
    order_ref       VARCHAR(64)    NOT NULL,
    idempotency_key VARCHAR(128)   NOT NULL,
    customer_id     VARCHAR(64)    NOT NULL,
    product         VARCHAR(120)   NOT NULL,
    quantity        INTEGER        NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'NEW',
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_orders_order_ref       UNIQUE (order_ref),
    -- Redis is the fast path for idempotency; this constraint is the durable
    -- backstop for when Redis is flushed, evicted, or unavailable.
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_orders_quantity        CHECK (quantity > 0),
    CONSTRAINT ck_orders_amount          CHECK (amount >= 0),
    CONSTRAINT ck_orders_status          CHECK (status IN ('NEW', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_created_at  ON orders (created_at DESC);

COMMENT ON TABLE  orders                 IS 'Customer orders accepted by OrderService';
COMMENT ON COLUMN orders.order_ref       IS 'Public, client-facing identifier (ORD-<uuid>)';
COMMENT ON COLUMN orders.idempotency_key IS 'Value of the Idempotency-Key request header';
COMMENT ON COLUMN orders.version         IS 'JPA @Version - optimistic locking';
