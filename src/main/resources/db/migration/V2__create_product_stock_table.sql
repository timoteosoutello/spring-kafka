-- Stock levels. This is the row OrderService takes a PESSIMISTIC_WRITE lock on
-- (SELECT ... FOR UPDATE) so two concurrent orders cannot oversell the same product.
CREATE TABLE product_stock (
    id         BIGSERIAL    PRIMARY KEY,
    product    VARCHAR(120) NOT NULL,
    available  INTEGER      NOT NULL,
    reserved   INTEGER      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_product_stock_product  UNIQUE (product),
    CONSTRAINT ck_product_stock_available CHECK (available >= 0),
    CONSTRAINT ck_product_stock_reserved  CHECK (reserved  >= 0)
);

COMMENT ON TABLE product_stock IS 'Per-product stock; locked FOR UPDATE during order creation';

INSERT INTO product_stock (product, available) VALUES
    ('widget',  100),
    ('gadget',   25),
    ('gizmo',     3);
