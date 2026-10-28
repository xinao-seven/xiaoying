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

## G5 前端地基与模板页面（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` | lint 0 问题；tsc 0 错误；Vitest 26 passed；Next build 生成 `/`、`/login`、`/templates/[id]`、`/api/[...path]` 与 middleware |
| 2 | 浏览器 `localhost:3000` 未登录访问 `/` | 307 到 `/login`；登录 demo 后进入列表 |
| 3 | 列表首屏 | 6 个模板卡片、4 个分类（创意图片 2、宠物写真 2、节日贺卡 1、婚礼情侣 1）；顶栏显示 `Demo User` 与登出按钮 |
| 4 | 点击“宠物写真” | 卡片 2 个（萌宠写真、多宠合影），按钮 `aria-pressed=true`，状态“共 2 个模板” |
| 5 | 连续点击 创意图片 → 节日贺卡 → 婚礼情侣 → 宠物写真 → 节日贺卡 | 最终只显示 1 个“节日贺卡”，选中态与列表一致（旧响应未覆盖） |
| 6 | 停后端 `docker compose ... stop backend` 后刷新 `/` | 分类与列表均进入错误态：“后端服务暂不可用，请稍后重试”，提供“重试/重新加载”，无写死数据 |
| 7 | 重启后端后刷新 `/` | 恢复 6 个模板；顶栏恢复 `Demo User` |
| 8 | `/templates/does-not-exist` | 显示“模板不存在”、说明文字与“返回模板列表”入口（404 来自 Java 接口） |
| 9 | 375×812 详情页 | `innerWidth=375`、`documentElement.scrollWidth=360`，无横向滚动；两个槽位同列（x=12、宽 336）；顶栏换行 |
| 10 | 登出后访问 `/` | 跳转 `/login`；登录页标题可见 |
| 11 | 代理登录（`localhost:3000/api/auth/login`） | 错误密码 401 JSON；正确密码 200，`Set-Cookie` 含 `HttpOnly; SameSite=Lax; Path=/; Max-Age=604800`，Cookie 写入 jar 后续请求有效 |
| 12 | 代理模板接口 | `GET /api/templates` 6 条；`?category=%E5%AE%A0%E7%89%A9%E5%86%99%E7%9C%9F` 返回 `pet-portrait,pet-family`；未知模板 404 |

## G6 上传槽位与提交（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `npm test`（UploadSlot / SubmitPanel） | 10 passed：TC-FE-09/10（成功切换、防重复、失败保留）、TC-FE-11/12（类型/大小拦截、单槽重试、预览回收、卸载回收） |
| 2 | 打开 2 图模板详情 | 渲染“槽位 1/2”，素材要求 2 张、JPEG/PNG、5 MB；未上传时按钮禁用并提示“还有 2 个槽位未上传完成” |
| 3 | 错误类型（`text/plain`） | 槽位 1 显示“仅支持 JPEG、PNG 格式，当前为 text/plain”，状态“需要重新选择”，未发起上传请求 |
| 4 | 6 MB PNG（模板上限 5 MB） | 槽位 1 显示“图片不能超过 5 MB，当前 6 MB”，未发起上传请求 |
| 5 | 上传两张 720×540 PNG | 真实 XHR 经 Next 代理；两槽位“已上传”，`已上传 2/2`，两个 `blob:` 预览，提交按钮启用 |
| 6 | 替换槽位 1 | 预览 `src` 由旧 `blob:` 变为新 `blob:`（旧 URL 已 revoke，单元测试断言） |
| 7 | 移除槽位 2 | 槽位 2 恢复空态，`已上传 1/2`，提交按钮禁用 |
| 8 | 停后端后上传槽位 2 | 仅槽位 2 显示“后端服务暂不可用，请稍后重试”，槽位 1 仍“已上传”，出现“重试槽位 2 的上传” |
| 9 | 重启后端后点重试 | 仅重传槽位 2，恢复 `已上传 2/2`，无错误提示，提交按钮启用 |
| 10 | 提交任务 | 服务端 `total` 由 1 增至 2（仅新增 1 个）；出现“提交成功，任务已加入右侧列表”；任务卡片立即出现（旅行明信片/等待处理）；按钮恢复可用 |
| 11 | 连续点击提交（单元测试） | `createTask` 只调用 1 次（按钮禁用 + `inFlight` ref 双保险） |
| 12 | 校验失败后的素材保留 | 失败槽位保留文件与预览，其他槽位不受影响；单测断言重试只调用一次对应文件 |

