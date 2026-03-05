CREATE TABLE IF NOT EXISTS o_schema_guard (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    note VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO o_schema_guard (note)
VALUES ('order-service schema initialized') AS new
ON DUPLICATE KEY UPDATE note = new.note;
