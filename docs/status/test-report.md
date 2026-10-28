# 测试报告

> 每过一门追加一次执行结果；命令必须可直接复制。
> 本机无 JDK，后端命令通过 Maven 官方镜像执行，等价于 `./mvnw <goal>`。

## G0 立项与版本验证（2026-10-28）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` | 通过：lint 0 问题；tsc 0 错误；Vitest 4 passed；Next build 生成 4 个静态页 |
| 1b | `cd frontend && npm ci && npm run build` | 干净安装成功（458 packages，21 s），构建通过 |
| 2 | `docker run --rm -v "$PWD/backend:/ws" -w /ws -v xiaoying-m2:/root/.m2 maven:3.9-eclipse-temurin-21 mvn -B -DskipTests package` | BUILD SUCCESS，产物 `target/xiaoying-backend-0.1.0.jar` |
| 3 | `docker compose up -d mysql` + `mvn -B flyway:info '-Ddb.url=jdbc:mysql://mysql:3306/xiaoying?...'` | 连接 MySQL 8.4，显示 V1～V7 均为 Pending（空库） |

## G1 后端地基（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `mvn -B test` | Tests run: 33, Failures: 0, Errors: 0；AuthControllerTest 7、SessionServiceTest 4、MaterialControllerTest 5、MaterialUploadServiceTest 8、TemplateControllerTest 5、TemplateServiceTest 4 |
| 1b | `./mvnw -B verify`（Maven 容器内） | 33 tests green，BUILD SUCCESS；证明 Wrapper 3.3.4 可用 |
| 2 | `curl -X POST /api/auth/login -d '{"username":"demo","password":"wrong"}'` | 401 `{"code":"INVALID_CREDENTIALS","message":"用户名或密码错误","traceId":"req_..."}` |
| 3 | 登录 demo | 200；`Set-Cookie: XIAOYING_SESSION=...; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax` |
| 4 | 登出后带旧 Cookie 访问 `/api/auth/me` | 401 `UNAUTHORIZED`；登出响应 204 且 `Max-Age=0` |
| 5 | `docker compose down -v` 后重启应用 | 从 `<< Empty Schema >>` 自动应用 V1～V7，日志 `Successfully applied 7 migrations` |

## G2 模板与素材（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `GET /api/templates` | 6 条；`category=创意图片`（UTF-8 编码）返回 2 条 `demo-postcard`、`city-poster`；分类接口返回 4 个分类及数量 |
| 2 | `POST /api/materials`（720×540 PNG，demo 会话） | 201；`mimeType=image/png`、`width=720`、`height=540`、`sizeBytes=40482`；容器内 `/data/uploads/{userId}/{uuid}.png` 存在 |
| 3 | alice 访问 demo 的素材 URL | 404 `MATERIAL_NOT_FOUND`；无 Cookie 访问 → 401 |
| 4 | 伪 PNG 文本 / 11 MB 文件 | 415 `MATERIAL_MIME_NOT_ALLOWED`；413 `PAYLOAD_TOO_LARGE`，响应为 JSON（4 ms） |
| 5 | `GET /api/templates/{unknown}` | 404 `TEMPLATE_NOT_FOUND` |

备注：
- 11 MB 上传通过容器网络内的 curl 验证（`docker run --network xiaoying_default curlimages/curl`）。宿主机 curl 经 Docker Desktop 端口代理上传超限文件时会挂起，服务端已返回 413；属本机代理现象，应用逻辑由单测 `oversizedUploadMapsToJson413` 覆盖。
- 分类筛选需使用 UTF-8 URL 编码；Git Bash 直接输入中文会被按本地代码页编码，导致筛选为空。

## gate-m2.1 复跑（2026-10-28）

- 修复：上传响应 `createdAt` 未截断到毫秒，与接口约定（ISO-8601 毫秒）不一致。
- 变更：`MaterialService` 使用 `Instant.truncatedTo(MILLIS)`；`MaterialUploadServiceTest` 增加纳秒为零的断言。
- 复跑：`./mvnw -B test` → 33 tests, 0 failures, 0 errors。G2 其余条目不受影响。
## 开发环境：后端全 Docker（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait` | `xiaoying-mysql`、`xiaoying-backend-1` 均 healthy；耗时约 2 分钟（首次编译） |
| 2 | `curl http://localhost:8080/actuator/health` | 200 `{"status":"UP"}` |
| 3 | 登录 demo + 模板列表 + 上传 PNG | 200 / 6 条 / 201 `image/png 720×540`，`createdAt=2026-10-28T04:08:59.594Z`（毫秒） |
| 4 | `docker compose ... port mysql 3306` | `0.0.0.0:13306`，不与宿主机 3306 上的其他服务冲突 |

## M2 后审查修正复跑（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm --no-deps backend mvn -B test` | 39 tests, 0 failures, 0 errors（新增 `WebConfigTest` 1、`SimulationPropertiesTest` 2；`MaterialControllerTest` 6、`MaterialUploadServiceTest` 9、`TemplateControllerTest` 6） |
| 2 | `POST /api/materials` + `Content-Type: application/json` | 415 `UNSUPPORTED_MEDIA_TYPE`，JSON 含 `traceId` |
| 3 | `POST /api/templates` | 405 `METHOD_NOT_ALLOWED`，JSON 含 `traceId` |
| 4 | 上传 720×540 PNG（demo 会话） | 201；`image/png`、`width=720`、`height=540` |
| 5 | 容器网络内上传 11 MB 文件 | 413 `PAYLOAD_TOO_LARGE` JSON（应用侧毫秒级返回） |
| 6 | 上传 10,240,000 B 随机文件 | 415：大小在 10 MB 以内，按文件头拒绝（证明框架大小限制未误伤边界文件） |