备注：
- 浏览器自动化环境（IAB）不提供文件选择器，第 3～10 项的选图通过页面内构造 `File` + `DataTransfer` 赋给 `input[type=file]` 并触发 `change` 完成；组件状态机、XHR、同源代理与后端均为真实执行。
- 浏览器全页截图在 IAB 下存在拼接伪影，布局结论以 `scrollWidth` 测量与视口截图为证据。
- 联调期间创建的任务保留在本地数据库，不影响仓库内容。

## G7 任务跟踪、历史、刷新恢复（2026-10-28）

| # | 命令/检查 | 结果 |
|---|---|---|
| 1 | `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` | lint 0；tsc 0；46 tests（tasksSlice 8、useTaskPolling/History 11、TaskCard 8、提交 4、上传 6、模板 5、格式化 4）；build 6 条路由（新增 `/history`） |
| 2 | 3 个任务同时存在（1 处理中 + 2 等待） | `/history` 三张卡片分别显示“处理中/模拟生成”“等待处理”“等待处理”，创建时间与结果区互不串扰；统计为处理中 1、等待 2 |
| 3 | 全部任务终态后观察 6 秒 | Next 代理日志中 `GET /api/tasks/tsk_*` 计数保持 48 不变，无周期请求（TC-FE-04） |
| 4 | 处理中刷新 `/history` | 历史与活跃任务从服务端恢复；阶段继续从“素材检查”推进到“模拟生成”，最终完成/失败，无需手动操作（TC-FE-08） |
| 5 | 停后端 6 秒 | 处理中卡片保留 `PROCESSING/模拟生成`，出现 `role=status`“状态刷新失败，当前显示最后一次成功状态。（后端服务暂不可用，请稍后重试）”与“重试”按钮；任务未被标成失败（TC-FE-02） |
| 6 | 重启后端 | 5 秒内轮询自动恢复，sync 提示消失；被中断任务收口为“制作失败（模拟生成）：服务重启导致处理中断” |
| 7 | 注入 `failStage=GENERATING,failNext=1` 后创建任务 | 对应卡片显示“制作失败（模拟生成）：模拟生成失败：生成服务返回错误（模拟注入）”，其余任务成功；统计失败 1、完成 4 |
| 8 | 登出 demo、登录 alice 后打开 `/history` | `Alice` 顶栏、统计全 0、空态“还没有任务”；demo 任务不可见 |
| 9 | alice 直接访问 demo 任务接口 | 详情、结果、事件均 404 `TASK_NOT_FOUND`（TC-FE-07 服务端侧） |
| 10 | 结果预览 | 点击“预览大图”打开 `role=dialog`，焦点在“关闭”；关闭后 `role=dialog` 数量 0，`body.overflow` 复位 |
| 11 | 结果图 404（单元测试） | `fireEvent.error` 后显示“结果图暂时无法加载，任务仍为已完成”，卡片仍为“已完成”，预览按钮禁用（TC-FE-14） |
| 12 | 晚到旧 version（单元测试） | v4 响应在 v6 之后到达时被丢弃，终态不回退（TC-FE-06） |
| 13 | 375×812 `/history` | `innerWidth=375`、`scrollWidth=375`、`body.scrollWidth=375`，无横向溢出 |

备注：
- 本次浏览器验收将前端跑在 `127.0.0.1:3100`：本机 3000 端口被其它应用占用；`BACKEND_URL` 仍为 `http://127.0.0.1:8080`。
- 任务用 `curl` 以 demo 会话创建（IAB 无文件选择器），浏览器侧只负责跟踪、刷新、断网与用户隔离；创建、上传与流水线均为真实后端执行。
- 验收后模拟耗时已恢复默认 800/2500/800 ms，`failStage` 清空。

