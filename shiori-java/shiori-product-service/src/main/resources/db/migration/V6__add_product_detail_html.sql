ALTER TABLE p_product
    ADD COLUMN detail_html MEDIUMTEXT NULL COMMENT '商品富文本详情HTML' AFTER description;
