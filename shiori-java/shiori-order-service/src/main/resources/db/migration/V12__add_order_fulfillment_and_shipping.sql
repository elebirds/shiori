ALTER TABLE o_order
    ADD COLUMN allow_meetup TINYINT NOT NULL DEFAULT 1 AFTER chat_listing_id,
    ADD COLUMN allow_delivery TINYINT NOT NULL DEFAULT 0 AFTER allow_meetup,
    ADD COLUMN fulfillment_mode VARCHAR(16) NULL AFTER allow_delivery,
    ADD COLUMN shipping_address_id BIGINT NULL AFTER fulfillment_mode,
    ADD COLUMN shipping_receiver_name VARCHAR(32) NULL AFTER shipping_address_id,
    ADD COLUMN shipping_receiver_phone VARCHAR(20) NULL AFTER shipping_receiver_name,
    ADD COLUMN shipping_province VARCHAR(32) NULL AFTER shipping_receiver_phone,
    ADD COLUMN shipping_city VARCHAR(32) NULL AFTER shipping_province,
    ADD COLUMN shipping_district VARCHAR(32) NULL AFTER shipping_city,
    ADD COLUMN shipping_detail_address VARCHAR(128) NULL AFTER shipping_district;

CREATE INDEX idx_o_order_fulfillment_status
    ON o_order(fulfillment_mode, status, id);