## G8 CI 与全量用例矩阵（2026-10-28）

### 流水线

`.github/workflows/ci.yml` 三个 job：

| job | 步骤 | 依赖服务 |
|---|---|---|
| `frontend` | `npm ci` → lint → typecheck → test → build | 无后端、无数据库 |
| `backend` | `./mvnw -B verify` | 无数据库（单测全 fake） |
| `migration-smoke` | Flyway migrate → validate → 种子/认领 SQL 冒烟 | MySQL 8.4 service container |

迁移冒烟脚本：`scripts/ci/migration-smoke.sh`，校验用户=2、启用模板≥6、`simulation_config` 有单例行、三张任务表存在，并验证条件更新认领一次成功/二次 0 行、终态不可回退。CI 通过 `mysql:8.4` 客户端容器执行同一脚本。

### 连续三次本地等价运行（无 GitHub 远端，无法实际触发 Actions）

| # | 命令 | 第 1 次 | 第 2 次 | 第 3 次 |
|---|---|---|---|---|
| 1 | `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` | 46 tests / 6 路由 | 46 tests / 6 路由 | 46 tests / 6 路由 |
| 2 | `cd backend && ./mvnw -B verify` | 103 tests, 0 失败 | 103 tests, 0 失败 | 103 tests, 0 失败 |
| 3 | 清库 → `flyway:migrate` → `flyway:validate` → `migration-smoke.sh` | OK | OK | OK |

三遍均无 flaky；测试不依赖真实网络、真实 sleep 或随机数（后端 fake sleeper/clock，前端注入 fetcher/scheduler）。

### 后端用例矩阵（TC-BE-01～22）

| 用例 | 测试类 / 方法 | 结果 |
|---|---|---|
| TC-BE-01 三阶段顺序 + 成功 | `TaskPipelineRunnerTest.completesThreeStagesInOrderAndMarksSucceeded` | 通过 |
| TC-BE-02/03/04 各阶段失败中断 | `TaskPipelineRunnerTest.plannedFailureStopsAtTargetStage`（3 参数） | 通过 |
| TC-BE-05 失败原因持久化 | `TaskPipelineRunnerTest.plannedFailureStopsAtTargetStage`（断言 failedStage/errorCode/message） | 通过 |
| TC-BE-06 终态幂等 | `TaskPipelineRunnerTest.terminalTaskIsIdempotent`、`terminalFailedTaskIsNotReprocessed` | 通过 |
| TC-BE-07 先提交后等待 | `TaskPipelineRunnerTest.stageIsCommittedBeforeSleep` | 通过 |
| TC-BE-08 查询只读 | `TaskQueryServiceTest.queriesAreReadOnly` | 通过 |
| TC-BE-09/10/11 数量/MIME/大小 | `TaskCreateServiceTest.rejectsWrongMaterialCount`、`rejectsMimeOutsideTemplateList`、`rejectsMaterialOverTemplateLimit` | 通过 |
| TC-BE-12 素材归属 → 404 | `TaskCreateServiceTest.rejectsMaterialOwnedByAnotherUser` | 通过 |
| TC-BE-13 模板不存在 | `TaskCreateServiceTest.rejectsMissingTemplate` | 通过 |
| TC-BE-14 参数非法 | `TaskCreateServiceTest.rejectsUnknownParams` 等 6 个校验用例 | 通过 |
| TC-BE-15 并发认领唯一 | `TaskClaimServiceTest.concurrentClaimsOnlyOneSucceeds`、`firstClaimWinsAndSecondLoses` | 通过 |
| TC-BE-16 未捕获异常兜底 | `TaskPipelineRunnerTest.unexpectedExceptionIsPersistedAsInternalError` | 通过 |
| TC-BE-17 结果文件生成 | `Java2dResultComposerTest.storesPngAndReturnsResultUrl`、`corruptedMaterialFailsAsUnreadable` | 通过 |
| TC-BE-18 多任务隔离 | `TaskPipelineRunnerTest.twoTasksDoNotInterfere` | 通过 |
| TC-BE-19 历史排序分页 | `TaskQueryServiceTest.historyIsNewestFirstAndPaged`、`paginationRejectsInvalidValues` | 通过 |
| TC-BE-20 用户隔离 | `TaskQueryServiceTest.userIsolationReturns404ForOtherUsersTask`、`resultIsAvailableOnlyForSucceededTask` | 通过 |
| TC-BE-21 重启对账 | `RestartReconcilerTest.marksProcessingTaskFailedAndKeepsPendingClaimable` | 通过 |
| TC-BE-22 PENDING 可恢复 | `RestartReconcilerTest.marksProcessingTaskFailedAndKeepsPendingClaimable`（PENDING 保持 + 可认领）、`reconcileIsIdempotent` | 通过 |
| 补充 登录/会话 | `AuthControllerTest` 7、`SessionServiceTest` 4 | 通过 |
| 补充 413/415/不可解码 | `MaterialControllerTest` 6、`MaterialUploadServiceTest` 9 | 通过 |
| 补充 模拟单一来源 | `SimulationServiceTest` 12、`DevSimulationControllerTest` 3 | 通过 |

