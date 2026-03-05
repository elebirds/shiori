ALTER TABLE o_order
    ADD COLUMN payment_mode VARCHAR(32) NOT NULL DEFAULT 'SIMULATED' AFTER payment_no;

CREATE INDEX idx_o_order_payment_mode_status
    ON o_order(payment_mode, status);
