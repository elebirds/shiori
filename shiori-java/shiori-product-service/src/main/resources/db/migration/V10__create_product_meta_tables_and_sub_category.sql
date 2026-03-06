CREATE TABLE IF NOT EXISTS p_product_campus (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campus_code VARCHAR(64) NOT NULL,
    campus_name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=ENABLED,0=DISABLED',
    sort_order INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_product_campus_code (campus_code),
    KEY idx_p_product_campus_status_sort (status, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_product_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=ENABLED,0=DISABLED',
    sort_order INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_product_category_code (category_code),
    KEY idx_p_product_category_status_sort (status, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_product_sub_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_code VARCHAR(64) NOT NULL,
    sub_category_code VARCHAR(64) NOT NULL,
    sub_category_name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=ENABLED,0=DISABLED',
    sort_order INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_product_sub_category_code (sub_category_code),
    KEY idx_p_product_sub_category_category (category_code, status, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE p_product
    ADD COLUMN sub_category_code VARCHAR(64) NULL COMMENT '商品子分类编码' AFTER category_code;

UPDATE p_product
SET category_code = UPPER(TRIM(category_code))
WHERE category_code IS NOT NULL;

UPDATE p_product
SET category_code = 'OTHER'
WHERE category_code IS NULL OR TRIM(category_code) = '';

INSERT INTO p_product_category (category_code, category_name, status, sort_order, is_deleted)
VALUES ('TEXTBOOK', '教材', 1, 10, 0),
       ('EXAM_MATERIAL', '考试资料', 1, 20, 0),
       ('NOTE', '笔记', 1, 30, 0),
       ('OTHER', '其他', 1, 999, 0)
AS new
ON DUPLICATE KEY UPDATE category_name = new.category_name,
                        status = new.status,
                        is_deleted = 0,
                        updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO p_product_category (category_code, category_name, status, sort_order, is_deleted)
SELECT DISTINCT p.category_code,
                p.category_code,
                1,
                500,
                0
FROM p_product p
WHERE p.category_code IS NOT NULL
  AND TRIM(p.category_code) <> ''
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO p_product_sub_category (category_code, sub_category_code, sub_category_name, status, sort_order, is_deleted)
SELECT c.category_code,
       LEFT(CONCAT(c.category_code, '_UNSPEC'), 64),
       '未细分',
       1,
       999,
       0
FROM p_product_category c
WHERE c.is_deleted = 0
ON DUPLICATE KEY UPDATE sub_category_name = VALUES(sub_category_name),
                        status = VALUES(status),
                        is_deleted = 0,
                        updated_at = CURRENT_TIMESTAMP(3);

UPDATE p_product
SET sub_category_code = LEFT(CONCAT(category_code, '_UNSPEC'), 64)
WHERE sub_category_code IS NULL OR TRIM(sub_category_code) = '';

INSERT INTO p_product_campus (campus_code, campus_name, status, sort_order, is_deleted)
VALUES ('UNKNOWN_CAMPUS', '未配置校区', 1, 999, 0)
AS new
ON DUPLICATE KEY UPDATE campus_name = new.campus_name,
                        status = new.status,
                        is_deleted = 0,
                        updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO p_product_campus (campus_code, campus_name, status, sort_order, is_deleted)
SELECT DISTINCT TRIM(p.campus_code),
                TRIM(p.campus_code),
                1,
                500,
                0
FROM p_product p
WHERE p.campus_code IS NOT NULL
  AND TRIM(p.campus_code) <> ''
  AND CHAR_LENGTH(TRIM(p.campus_code)) <= 64
  AND TRIM(p.campus_code) REGEXP '^[A-Za-z0-9_-]+$'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

UPDATE p_product
SET campus_code = TRIM(campus_code)
WHERE campus_code IS NOT NULL;

UPDATE p_product
SET campus_code = 'UNKNOWN_CAMPUS'
WHERE campus_code IS NULL
   OR TRIM(campus_code) = ''
   OR CHAR_LENGTH(TRIM(campus_code)) > 64
   OR TRIM(campus_code) NOT REGEXP '^[A-Za-z0-9_-]+$';

DROP INDEX idx_p_product_v2_filter ON p_product;

CREATE INDEX idx_p_product_v2_filter
    ON p_product(status, category_code, sub_category_code, condition_level, trade_mode, campus_code, created_at);
