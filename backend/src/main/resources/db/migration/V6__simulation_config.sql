CREATE TABLE simulation_config (
    id                      INT         NOT NULL,
    fail_stage              VARCHAR(32) NULL,
    fail_remaining          INT         NOT NULL DEFAULT 0,
    delay_material_check_ms INT         NOT NULL,
    delay_generating_ms     INT         NOT NULL,
    delay_assembling_ms     INT         NOT NULL,
    updated_at              DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_simulation_singleton CHECK (id = 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO simulation_config (id, fail_stage, fail_remaining,
                               delay_material_check_ms, delay_generating_ms, delay_assembling_ms,
                               updated_at)
VALUES (1, NULL, 0, 800, 2500, 800, UTC_TIMESTAMP(3));