### 前端用例矩阵（TC-FE-01～16）

| 用例 | 测试文件 / 用例 | 结果 |
|---|---|---|
| TC-FE-01 多任务隔离 | `tasksSlice.test.ts`「多任务按 id 独立合并」、`useTaskPolling.test.tsx`「多任务各自合并」 | 通过 |
| TC-FE-02 查询异常 ≠ 失败 | `useTaskPolling.test.tsx`「查询失败只写 sync」、`TaskCard.test.tsx`「查询异常只追加提示」 | 通过 |
| TC-FE-03 失败与同步错误区分 | `TaskCard.test.tsx`「失败任务展示失败阶段与原因」「查询异常只追加提示」 | 通过 |
| TC-FE-04 终态停跟踪 | `useTaskPolling.test.tsx`「任务到达终态后停止轮询」 | 通过 |
| TC-FE-05 无活跃不轮询 | `useTaskPolling.test.tsx`「无活跃任务时不发请求也不排定时器」 | 通过 |
| TC-FE-06 晚到响应丢弃 | `tasksSlice.test.ts`「丢弃晚到的旧版本响应」「批量合并时逐条应用版本规则」 | 通过 |
| TC-FE-07 用户切换清理 | `useTaskPolling.test.tsx`「resetAll 会 abort 在途请求」 | 通过 |
| TC-FE-08 刷新恢复 | `useTaskPolling.test.tsx`「首屏同时拉历史和活跃任务」 | 通过 |
| TC-FE-09 提交成功/失败 | `SubmitPanel.test.tsx`「成功后立即插入任务」「提交失败保留槽位」 | 通过 |
| TC-FE-10 防重复提交 | `SubmitPanel.test.tsx`「连续点击只发起一次请求」 | 通过 |
| TC-FE-11 上传校验 | `UploadSlot.test.tsx`「类型不符」「超过大小上限」 | 通过 |
| TC-FE-12 失败重试/回收 | `UploadSlot.test.tsx`「单槽重试」「替换与移除回收」「卸载回收」 | 通过 |
| TC-FE-13 卸载清理 | `useTaskPolling.test.tsx`「卸载时 abort 在途请求」 | 通过 |
| TC-FE-14 结果图 404 | `TaskCard.test.tsx`「结果图 404 时显示占位」 | 通过 |
| TC-FE-15 列表异常/空态/竞态 | `TemplateList.test.tsx` 前 3 个用例 | 通过 |
| TC-FE-16 模板 404 | `TemplateList.test.tsx`「模板不存在」「详情加载失败可重试」 | 通过 |

> 说明：本仓库尚未配置 GitHub 远端，`ci.yml` 只做了 YAML 解析校验与本地等价运行；推送后需在 Actions 页面确认三 job 首次全绿，并连续触发 3 次。README 的 CI 徽章地址中的 `OWNER/REPOSITORY` 需在推送后替换。
