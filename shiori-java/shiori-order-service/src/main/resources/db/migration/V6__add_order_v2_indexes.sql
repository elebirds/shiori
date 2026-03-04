CREATE INDEX idx_o_order_seller_status_created
    ON o_order(seller_user_id, status, created_at);

CREATE INDEX idx_o_order_seller_status_updated
    ON o_order(seller_user_id, status, updated_at);

CREATE INDEX idx_o_order_status_audit_timeline
    ON o_order_status_audit_log(target_order_no, id);
