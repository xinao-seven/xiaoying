CREATE TABLE task_events (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    task_id    VARCHAR(36)  NOT NULL,
    stage      VARCHAR(32)  NOT NULL,
    event_type VARCHAR(32)  NOT NULL,
    message    VARCHAR(512) NULL,
    created_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_task_events_task_id (task_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;