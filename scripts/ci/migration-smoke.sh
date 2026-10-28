#!/usr/bin/env bash
# 迁移冒烟：在已执行 Flyway 的库上校验种子、表结构与“条件更新认领”语义。
# CI 使用 MySQL service + mysql:8.4 客户端容器；本地可用同一脚本连 compose 的 mysql。
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-xiaoying}"
DB_PASSWORD="${DB_PASSWORD:-xiaoying}"
DB_NAME="${DB_NAME:-xiaoying}"

export MYSQL_PWD="$DB_PASSWORD"

mysql_cmd=(mysql --protocol=TCP -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -N -B)

query() {
  "${mysql_cmd[@]}" -e "$1"
}

expect() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL ${label}: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
  echo "PASS ${label}=${actual}"
}

expect_at_least() {
  local label="$1"
  local minimum="$2"
  local actual="$3"
  if (( actual < minimum )); then
    echo "FAIL ${label}: expected >= ${minimum}, got '${actual}'" >&2
    exit 1
  fi
  echo "PASS ${label}=${actual}"
}

users=$(query "SELECT COUNT(*) FROM users;")
expect users 2 "$users"

templates=$(query "SELECT COUNT(*) FROM templates WHERE enabled = TRUE;")
expect_at_least templates 6 "$templates"

config_rows=$(query "SELECT COUNT(*) FROM simulation_config WHERE id = 1;")
expect simulation_config 1 "$config_rows"

schema_tables=$(query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('tasks','task_materials','task_events');")
expect task_tables 3 "$schema_tables"

# 认领语义：只有 PENDING 能被条件更新认领一次；重复认领影响 0 行。
query "DELETE FROM tasks WHERE id = 'tsk_ci_claim';" >/dev/null
query "INSERT INTO tasks (id, user_id, template_id, status, version, created_at, updated_at)
       VALUES ('tsk_ci_claim', 'usr_01M607ZMC7QETA6BERJWY9P6NB', 'demo-postcard', 'PENDING', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));" >/dev/null

claimed=$(query "UPDATE tasks SET status = 'PROCESSING', stage = 'MATERIAL_CHECK', version = version + 1
                 WHERE id = 'tsk_ci_claim' AND status = 'PENDING';
                 SELECT ROW_COUNT();")
expect claim_first 1 "$claimed"

duplicate_claim=$(query "UPDATE tasks SET status = 'PROCESSING'
                         WHERE id = 'tsk_ci_claim' AND status = 'PENDING';
                         SELECT ROW_COUNT();")
expect claim_second 0 "$duplicate_claim"

claim_version=$(query "SELECT version FROM tasks WHERE id = 'tsk_ci_claim';")
expect claim_version 2 "$claim_version"

# 终态不可回退：SUCCEEDED 之后，带 status = 'PROCESSING' 条件的更新不影响任何行。
query "UPDATE tasks SET status = 'SUCCEEDED', stage = 'DONE', result_url = '/api/tasks/tsk_ci_claim/result',
       version = version + 1, finished_at = UTC_TIMESTAMP(3)
       WHERE id = 'tsk_ci_claim' AND status = 'PROCESSING';" >/dev/null
regression=$(query "UPDATE tasks SET status = 'PROCESSING'
                    WHERE id = 'tsk_ci_claim' AND status = 'PROCESSING';
                    SELECT ROW_COUNT();")
expect terminal_regression_blocked 0 "$regression"

final_status=$(query "SELECT CONCAT(status, '/', stage) FROM tasks WHERE id = 'tsk_ci_claim';")
expect final_status "SUCCEEDED/DONE" "$final_status"

query "DELETE FROM tasks WHERE id = 'tsk_ci_claim';" >/dev/null
echo "migration-smoke: OK"