ALTER TABLE o_order
    ADD COLUMN biz_source VARCHAR(32) NULL AFTER paid_at,
    ADD COLUMN chat_conversation_id BIGINT NULL AFTER biz_source,
    ADD COLUMN chat_listing_id BIGINT NULL AFTER chat_conversation_id;

CREATE INDEX idx_o_order_chat_conversation
    ON o_order(chat_conversation_id, id);
