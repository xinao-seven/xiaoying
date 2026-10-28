# 开发日志

> 每个里程碑结束追加一节；关键修复后追加。格式见 `docs/execution-plan.md` 14.2。

## 2026-10-28 · M0 立项与版本验证（0.5 人日）

目标：证明所选版本互相兼容，锁定依赖。

完成：
- 建立 `frontend/`、`backend/`、`docs/status/`、`.github/workflows/` 目录。
- 前端 Next.js 15.5.28 + React 19.2.9 + TypeScript 5.9.3 strict + ESLint 9 + Vitest 3.2.7。
- 后端 Spring Boot 3.5.16 + Java 21 + MyBatis 3.0.5 + Flyway 11.20.3 + MySQL Connector 9.7.0。
- `docker-compose.yml` 骨架只含 MySQL 8.4（healthcheck、utf8mb4、数据卷）；`.env.example` 只有演示值。
- Maven Wrapper 3.3.4（only-script，Maven 3.9.9）。
- 深色主题设计变量与首页骨架，避免 M5 返工。

验证：
- `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` 全过。
- Maven 容器 `mvn -B -DskipTests package` → BUILD SUCCESS。
- `mvn -B flyway:info` 连上 MySQL 8.4，识别 7 个待执行迁移。
- 实际版本见 README「技术版本」。

偏离与决策：
- 本机没有 JDK，后端构建/测试/迁移全部在 `maven:3.9-eclipse-temurin-21` 容器中执行；命令写在 README。
- 宿主机 3306 被其他项目占用，compose 宿主机端口默认改为 13306（`${MYSQL_PORT:-13306}:3306`），Maven `flyway` 默认 URL 同步改为 `localhost:13306`；容器内始终是 `mysql:3306`。
- `flyway-maven-plugin` 的 `locations` 增加 `filesystem:src/main/resources/db/migration`，否则插件扫描不到迁移（首次 `flyway:info` 显示 0 个）。

问题与修复：
- Flyway 插件首次只显示 `Empty Schema`、无 Pending 迁移 → 补 filesystem location 后能看到 V1～V7。

## 2026-10-28 · M1 后端地基：迁移、认证、错误模型（1.5 人日）

目标：数据库可重建、认证可闭环、错误响应可解析。

完成：
- V1～V7 迁移：users/sessions、templates、materials、tasks/task_materials、task_events、simulation_config、种子数据。
- 统一错误模型 `ErrorCode` / `ApiException` / `ApiError` / `GlobalExceptionHandler`，含 413、415、404 与参数校验。
- 会话：`SessionService`（签发/校验/删除/过期清理）、`CookieFactory`；令牌 32 字节随机，SHA-256 入库。
- `AuthInterceptor` + `CurrentUserArgumentResolver`；放行登录、模板接口与开发接口。
- 登录/登出/me 三个接口；BCrypt 种子账号 demo/alice。
- `AppProperties`（数据目录、并发、轮询、会话 TTL）与 `Clock` Bean。

验证：
- `mvn -B test`：33 个测试通过（含 AuthControllerTest 7、SessionServiceTest 4）。
- curl 错误密码 401 JSON；登录 Set-Cookie 含 `HttpOnly; SameSite=Lax; Path=/; Max-Age=604800`；登出 204 并清 Cookie；登出后 me 401。
- `docker compose down -v` 清空后重启应用：Flyway 从空库应用 V1～V7 成功，种子可用。

偏离与决策：
- `SimulationProperties`（初始耗时）推迟到 M4 与 `SimulationService` 一起落地；M1 只保留 `application.yml` 中的 `app.simulation.delays.*`，运行期不读取，避免出现两处配置同时生效。见 D4。

## 2026-10-28 · M2 模板浏览与素材上传（1.0 人日）

目标：模板数据从 MySQL 经 Java 输出；素材可上传、可落盘、可按归属取回。

完成：
- `GET /api/templates/categories`、`GET /api/templates?category=`、`GET /api/templates/{id}`。
- `POST /api/materials`：全局 10 MB、文件头识别 JPEG/PNG、ImageIO 取尺寸、UUID 落盘、写 materials 行。
- `GET /api/materials/{id}/content`：归属校验、404 统一、`Content-Type` + `Cache-Control: private`。
- 6 张自绘封面（`frontend/public/demo`，脚本 `npm run covers`），种子覆盖 4 分类、数量 1/2/3、大小 2/5/10 MB。

验证：
- 模板列表 6 条；`category=创意图片` 返回 2 条（UTF-8 URL 编码）。
- 上传 720×540 PNG → 201，文件位于 `/data/uploads/{userId}/{uuid}.png`。
- 用户 alice 取 demo 素材 → 404；无 Cookie → 401。
- 伪造 PNG → 415 `MATERIAL_MIME_NOT_ALLOWED`；11 MB → 413 `PAYLOAD_TOO_LARGE` JSON（从容器网络验证，见 test-report 备注）。

