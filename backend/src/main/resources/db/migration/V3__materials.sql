CREATE TABLE materials (
    id            VARCHAR(36)  NOT NULL,
    user_id       VARCHAR(36)  NOT NULL,
    original_name VARCHAR(255) NULL,
    storage_key   VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    width         INT          NULL,
    height        INT          NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_materials_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;