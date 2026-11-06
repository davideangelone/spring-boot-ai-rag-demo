CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY,
    customer_name VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE NOT NULL,
    CONSTRAINT chk_orders_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'SHIPPED', 'CANCELLED'))
);
