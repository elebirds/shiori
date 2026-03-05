DROP TABLE IF EXISTS u_user_follow;
DROP TABLE IF EXISTS u_user;

CREATE TABLE u_user (
    id BIGINT PRIMARY KEY,
    user_no VARCHAR(32) NOT NULL,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(255),
    bio VARCHAR(280),
    is_deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE u_user_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_user_id BIGINT NOT NULL,
    followed_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_u_user_follow_pair UNIQUE (follower_user_id, followed_user_id)
);
