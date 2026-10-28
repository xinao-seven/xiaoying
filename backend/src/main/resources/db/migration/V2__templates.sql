CREATE TABLE templates (
    id                     VARCHAR(64)  NOT NULL,
    title                  VARCHAR(128) NOT NULL,
    category               VARCHAR(64)  NOT NULL,
    cover_url              VARCHAR(255) NOT NULL,
    description            VARCHAR(512) NULL,
    required_image_count   INT          NOT NULL,
    accepted_mime_types    VARCHAR(255) NOT NULL,
    max_file_size_bytes    BIGINT       NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order             INT          NOT NULL DEFAULT 0,
    created_at             DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_templates_category (category),
    KEY idx_templates_enabled_sort (enabled, sort_order),
    CONSTRAINT ck_templates_required_count CHECK (required_image_count > 0),
    CONSTRAINT ck_templates_max_size CHECK (max_file_size_bytes > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;