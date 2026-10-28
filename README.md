# 小影素材站

浏览模板、上传素材、提交制作任务。任务由 Java 后台按「素材检查 → 模拟生成 → 结果整理」三阶段异步执行，前端可以继续浏览，并跟踪任务状态、阶段与结果图。

[![CI](https://github.com/OWNER/REPOSITORY/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPOSITORY/actions/workflows/ci.yml)

> 仓库尚未配置 GitHub 远端；推送后请把徽章与链接中的 `OWNER/REPOSITORY` 替换为实际地址。

- 当前进度：M0～M9 全部完成，交付快照 tag `v1.0-submission`。
- 本文档按题目「10.2 README 必须包含」的 16 项组织；对应关系见文末「README 16 项对照」。

## 1. 项目简介

用户登录后浏览 4 个分类、6 个模板，进入详情上传 1～3 张 JPEG/PNG，提交后立即拿到任务 id；后台串行执行三个阶段，前端每 2 秒轮询活跃任务，展示当前阶段、失败阶段与原因、结果大图。刷新页面、切换页面、关闭标签都不会中断后台任务；后端停机只影响状态刷新提示，不会把任务误标为失败。

技术职责边界：

- 模板、素材、任务、模拟控制全部由 Java（Spring Boot + MyBatis + MySQL）提供。
- 浏览器只访问 Next.js 的 `:3000`，`/api/*` 由 Next Route Handler 同源代理到 Java，Cookie 全程同源。
- 任务状态、阶段、失败与结果都由服务端持久化，前端不按计时器伪造状态。

## 2. 技术版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | 22 LTS（本地验证 24.18.0） | 前端运行时；镜像与 CI 固定 22 |
| Next.js | 15.5.28 | App Router、`output: "standalone"` |
| React / React DOM | 19.2.9 | |
| TypeScript | 5.9.3 | strict |
| Redux Toolkit / react-redux | 2.13.0 / 9.3.1 | tasks slice 按 id 归一化 |
| Vitest | 3.2.7 | 前端单测 |
| ESLint | 9.39.4 | flat config |
| Java | 21 | 构建与运行 |
| Spring Boot / Spring Framework | 3.5.16 / 6.2.19 | |
| MyBatis (starter) | 3.0.5 | |
| MySQL | 8.4 LTS | utf8mb4；驱动 9.7.0 |
| Flyway | 11.20.3 | 含 flyway-mysql |
| JUnit | 5.x（Boot 管理） | 后端单测 |
| Spring Security Crypto | 6.5.11 | 仅 BCrypt 哈希 |
| Docker 镜像 | `maven:3.9-eclipse-temurin-21`、`eclipse-temurin:21-jre-jammy`、`node:22-alpine`、`mysql:8.4` | 多阶段构建 |

实际解析版本以 `frontend/package-lock.json`、`backend/pom.xml` 为准。

## 3. 项目结构

```
.
├── backend/                     # Spring Boot 3.5 + MyBatis
│   ├── Dockerfile               # Maven 构建 → JRE 运行，非 root
│   ├── .dockerignore
│   ├── src/main/java/com/xiaoying/
│   │   ├── auth/                # 会话、Cookie、拦截器
│   │   ├── common/              # 错误码、统一异常、ID、traceId
│   │   ├── config/              # 应用配置、MVC 配置
│   │   ├── material/            # 素材上传与受控读取
│   │   ├── pipeline/            # 调度器、三阶段 worker、结果拼接、重启对账
│   │   ├── simulation/          # 模拟配置与开发接口
│   │   ├── storage/             # 本地卷存储
│   │   ├── task/                # 任务创建、查询、历史与结果
│   │   └── template/            # 模板分类/列表/详情
│   └── src/main/resources/db/migration/   # Flyway V1～V7
├── frontend/                    # Next.js 15 App Router
│   ├── Dockerfile               # standalone 产物 + 非 root 运行
│   ├── .dockerignore
│   ├── app/                     # 页面、布局、全局样式与 /api 同源代理
│   ├── components/              # Providers、顶栏
│   ├── features/                # 登录、模板、上传、提交、任务跟踪、历史
│   ├── store/                   # Redux Toolkit tasks slice
│   ├── lib/                     # API client、类型、上传、格式化
│   ├── public/demo/             # 自生成示例封面
│   ├── test/                    # Vitest setup、fixtures
│   ├── middleware.ts            # 无会话 Cookie 时跳登录
│   └── scripts/                 # 封面生成脚本
├── scripts/ci/                  # 迁移冒烟脚本
├── .github/workflows/ci.yml     # 三 job CI
├── docs/                        # 需求、执行计划、完成记录
├── docker-compose.yml           # 交付编排：mysql + backend + frontend
├── docker-compose.dev.yml       # 开发编排：后端跑在 Maven 镜像里
├── .env.example                 # 仅演示值
└── .gitattributes               # 脚本/SQL/YAML 强制 LF
```

## 4. 快速开始（交付镜像，推荐）

前置条件：Docker 与 Docker Compose v2.24+（`!reset/!override` 用于开发编排），可访问 Docker Hub 与 npm/Maven 依赖。

```bash
# 从空卷构建并启动三个服务
docker compose down -v
docker compose up --build -d

# 等待健康
docker compose ps
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/login   # 200
```

- 访问 `http://localhost:3000`，用 `demo / demo1234` 登录。
- MySQL 宿主机端口默认 `13306`（避免与本机 3306 冲突），容器内始终 `mysql:3306`。
- 若本机 3000 被占用：`FRONTEND_PORT=3100 docker compose up --build -d`，再访问 `:3100`。
- 只重建某个服务：`docker compose up -d --build backend`。
- 查看日志：`docker compose logs -f backend`、`docker compose logs -f frontend`。
- 停止并保留数据：`docker compose down`；清空数据库与素材：`docker compose down -v`。

首次构建会下载 npm/Maven 依赖，约 3～8 分钟；之后利用 Docker 层缓存会快很多。

### 开发方式（宿主机前端 + 容器后端）

```bash
# MySQL + 后端（源码 bind mount 进 Maven 镜像）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
curl -s http://127.0.0.1:8080/actuator/health

# 前端
cd frontend
npm ci
BACKEND_URL=http://127.0.0.1:8080 npm run dev   # http://localhost:3000
```

修改 Java 代码后：`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --force-recreate backend`。
需要同时启动前端交付镜像：在 dev 组合命令后加 `--profile full --build`。

### 构建与测试命令

```bash
# 前端
cd frontend
npm ci
npm run lint && npm run typecheck && npm test && npm run build

# 后端（本机 JDK 21）
cd backend && ./mvnw -B verify

# 后端（无本机 JDK，容器等价）
docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm --no-deps backend mvn -B verify
```

## 5. 前后端与数据库连接方式

```
浏览器 ──(:3000)──> Next.js ──(容器网络 http://backend:8080)──> Spring Boot ──(mysql:3306)──> MySQL 8.4
   │                    │                                          │
   └ Cookie 同源 ───────┘  /api/* Route Handler 透传 Cookie        └ /data 卷：uploads + results
```

- 浏览器只访问前端；`app/api/[...path]/route.ts` 把 `/api/*` 转发到 `BACKEND_URL`（交付编排默认 `http://backend:8080`，本机开发默认 `http://127.0.0.1:8080`）。
- 代理透传 `Cookie` 与多个 `Set-Cookie`，上传体用 `arrayBuffer` 转发；后端不可达时返回 502 JSON。
- 后端通过 `SPRING_DATASOURCE_URL` 连 MySQL；Flyway 启动时自动迁移。
- 素材与结果写入 `/data` 命名卷 `app-data`；MySQL 数据在 `mysql-data` 卷。重建数据库不会删除已上传文件。

## 6. 配置项

| 变量 | 默认值 | 作用 |
|---|---|---|
| `MYSQL_DATABASE` | `xiaoying` | 数据库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `xiaoying` / `xiaoying` | 应用账号（仅演示） |
| `MYSQL_ROOT_PASSWORD` | `root` | MySQL root（仅演示） |
| `MYSQL_PORT` | `13306` | MySQL 宿主机端口 |
| `BACKEND_PORT` | `8080` | 后端宿主机端口 |
| `FRONTEND_PORT` | `3000` | 前端宿主机端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/xiaoying?...` | 后端连接串 |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `xiaoying` / `xiaoying` | 后端数据库账号 |
| `APP_DATA_DIR` | `/data` | 素材与结果目录 |
| `APP_PIPELINE_CONCURRENCY` | `1` | 同时处理的任务数；串行演示用 1 |
| `APP_PIPELINE_POLL_INTERVAL_MS` | `1000` | 调度器扫描 PENDING 的间隔 |
| `APP_SIMULATION_DEV_API_ENABLED` | 应用默认 `false`；compose 演示 `true` | 是否暴露 `/api/dev/simulation` |
| `APP_UPLOAD_MAX_BYTES` | `10485760` | 单文件上限（10 MB） |
| `APP_UPLOAD_MAX_REQUEST_SIZE` | `11MB` | multipart 请求上限 |
| `BACKEND_URL` | `http://backend:8080` | 前端代理目标 |

`.env.example` 只有演示值；`.env` 已被 `.gitignore` 忽略，仓库不包含真实凭据。生产部署必须替换所有密码并设置 `APP_SIMULATION_DEV_API_ENABLED=false`。

## 7. 数据库初始化、迁移、种子与素材

- 迁移脚本：`backend/src/main/resources/db/migration/V1__users_sessions.sql` ～ `V7__seed_demo.sql`。
- 应用启动时自动执行 Flyway；也可在 `backend/` 下手动执行：

```bash
./mvnw -B org.flywaydb:flyway-maven-plugin:migrate
./mvnw -B org.flywaydb:flyway-maven-plugin:info
```

- 种子：2 个用户（`demo`、`alice`）、4 个分类、6 个模板、1 行 `simulation_config`；不预置任务。
- 重置全部数据：`docker compose down -v && docker compose up --build -d`。
- 素材准备：`frontend/public/demo/*.png` 为封面；上传素材由用户选择，落盘到 `{APP_DATA_DIR}/uploads/{userId}/{uuid}.{ext}`，数据库只存相对 key。
- 结果文件：`{APP_DATA_DIR}/results/{taskId}.png`，通过受控接口 `/api/tasks/{id}/result` 读取。
- 示例封面由 `frontend/scripts/generate-covers.mjs` 纯 Node + zlib 自绘，可重跑 `npm run covers`，不依赖第三方图片。

## 8. 主要接口与字段

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 否 | `{username,password}` → `{user}` + HttpOnly Cookie |
| POST | `/api/auth/logout` | 是 | 204 + 清 Cookie |
| GET | `/api/auth/me` | 是 | 当前用户 |
| GET | `/api/templates/categories` | 否 | `{items:[{name,count}]}` |
| GET | `/api/templates?category=` | 否 | `{items:[Template]}`，仅启用模板 |
| GET | `/api/templates/{id}` | 否 | Template 详情；不存在 404 |
| POST | `/api/materials` | 是 | multipart `file` → 201 `{id,url,mimeType,sizeBytes,width,height,createdAt}` |
| GET | `/api/materials/{id}/content` | 是 | 按归属返回图片；越权/不存在 404 |
| POST | `/api/tasks` | 是 | `{templateId,materialIds,params?}` → 201 Task，立即返回 |
| GET | `/api/tasks/{id}` | 是 | Task 详情（仅本人） |
| GET | `/api/tasks?page=&size=` | 是 | `{items,page,size,total}`，创建时间倒序 |
| GET | `/api/tasks/active` | 是 | `{items}`，当前用户非终态任务 |
| GET | `/api/tasks/{id}/result` | 是 | 成功结果 PNG，`Cache-Control: private, max-age=60` |
| GET | `/api/tasks/{id}/events` | 是 | `{items:[{stage,eventType,message,createdAt}]}`，按 id 升序 |
| GET / POST | `/api/dev/simulation` | 开发开关 | 查看/调整耗时、注入失败 |
| GET | `/actuator/health` | 否 | 健康检查 |

Task 字段：`id, templateId, templateTitle, status, stage, failedStage, errorCode, errorMessage, resultUrl, version, createdAt, startedAt, finishedAt`。

请求/响应示例：

```bash
# 登录并保存 Cookie
curl -s -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo1234"}' \
  http://127.0.0.1:8080/api/auth/login

# 创建任务
curl -s -b cookies.txt -H 'Content-Type: application/json' \
  -d '{"templateId":"demo-postcard","materialIds":["mat_xxx","mat_yyy"],"params":{"caption":"summer"}}' \
  http://127.0.0.1:8080/api/tasks

# 查询、历史、活跃、事件、结果
curl -s -b cookies.txt http://127.0.0.1:8080/api/tasks/tsk_xxx
curl -s -b cookies.txt 'http://127.0.0.1:8080/api/tasks?page=0&size=20'
curl -s -b cookies.txt http://127.0.0.1:8080/api/tasks/active
curl -s -b cookies.txt http://127.0.0.1:8080/api/tasks/tsk_xxx/events
curl -s -b cookies.txt -o result.png http://127.0.0.1:8080/api/tasks/tsk_xxx/result
```

错误体统一为 `{code, message, fieldErrors?, traceId}`；401/404/413/415/400/500 均返回 JSON。

## 9. 任务状态、阶段与后台执行

状态与阶段分离：

| 字段 | 取值 | 说明 |
|---|---|---|
| `status` | `PENDING / PROCESSING / SUCCEEDED / FAILED` | 任务生命周期 |
| `stage` | `MATERIAL_CHECK / GENERATING / ASSEMBLING / DONE` | 当前阶段；等待态为 `null` |
| `failedStage` / `errorCode` / `errorMessage` | 失败时写入 | 前端展示失败阶段与可读原因 |
| `version` | 单调递增 | 前端丢弃晚到的旧响应 |

执行方式（DB 队列 + 调度器）：

1. `POST /api/tasks` 只校验并插入 `PENDING`，事务提交后立即返回 201。
2. `@Scheduled` 每秒扫描 PENDING，用 `UPDATE ... WHERE status='PENDING'` 条件更新原子认领；并发度默认 1，其余保持 PENDING。
3. worker 每个阶段先写 `stage` + `version` 并提交，再等待模拟耗时，再执行工作；因此轮询能观察到中间阶段。
4. 失败停在该阶段，写入 `failed_stage/error_code/error_message`，不执行后续阶段；终态更新带状态条件，结构上不可回退。
5. `task_events` 记录 `STAGE_STARTED/STAGE_SUCCEEDED/STAGE_FAILED/TASK_SUCCEEDED`，用于追溯。
6. 查询接口只读：Mapper 只声明 SELECT，单测用写调用计数断言。

模拟控制（开发接口默认关闭；交付 compose 为演示开启）：

```bash
# 三阶段改为 3/8/3 秒，便于观察
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"delayMaterialCheckMs":3000,"delayGeneratingMs":8000,"delayAssemblingMs":3000}'

# 下一个任务在生成阶段失败一次
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' -d '{"failStage":"GENERATING","failNext":1}'

# 清空注入并恢复默认耗时
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStage":null,"delayMaterialCheckMs":800,"delayGeneratingMs":2500,"delayAssemblingMs":800}'
```

失败计划在创建任务时快照到 `tasks.planned_fail_stage`，之后修改配置不影响已创建任务。

## 10. 演示步骤

> 以下步骤在 `docker compose up --build -d` 后执行；若前端映射到 3100，请替换端口。

| # | 操作 | 预期 |
|---|---|---|
| 1 | `docker compose down -v && docker compose up --build -d` | mysql/backend/frontend 均 healthy；`/login` 200 |
| 2 | 登录 `demo / demo1234` | 进入模板列表，顶栏显示 `Demo User`；4 分类 6 模板 |
| 3 | 切换分类、点击“旅行明信片” | 列表随分类变化；详情显示 2 个槽位、JPEG/PNG、5 MB |
| 4 | 上传 2 张图 → 提交 | 立即出现“等待处理”卡片；阶段依次变化；结束后显示结果图与“预览大图” |
| 5 | 设置 3/8/3 秒耗时后新建任务 | 轮询能看到“素材检查 → 模拟生成 → 结果整理” |
| 6 | `failStage=GENERATING,failNext=1` 后提交 | 任务在“模拟生成”失败；阶段轨迹“结果整理”未执行；原因可读 |
| 7 | 连续提交 3 个长任务 | 串行时 1 处理中 + 2 等待；状态、阶段、结果互不串扰 |
| 8 | 处理中刷新页面 | 历史恢复；阶段继续更新，无需重新提交 |
| 9 | 提交后关闭标签，稍后重开 | 任务已完成或仍在处理；结果可见 |
| 10 | `docker compose stop backend` | 卡片保留最后状态，出现“状态刷新失败 / 重试”；任务不变为失败；`start` 后自动恢复 |
| 11 | demo 建任务 → 登出 → 登录 alice | alice 历史为空；直接访问 demo 任务/结果/事件均 404 |
| 12 | 375 px 宽 + 键盘操作 | 无横向滚动；Tab 焦点可见；按钮可用空格/回车激活 |

完整的手工用例结果见 `docs/status/test-report.md` G9；自动化用例见同文件 G8。

## 11. 用户身份与个人记录

- 种子账号：`demo / demo1234`（主演示）、`alice / alice1234`（隔离验证）；密码用 BCrypt 哈希存储。
- 会话：登录签发 32 字节随机令牌，数据库只存 SHA-256；Cookie `XIAOYING_SESSION` 为 `HttpOnly; SameSite=Lax; Path=/; Max-Age=604800`。
- 归属：素材、任务、结果、事件全部按 session 用户过滤；越权与不存在统一 404，避免泄露存在性。
- 前端：401 全局清空 Redux store 并跳登录；登录成功与登出都会 `resetAll()`，并在轮询 cleanup 中 abort 在途请求；顶栏在非 401 查询失败时保留最后一次用户信息。
- 演示隔离：demo 建任务后登出，登录 alice，历史为空；用 alice 的 Cookie 直接请求 demo 任务详情/结果/事件均返回 404 `TASK_NOT_FOUND`。

## 12. 重启恢复边界

- `PENDING`：重启后仍可被调度器重新认领。
- `PROCESSING`：启动对账标记为 `FAILED`，`errorCode=SERVER_RESTART`，`errorMessage=服务重启导致处理中断`，不实现断点续跑。
- 已完成/失败任务：保持不变；事件表保留已发生的阶段记录。
- 单实例假设：多实例会重复消费，未实现分布式锁。
- 前端在轮询失败时只写 `sync`，因此重启窗口内不会把任务误标为失败；恢复后自动继续。

## 13. 部署与回滚

- 服务：`mysql`（`mysql-data` 卷、healthcheck）→ `backend`（`app-data` 卷、`/actuator/health`）→ `frontend`（healthcheck `/login`）。
- 镜像：`xiaoying-backend:local`、`xiaoying-frontend:local`；后端与前端均以非 root 用户运行。
- 启动：`docker compose up --build -d`；检查：`docker compose ps`、`docker compose logs --tail=100 backend`。
- 回滚：

```bash
git checkout <上一个验收 tag>
docker compose up -d --build
```

- 迁移已前进且需要保留数据时，先备份卷再回滚：

```bash
docker run --rm -v xiaoying_mysql-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/db.tgz /data
docker compose down          # 不删卷
docker compose up -d --build
```

- 数据库不兼容时 `docker compose down -v` 重建；会清空数据库与素材，仅演示环境可接受。
- 生产注意：替换所有默认密码、关闭 `APP_SIMULATION_DEV_API_ENABLED`、在反向代理层加 HTTPS 与限流。

## 14. 测试与 CI

- 后端：103 个 JUnit 测试，全部使用内存 fake repository / fake sleeper / 固定 Clock，不连库、不真实 sleep、无随机。
- 前端：46 个 Vitest 测试，注入 `fetcher` 与 `scheduler`，不依赖真实网络与定时器。
- 迁移冒烟：MySQL 8.4 + Flyway V1～V7 + 种子/表结构/条件更新认领 SQL。
- 用例矩阵与连续三次本地等价运行结果：`docs/status/test-report.md` G8。

`.github/workflows/ci.yml` 三个 job：

| job | 内容 | 数据库 |
|---|---|---|
| `frontend` | `npm ci` → lint → typecheck → test → build | 否 |
| `backend` | `./mvnw -B verify` | 否 |
| `migration-smoke` | Flyway migrate/validate → `scripts/ci/migration-smoke.sh` | MySQL 8.4 service |

本地等价命令：

```bash
cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build
cd backend && ./mvnw -B verify

# 迁移冒烟（先启动 compose 的 mysql）
cd backend && ./mvnw -B org.flywaydb:flyway-maven-plugin:migrate \
  -Ddb.url='jdbc:mysql://127.0.0.1:13306/xiaoying?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
docker run --rm --network xiaoying_default \
  -e DB_HOST=mysql -e DB_PORT=3306 -e DB_USER=xiaoying \
  -e DB_PASSWORD=xiaoying -e DB_NAME=xiaoying \
  -v "$PWD/scripts/ci:/scripts:ro" mysql:8.4 \
  bash /scripts/migration-smoke.sh
```

> 本仓库未配置 GitHub 远端，Actions 尚未实际触发；G8 记录的是同一组命令在本地连续三次的结果。推送后需确认首次三 job 全绿并替换徽章地址。

## 15. 已完成、未完成与已知限制

已完成：R-01～R-14 的功能、测试、CI 配置、容器交付与文档。题目要求的功能均已实现并通过演示。

未完成 / 由交付人执行：

- 通过原沟通渠道发送仓库链接或压缩包，并与对方确认提交日期（本 README 记录快照日期，实际发送动作由交付人完成）。
- 向 GitHub 推送后触发 Actions 首次运行、替换 CI 徽章中的 `OWNER/REPOSITORY`。
- 完整焦点陷阱（结果对话框内 Tab 可离开对话框）；当前已实现 Esc 关闭与焦点返回。

已知限制：

- 单实例：多实例会重复消费任务；无分布式锁。
- 重启时处理中的任务变为失败，不实现断点续跑。
- 默认串行；`APP_PIPELINE_CONCURRENCY` 可调，但未做公平调度。
- 仅前端防重复提交，无服务端 `Idempotency-Key`。
- 结果为 Java2D 模拟拼图，不调用真实 AI；中文标题依赖运行环境字体，缺失时回退英文标题。
- 开发模拟接口不鉴权，仅限本地/演示；生产必须关闭。
- 轮询间隔固定 2 秒；历史首屏最近 50 条，未做分页/无限滚动；多标签页各自轮询，无跨标签同步。
- 无对象存储、病毒扫描、速率限制、CSRF Token（依赖 SameSite=Lax + 同源代理）、任务删除/重做/下载、国际化。
- 浏览器验收时本机 3000 被其它应用占用，前端临时映射 3100；交付默认仍为 3000。

## 16. 实际投入与取舍

实际投入（单日集中开发，含 AI 协作）：

| 日期 | 阶段 | 内容 | 实际 |
|---|---|---|---|
| 2026-10-28 | 需求与方案 | 需求拆解、架构、风险、版本验证 | 约 1.0 h |
| 2026-10-28 | 后端 | 迁移、认证、模板/素材、任务流水线、查询与模拟 | 约 1.7 h |
| 2026-10-28 | 前端 | 模板、上传、提交、轮询、历史、结果预览 | 约 1.6 h |
| 2026-10-28 | 验证 | 单测、浏览器验收、断网/重启/隔离/小屏 | 约 0.6 h |
| 2026-10-28 | 交付 | 镜像、Compose、CI、README、文档与标签 | 约 0.9 h |
| 合计 | | 仓库时间戳跨度约 10:24–15:40 | **约 5.8 h** |

> 上述为按会话与文件时间戳估算的实际 wall-clock，未逐任务计时；执行计划的估算合计为 16 人日（单人串行），本次在 AI 协作下压缩为单日。若按人日申报，建议以团队实际评审口径为准。

主要取舍：

| 决策 | 选择 | 原因 | 未选择 |
|---|---|---|---|
| 状态更新方式 | 轮询（2 秒） | 实现与清理简单，满足题目，断网可保留快照 | SSE / WebSocket |
| 异步执行 | DB 队列 + 进程内 worker | 等待态可见、重启可恢复、无额外中间件 | MQ / Redis |
| 重启恢复 | 启动对账标记失败 | 题目不要求断点续跑 | checkpoint 续跑 |
| 身份 | 种子账号 + 服务端会话 Cookie | 隔离可验证、成本低 | OAuth / 完整 Spring Security |
| 结果生成 | Java2D 拼图 | 可预览、无外网依赖 | 真实 AI / 复制原图 |
| 阶段证据 | 阶段字段 + `task_events` | 便于验收追溯阶段顺序 | 仅阶段字段 |
| 并行度 | 默认串行，可配 | 题目不要求真正并行 | 固定高并发 |
| 幂等 | 前端防重 | 非必需，避免额外接口 | 服务端幂等键 |
| 前端状态 | Redux Toolkit 按 id 归一化 | 版本合并与多任务隔离可单测 | Context / SWR |

## 17. AI 使用说明

本项目在 ZCode（AI 编程代理）中完成，使用方式是「人定目标与验收口径，AI 写代码/测试/文档并在浏览器中验证」：

1. 需求拆解：先把题目 R-01～R-14 转成里程碑、测试矩阵与风险清单，再逐阶段实现。
2. 代码与测试：每个里程碑先写核心逻辑与可注入依赖（`Sleeper`、`Clock`、`fetcher`、`scheduler`），再补对应用例；提交信息带需求/用例编号。
3. 真实环境验证：AI 通过 Docker Compose、curl 与内置浏览器执行登录、上传、提交、断网、重启、用户隔离、375 px 等验收，并把结果写回 `docs/status/test-report.md`。
4. 问题定位示例：
   - 镜像构建失败时发现 `package-lock.json` 混入 extraneous 的 `lightningcss` 平台包，`npm prune` + 重新生成锁文件修复。
   - 阶段观察不稳定时，先确认任务 id/顺序与 API 返回值，再用 3/8/3 秒耗时验证轮询可见性。
   - Flyway 前缀在本地无法解析时，改用完全限定的 `org.flywaydb:flyway-maven-plugin:migrate`，CI 同步。
5. 人工复核：交付人需复核演示脚本、真实密码策略、提交渠道与日期；AI 不代替对外发送仓库或压缩包。

## 18. 第三方来源与复用范围

- 框架与库：Next.js、React、Redux Toolkit、TypeScript、Vitest、Spring Boot、Spring Framework、MyBatis、Flyway、MySQL Connector/J、JUnit、Mockito、AssertJ，均来自公开开源项目，遵循各自许可证（MIT / Apache-2.0 / EPL 等）。
- 构建工具：Maven Wrapper 3.3.4（Apache-2.0）、npm 锁文件。
- 容器镜像：`maven:3.9-eclipse-temurin-21`、`eclipse-temurin:21-jre-jammy`、`node:22-alpine`、`mysql:8.4`。
- 设计参考：题目提供的 UI 截图仅用于页面结构参考；没有复制第三方模板、图标或图片。
- 示例封面：`frontend/scripts/generate-covers.mjs` 本地生成，无第三方素材；用户上传的图片仅用于本地演示。
- 脚手架：项目目录与配置自行搭建，未复制其他仓库的业务代码；依赖版本由锁文件固定。

## 19. 交付与验收

- 交付快照：tag `v1.0-submission`（`git describe --tags --always` 可查）。
- 验收环境：`docker compose down -v && docker compose up --build -d`，三服务 healthy。
- 提交方式：仓库链接或压缩包，经原沟通渠道发送；提交日期：2026-10-28（以双方最终确认为准）。
- 压缩包排除：`node_modules/`、`.next/`、`target/`、`dist/`、`data/`、`uploads/`、`results/`、`.env`、日志与本地截图。可用：

```bash
git archive --format=zip --prefix=xiaoying/ -o xiaoying.zip v1.0-submission
```

- 需求追踪：`docs/status/requirements-status.md`；过程记录：`docs/status/dev-log.md`；测试证据：`docs/status/test-report.md`；决策：`docs/status/decisions.md`。

## 20. README 16 项对照

| 题目 10.2 | 本文档 |
|---|---|
| 1 项目简介与截图（可选） | §1 |
| 2 技术版本 | §2 |
| 3 项目结构 | §3 |
| 4 构建、测试、启动命令 | §4、§14 |
| 5 前后端与数据库连接方式 | §5 |
| 6 配置项与无真实凭据示例 | §6、`.env.example` |
| 7 数据库初始化、迁移、种子、素材 | §7 |
| 8 主要接口与字段 | §8 |
| 9 任务状态、阶段、后台执行 | §9 |
| 10 成功/失败/耗时/多任务/刷新演示 | §10 |
| 11 用户身份与个人记录 | §11 |
| 12 已完成/未完成、限制、投入与取舍 | §15、§16 |
| 13 重启恢复边界 | §12 |
| 14 部署与回滚 | §13 |
| 15 AI 使用说明与工作流 | §17 |
| 16 第三方来源与复用范围 | §18 |

## 门标签

| 标签 | 里程碑 | 说明 |
|---|---|---|
| `gate-m0` | 立项与版本验证 | `ab1e371` |
| `gate-m1` | 迁移、认证、错误模型 | `9f9a6b9` |
| `gate-m2` | 模板与素材 | `2a289cc` |
| `gate-m2.1` | 上传时间毫秒精度修复复跑 | `aa43e72` |
| `gate-m3` | 任务受理与异步流水线 | `b8a8516` |
| `gate-m4` | 查询、历史、结果与模拟控制 | `b8a8516` |
| `gate-m5` | 前端地基与模板页面 | `05a7d71`、`b8dcfe8` |
| `gate-m6` | 制作页上传槽位与提交 | `05a7d71`、`b8dcfe8` |
| `gate-m7` | 任务跟踪、历史与刷新恢复 | M7 提交 |
| `gate-m8` | 测试补齐与 CI | M8 提交 |
| `v1.0-submission` | 容器、README 与交付快照 | 本次 M9 提交 |

需求追踪与过程证据见 `docs/status/`。后端构建/测试同时支持本机 JDK 21 与 Maven 容器；CI 与交付镜像固定 Node 22、Java 21。