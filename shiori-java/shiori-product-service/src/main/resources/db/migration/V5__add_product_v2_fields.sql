ALTER TABLE p_product
    ADD COLUMN category_code VARCHAR(64) NULL COMMENT '商品分类编码' AFTER cover_object_key,
    ADD COLUMN condition_level VARCHAR(32) NULL COMMENT '成色等级编码' AFTER category_code,
    ADD COLUMN trade_mode VARCHAR(32) NULL COMMENT '交易方式编码' AFTER condition_level,
    ADD COLUMN campus_code VARCHAR(64) NULL COMMENT '校区编码' AFTER trade_mode;

CREATE INDEX idx_p_product_v2_filter
    ON p_product(status, category_code, condition_level, trade_mode, campus_code, created_at);
