CREATE TABLE IF NOT EXISTS u_user_follow (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    follower_user_id BIGINT NOT NULL,
    followed_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_u_user_follow_pair (follower_user_id, followed_user_id),
    KEY idx_u_user_follow_follower_created (follower_user_id, created_at, id),
    KEY idx_u_user_follow_followed_created (followed_user_id, created_at, id),
    CONSTRAINT fk_u_user_follow_follower_user_id FOREIGN KEY (follower_user_id) REFERENCES u_user(id),
    CONSTRAINT fk_u_user_follow_followed_user_id FOREIGN KEY (followed_user_id) REFERENCES u_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
