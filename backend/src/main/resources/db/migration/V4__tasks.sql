CREATE TABLE tasks (
    id                 VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    template_id        VARCHAR(64)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    stage              VARCHAR(32)  NULL,
    failed_stage       VARCHAR(32)  NULL,
    error_code         VARCHAR(64)  NULL,
    error_message      VARCHAR(512) NULL,
    result_url         VARCHAR(255) NULL,
    result_width       INT          NULL,
    result_height      INT          NULL,
    params_json        JSON         NULL,
    planned_fail_stage VARCHAR(32)  NULL,
    version            INT          NOT NULL DEFAULT 0,
    created_at         DATETIME(3)  NOT NULL,
    started_at         DATETIME(3)  NULL,
    finished_at        DATETIME(3)  NULL,
    updated_at         DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tasks_user_created (user_id, created_at),
    KEY idx_tasks_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_materials (
    task_id     VARCHAR(36) NOT NULL,
    material_id VARCHAR(36) NOT NULL,
    slot_index  INT         NOT NULL,
    PRIMARY KEY (task_id, material_id),
    UNIQUE KEY uk_task_materials_slot (task_id, slot_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;