偏离与决策：
- 上传阶段即用 ImageIO 解码一次以取得宽高并拒绝损坏图片；`MATERIAL_CHECK` 阶段（M3）仍会再次校验文件存在与解码。

问题与修复：
- 宿主机 curl 上传 11 MB 时 Docker Desktop 端口代理挂起（应用侧从容器网络 4 ms 返回 413），改用容器内 curl 验证；不是应用缺陷。
## 2026-10-28 · 开发环境全 Docker 化（M0 补充）

目标：本机没有 JDK 也能编译、测试、运行后端，并消除 MySQL 端口冲突。

完成：
- 新增 `docker-compose.dev.yml`：`maven:3.9-eclipse-temurin-21` + 源码绑定挂载 + `maven-cache`/`app-data` 卷 + `/actuator/health` 健康检查，`depends_on` MySQL healthy。
- MySQL 宿主机端口默认改为 13306；`backend/pom.xml` 的 flyway 默认 URL 同步为 `localhost:13306`。
- README 补充 Docker 后端启动、重启、容器内测试与停止命令。

验证：
- `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait`：mysql 与 backend 均 healthy。
- `GET /actuator/health` → `{"status":"UP"}`；登录 demo 200；模板 6 条；上传 PNG 201（`createdAt` 毫秒精度）。
- `docker compose ... port mysql 3306` → `0.0.0.0:13306`，与本机 3306 的其他服务不冲突。

偏离与决策：
- 开发编排与 M9 交付镜像分离：开发用 Maven 镜像直接跑源码，M9 再提供多阶段 Dockerfile，避免提前维护两套构建。

## 2026-10-28 · M2 后审查修正

目标：消除审查发现的配置、错误映射与文档不一致，并补上计划要求的配置类与测试。

完成：
- `application.yml`：数据源默认端口改为 13306（与 compose 宿主机端口、README、flyway 插件一致）；multipart 文件上限复用 `app.upload.max-bytes`，请求上限额外留出边界开销，正好 10 MB 的文件不再被框架先拒。
- 补回 `config/SimulationProperties`（`app.simulation.delays.*`、`dev-api-enabled`）：只作为 M4 首次初始化 `simulation_config` 的输入，运行期仍只读表（见 D5）。
- `GlobalExceptionHandler` 新增 `HttpMediaTypeNotSupportedException → 415`、`HttpRequestMethodNotSupportedException → 405`，不再落到兜底 500。
- 上传落盘后写库失败时删除文件（`StorageService.deleteQuietly`），避免孤儿素材。
- 测试：新增 `WebConfigTest`（放行清单）、`SimulationPropertiesTest`（绑定与默认值）、非 multipart→415、405、MockMvc 413、写库失败清理。
- 文档：README 补 `gate-m2.1`、本地 DB 默认值说明与 dev 接口风险提示；`requirements.md` 头部版本对齐 v1.1；`pic/` 标记为本地参考并加入 `.gitignore`。

验证：
- 容器内 `mvn -B test` → 39 tests, 0 failures。
- 重建 dev 后端后：`POST /api/materials`（JSON）→ 415 JSON；`POST /api/templates` → 405 JSON；上传 PNG → 201；容器网络内 11 MB → 413 JSON。

过程修正：
- `docs/status` 在首个提交 `ab1e371` 就把 G0～G2 写成"通过"（G1/G2 早于对应代码提交）；`gate-m1`/`gate-m2` 标签所指提交也不含当次完成记录，证据到 `d3ca83b` 才补齐，晚于标签。历史不改写；后续门按计划 14.3/15.4 执行"文档提交 → 打标签"。本次修正建议单独打 `gate-m2.2`。

## 2026-10-28 · M3 任务受理与异步流水线（本次会话）

目标：受理立即返回；三阶段顺序执行；失败中断；终态不可回退；查询不可推进。

完成：
- `TaskMapper`：创建/槽位、PENDING 查询、条件认领、阶段更新、成功/失败、重启对账、事件、详情/历史/活跃查询。
- `TaskCreateService`：按需求 4.5 十步校验；事务内 `simulation_config` 行锁 + `tasks.planned_fail_stage` 快照；计数消费到 0 时清空 `fail_stage`，避免误入持续失败模式。
- `TaskDispatcher`：`@Scheduled(fixedDelay)` 轮询；信号量把在飞任务限制在并发度内（默认 1），其余保持 PENDING；线程池拒绝时回退 PENDING。
- `TaskPipelineRunner`：每阶段先 `updateStage` + 版本递增，再 sleep，再执行；`Sleeper`/`ResultComposer` 接口；顶层捕获 Throwable 落 `INTERNAL_ERROR`。
- `MATERIAL_CHECK` 检查数量/文件存在/可解码/MIME/大小；`GENERATING` 命中快照即失败；`ASSEMBLING` 用 Java2D 写 1200×800 PNG。
- `RestartReconciler`：启动把残留 PROCESSING 标 `FAILED/SERVER_RESTART` 并写事件。
- 线程池 `pipelineExecutor`：core=max=并发度，队列 100，`AbortPolicy`。

