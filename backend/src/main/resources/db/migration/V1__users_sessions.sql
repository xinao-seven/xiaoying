CREATE TABLE users (
    id            VARCHAR(36)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sessions (
    token_hash CHAR(64)    NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (token_hash),
    KEY idx_sessions_user (user_id),
    KEY idx_sessions_expires (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;