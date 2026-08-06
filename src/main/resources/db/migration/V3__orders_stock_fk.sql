-- Orders reference the stock row they drew down, so the ER diagram (and any
-- reporting query) can walk from an order to its product.
ALTER TABLE orders
    ADD COLUMN product_stock_id BIGINT;

UPDATE orders o
   SET product_stock_id = ps.id
  FROM product_stock ps
 WHERE ps.product = o.product;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_product_stock
        FOREIGN KEY (product_stock_id) REFERENCES product_stock (id);

CREATE INDEX idx_orders_product_stock_id ON orders (product_stock_id);