验证：
- 容器内 `mvn -B test` → 103 tests, 0 failures。
- curl（默认 800/2500/800 ms）：PENDING→PROCESSING/MATERIAL_CHECK→GENERATING→ASSEMBLING→SUCCEEDED；`task_events` 顺序 7 条；结果 PNG 1200×800。
- 8 s 生成配置：创建接口 13 ms 返回 201；GENERATING 约 8 s；总耗时约 10 s。
- 连续 20 次查询：version=6、`updated_at` 不变。
- 注入 `GENERATING, failNext=1`：FAILED/GENERATING，事件无 ASSEMBLING；下一任务成功。
- 3 个 5 s 任务：1 PROCESSING + 2 PENDING，依次完成。
- 处理中重启：残留任务 FAILED/SERVER_RESTART，事件 STAGE_FAILED。
- 关闭 dev API 的独立实例：`GET /api/dev/simulation` → 404 JSON。

偏离与决策：
- 用信号量限制“已认领未完成”的任务数；线程池队列本身不承担排队可见性，避免串行时所有任务都显示 PROCESSING。见 D7。
- 失败计划快照使用 `SELECT ... FOR UPDATE` + 应用层计数；计数到 0 同时清空 `fail_stage`，用一张表表达“计数模式/持续失败模式/已清空”三种状态。见 D8。
- 事件表与阶段字段并存；阶段先提交后等待用 fake sleeper 在 sleep 中读库验证（TC-BE-07）。见 D9。
- 结果生成为 Java2D 拼图，不调用真实 AI；结果文件固定 `results/{taskId}.png`。见 D10。

问题与修复：
- 首次联调 `GET /api/tasks/{id}` 返回 500：Java 文本块开头不含换行，`"SELECT " + COLUMNS + """FROM ..."""` 把 `FROM` 拼到最后一个列名后（`...template_titleFROM tasks`）。在 `TaskMapper.COLUMNS` 末尾补空格，并新增 `TaskMapperSqlTest` 直接校验注解 SQL。修复包含在 `b8a8516`。

遗留 / 风险：
- 单实例假设；多实例会重复消费，README 说明。
- 重启对账选择“标记失败”，不实现断点续跑。
- Java2D 中文标题依赖容器字体；无 CJK 字体时回退 `MY CREATION`，不影响结果文件生成。

## 2026-10-28 · M4 查询、历史、结果与模拟控制（本次会话）

目标：把后端状态变成前端可用的读接口，并提供可重复的演示开关。

完成：
- `GET /api/tasks/{id}`、`GET /api/tasks?page=&size=`、`GET /api/tasks/active`、`GET /api/tasks/{id}/result`、`GET /api/tasks/{id}/events`；全部按当前用户过滤，越权 404。
- 历史按 `created_at DESC, id DESC`；`page` 从 0、`size` 1～100；非法值 400 JSON。
- 结果仅 `SUCCEEDED` 且本人可读；`Cache-Control: private, max-age=60`。
- `SimulationService` + `DevSimulationController`：单例表读取/更新；`failStage` 缺省保持、显式 null 清空；`failNext` 计数；`failNext=0` + `failStage` 为持续失败。
- `SimulationInitializer`：仅在单例行缺失时用 `app.simulation.delays.*` 初始化，绝不覆盖开发接口已改的值。
- `GlobalExceptionHandler` 增加 `MethodArgumentTypeMismatchException → 400`，`page=abc` 不再 500。

验证：
- 3 个任务（1 失败 2 成功）：`page=0&size=20` → `total=3`，顺序为创建时间倒序；`size=2` 分页两组正确。
- `active`：全部终态时为空；8 s 任务创建后立即只返回该任务（PROCESSING/MATERIAL_CHECK）。
- 成功任务结果 200 `image/png`、125 KB、1200×800；失败任务结果 404 `TASK_NOT_FOUND`。
- 20 次查询前后 API version 与 DB `updated_at` 一致。
- `POST {"delayGeneratingMs":8000}` 后新任务 GENERATING 约 8 s；恢复 2500 ms。
- `failNext=1` 第一次失败、第二次成功；`failNext=0` 连续两个失败，`failStage=null` 后恢复成功。

偏离与决策：
- 查询 Service 不调用任何写方法，Mapper 只声明 SELECT；用 fake mapper 的写计数断言 TC-BE-08。
- 开发接口用 `@ConditionalOnProperty` 实现“关闭即不存在”，请求落到统一 404 JSON；未采用运行时 if 判断。

遗留 / 风险：
- 开发接口不鉴权，仅限本地/演示环境；生产必须保持 `APP_SIMULATION_DEV_API_ENABLED=false`。
