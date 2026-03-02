CREATE DATABASE IF NOT EXISTS `shiori_user`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS `shiori_product`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS `shiori_order`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON `shiori_user`.* TO 'shiori'@'%';
GRANT ALL PRIVILEGES ON `shiori_product`.* TO 'shiori'@'%';
GRANT ALL PRIVILEGES ON `shiori_order`.* TO 'shiori'@'%';
FLUSH PRIVILEGES;