## G3 任务受理与异步流水线（2026-10-28）

> 宿主机 `localhost` 可能解析到 `::1` 并被其他进程占用；本节 curl 统一使用 `http://127.0.0.1:8080`。

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `docker compose ... run --rm --no-deps backend mvn -B test` | 103 tests, 0 failures, 0 errors（新增 TaskPipelineRunnerTest 11、TaskCreateServiceTest 13、TaskClaimServiceTest 2、RestartReconcilerTest 2、Java2dResultComposerTest 5、TaskQueryServiceTest 7、SimulationServiceTest 12、DevSimulationControllerTest 3、TaskControllerTest 8、TaskMapperSqlTest 1） |
| 2 | 登录 demo + 上传 2 张 720×540 PNG | 登录 200；素材 201，`image/png` 40482/40788 B |
| 3 | `POST /api/tasks`（默认 800/2500/800 ms） | 201，`PENDING`、`version=1`、`stage=null`；轮询观察到 `MATERIAL_CHECK`（v3）→`GENERATING`（v4）→`ASSEMBLING`（v5）→`SUCCEEDED/DONE`（v6） |
| 4 | `GET /api/tasks/{id}/events` | 7 条：MATERIAL_CHECK/STARTED、SUCCEEDED；GENERATING/STARTED、SUCCEEDED；ASSEMBLING/STARTED、SUCCEEDED；DONE/TASK_SUCCEEDED |
| 5 | 连续 20 次 `GET /api/tasks/{id}` | API `version=6`、`status/stage` 不变；DB `updated_at=2026-10-28 05:49:34.246` 不变 |
| 6 | `POST /api/dev/simulation {"failStage":"GENERATING","failNext":1}` 后创建任务 | 201；任务 `FAILED/failedStage=GENERATING/errorCode=SIMULATED_FAILURE`；事件无 ASSEMBLING；结果 404；随后任务成功（计数已消费并清空） |
| 7 | `{"delayGeneratingMs":8000}` 后创建任务 | 创建 201 用时 13 ms；GENERATING 约 8.0 s；终态总耗时约 10.0 s |
| 8 | 连续创建 3 个 5 s 任务 | 1 s 后：1 个 PROCESSING（GENERATING）+ 2 个 PENDING；随后 active 3→2→1→0，依次成功 |
| 9 | 处理中重启后端（生成耗时 60 s） | 重启前 PROCESSING/GENERATING；重启后 `FAILED/failedStage=GENERATING/errorCode=SERVER_RESTART/errorMessage=服务重启导致处理中断`，事件 `STAGE_FAILED` 收口 |
| 10 | 独立后端实例 `APP_SIMULATION_DEV_API_ENABLED=false` | `GET /api/dev/simulation` → 404 `NOT_FOUND` JSON |
| 11 | `TaskMapperSqlTest` | 校验注解 SQL 中列清单与 `FROM` 之间有空格，防止文本块拼接回归 |

## G4 查询、历史、结果与模拟控制（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | 3 个任务（1 失败 2 成功）`GET /api/tasks?page=0&size=20` | `total=3`、`page=0`、`size=20`；顺序为创建时间倒序（S2、S1、F） |
| 2 | `page=0&size=2` / `page=1&size=2` | 第一页 S2、S1；第二页 F；两页 `total` 均为 3 |
| 3 | 全部终态时 `GET /api/tasks/active` | `items=[]`；8 s 任务创建后立即 `active` 只含该任务（PROCESSING/MATERIAL_CHECK） |
| 4 | 成功任务 `GET /api/tasks/{id}/result` | 200 `image/png`、125046 B、1200×800；`Cache-Control: private, max-age=60` |
| 5 | 失败任务 `GET /api/tasks/{id}/result` | 404 `TASK_NOT_FOUND` |
| 6 | `GET /api/tasks/{id}/events` | 事件按 `id` 升序；含 STAGE_STARTED/STAGE_SUCCEEDED/TASK_SUCCEEDED |
| 7 | `POST {"delayGeneratingMs":8000}` | 创建 201 用时 13 ms；GENERATING 约 8 s；恢复 2500 ms 后正常 |
| 8 | `failNext=1` 连续提交两次 | 第一次 FAILED/GENERATING；第二次 SUCCEEDED/DONE |
| 9 | `failNext=0`（持续失败）连续提交两次，再 `failStage=null` | 两次均 FAILED/GENERATING；清空后任务 SUCCEEDED/DONE |
| 10 | 用户隔离：alice 访问 demo 任务/结果 | 详情 404 `TASK_NOT_FOUND`；结果 404 `TASK_NOT_FOUND`；alice 历史 `total=0` |
| 11 | 查询只读：20 次查询前后 | API version=6 不变；DB `updated_at` 精确到毫秒不变 |
