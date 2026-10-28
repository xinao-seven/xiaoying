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

## 2026-10-28 · 本机开发环境补充：JDK 21 + Maven

背景：此前本机没有 JDK，所有后端命令都要通过 `maven:3.9-eclipse-temurin-21` 容器执行，测试命令长且需要先停 dev 后端避免争用 `target/`。

完成：
- 安装 Eclipse Temurin JDK 21.0.12.1 到 `C:\Users\xinao\jdks\temurin21\jdk-21.0.12.1+1`，设置用户级 `JAVA_HOME` 与 PATH。
- 安装 Apache Maven 3.9.9 到 `C:\Users\xinao\tools\apache-maven-3.9.9`，并将 `bin` 加入用户级 PATH；同时把 Maven 发行版放入 Maven Wrapper 缓存（`~/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1`），避免本机 `wget` 访问 Maven Central 返回 502 时 wrapper 无法下载。
- 本地验证：`cd backend && mvn -B test` → 103 tests, 0 failures；`./mvnw -v` 与 `./mvnw -B test` 也可用。

影响：
- 日常单测改为 `cd backend && ./mvnw -B test`，不需要 Docker/MySQL；容器命令仍保留用于干净环境复现与 CI 等价验证。
- 因为 dev 后端仍通过 bind mount 与宿主机共用 `backend/target`，测试前仍需停掉 dev 后端（或改用本机 `./mvnw spring-boot:run`）。
- 用户级环境变量对已经运行的 ZCode/终端进程不生效；新开终端或重启客户端后，`java`/`mvn` 才会直接在 PATH 上。

## 2026-10-28 · M5 前端地基与模板页面（1.5 人日）

目标：同源代理、认证、状态管理与模板浏览跑通，为制作页提供骨架。

完成：
- `app/api/[...path]/route.ts`：所有 `/api/*` 转发到 `BACKEND_URL`（默认 `http://127.0.0.1:8080`）；透传 `Cookie`、多个 `Set-Cookie`、`Content-Type`；上传用 `arrayBuffer` 转发；后端不可达时返回 502 JSON。
- `lib/api/client.ts` + `endpoints.ts` + `types.ts`：统一 fetch 包装，非 2xx 解析 `{code,message,fieldErrors,traceId}` 为 `ApiError`；401 由全局处理器清空 store 并跳 `/login?next=…`（登录接口的 401 除外）。
- `/login`：用户名/密码、提交中禁用、401 文案、`next` 参数安全校验。
- `app/layout.tsx` + `Providers` + `TopBar`：Redux Provider、产品名、当前用户、登出；`middleware.ts` 仅做“无会话 Cookie 跳登录”的粗判。
- `store/tasksSlice.ts`：`byId`、`order`（创建时间倒序）、`upsertTask/upsertMany`（`version` 更小直接丢弃）、`setSync`、`resetAll`。
- `/`：分类栏（含“全部”与数量）、卡片网格、加载骨架、空态、错误重试；分类切换用 `AbortController` + 请求序号丢弃旧响应。
- `/templates/[id]`：封面、标题、描述、素材要求、上传区、任务侧栏；404 显示“模板不存在”与返回入口；加载失败可重试。
- 深色主题扩展：按钮、分类 chip、卡片、骨架、详情双栏、上传槽位、任务卡片；375 px 下顶栏换行、详情单栏、无横向滚动。

验证：
- `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` 全过（26 tests，5 条路由 + middleware）。
- 浏览器（`localhost:3000`）：demo 登录成功；列表 6 个模板、4 个分类；筛选“宠物写真”得 2 个；连续切换 5 次分类后最终只显示“节日贺卡”且 `aria-pressed=true`。
- 停后端后刷新：分类与列表均显示错误态与重试入口，无写死卡片；后端恢复后重新加载得到 6 个模板。
- `/templates/does-not-exist` 显示“模板不存在”和两个返回列表入口。
- 375×812：`documentElement.scrollWidth=360 < innerWidth=375`，无横向溢出；两个槽位同列（x=12，宽 336）。
- 登出后跳到 `/login`，直接访问 `/` 被 middleware 重定向回登录页。

偏离与决策：
- 模板与详情页使用 Client Component 取数，构建期不访问后端，CI 无后端也能 build。
- 需要会话的结果图、素材图与 `blob:` 预览用原生 `<img>`，不走 `next/image` 优化器（优化器请求不会携带浏览器 Cookie）。见 D12。
- 顶栏在非 401 的查询错误时保留最后一次已知用户，并显示“服务暂不可用”，避免后端停机被误认为未登录。

问题与修复：
- 后端停机时顶栏一度显示“去登录”（`me()` 任何失败都清空用户）→ 区分 401 与其他错误，其他错误保留用户并显示服务不可用。

## 2026-10-28 · M6 制作页：上传槽位与提交（2.0 人日）

目标：R-03 的交互全部落地；提交成功后立刻看到任务，而不是停在“提交中”。

完成：
- `useUploadSlots`：槽位状态机 `EMPTY → SELECTED → UPLOADING → UPLOADED`，失败进入 `ERROR` 可重试；任意态可移除，`UPLOADED` 可替换。
- 预览：`URL.createObjectURL`，替换/移除/卸载时统一 `revokeObjectURL`；每次选择带请求令牌，旧上传结果与晚到响应不会覆盖新状态。
- 客户端校验：类型、大小、空文件；错误定位到具体槽位且不发请求；数量由槽位数量与“全部 UPLOADED”约束。
- 选择即上传：XHR 上传以获取进度；槽位级进度条、失败原因与单独重试。
- `SubmitPanel`：全部槽位 `UPLOADED` 才可提交；`submitting` 与 `uploading` 分离；`inFlight` ref 防同 tick 重复提交；成功后 `upsertTask` 插入 store、清空提交中状态并显示成功提示；失败保留槽位与标题输入，显示表单级错误。
- `TaskCard` / `TaskPanel`：提交后立即在详情页右栏显示任务（等待/处理中/成功/失败），结果图用同源受控 URL。
- 401：全局处理器清空 store 并跳登录，上传与提交共用。

验证：
- `npm test` 26 passed：`UploadSlot.test.tsx`（6，含类型/大小拦截、单槽重试、预览回收、卸载回收）、`SubmitPanel.test.tsx`（4，含双提交只发一次、成功插入、失败保留、caption）。
- 浏览器：2 张模板渲染 2 个槽位；错误类型提示“仅支持 JPEG、PNG 格式，当前为 text/plain”；6 MB 文件提示“图片不能超过 5 MB，当前 6 MB”；两种非法选择均未发起上传。
- 上传两张 PNG（真实 XHR 经同源代理）：`已上传 2/2`、两个 `blob:` 预览、提交按钮启用；替换槽位 1 后预览 URL 改变；移除槽位 2 后恢复空槽、`已上传 1/2`、提交按钮禁用。
- 停后端后上传槽位 2：仅该槽位显示“后端服务暂不可用，请稍后重试”，槽位 1 仍为已上传；恢复后端后点“重试槽位 2 的上传”成功，两个槽位均 `UPLOADED`。
- 提交后服务端任务总数 1→2（只新增 1 个），出现“提交成功”提示与任务卡片（旅行明信片/等待处理），按钮恢复可用。
- 连续点击提交由单元测试断言只调用一次 `createTask`。

偏离与决策：
- 浏览器自动化环境（IAB）不提供真实文件选择器；验收时在页面内构造 `File` 并通过 `DataTransfer` 赋给 `input[type=file]` 再触发 `change`，仍走真实的组件状态机、XHR、代理与后端。真实点击文件选择器的链路由原生 input + label 与组件测试覆盖。
- 详情页右栏只展示本次会话提交的任务；历史恢复、自动轮询、结果预览对话框属于 M7。

遗留 / 风险：
- 提交成功后保留已上传槽位（按钮恢复可用），用户可再次提交；服务端幂等键为 P2，未实现。
- 预览 URL 与上传请求的回收依赖 `useUploadSlots` 的卸载清理；React StrictMode 下已验证无重复上传（请求令牌按槽位递增）。

## 2026-10-28 · M7 任务跟踪、历史、刷新恢复（2.0 人日）

目标：把异步状态管理全部考点落地——多任务隔离、终态停轮询、查询异常 ≠ 任务失败、晚到响应丢弃、切换用户清理、刷新恢复与结果预览。

完成：
- `features/tasks/useTaskPolling.ts`：从 store 选 `PENDING/PROCESSING` 的 id；空集合直接返回，不排定时器；链式 `setTimeout` 在上一轮 `Promise.allSettled` 结束后再排下一轮；每个任务各自查询并合并；失败只 `setSync('error')`，保留 `status/stage/result`；终态使活跃集合变化，effect 清理统一 `abort + clearTimeout`；卸载、登出、`resetAll` 均走同一路径。`fetchTask/schedule/cancel` 可注入，测试不依赖真实网络与定时器。
- `useTaskHistory` + `TaskSync`：布局层首屏并发拉 `GET /api/tasks?page=0&size=50` 与 `/api/tasks/active`，写入 store 后由轮询继续；登录页不请求任务接口；历史失败保留已加载任务并给出重试。`TaskSync` 同时向页面提供 `refresh/reloadHistory/historyState`。
- `TaskCard`：等待/处理中（阶段文案 + 三阶段轨迹）/成功（结果图）/失败（失败阶段 + 原因）/查询异常（虚线提示 + 重试）分别渲染；结果图 `onError` 只显示占位，不改任务状态。
- `ResultDialog`：等价对话框实现（遮罩、`role=dialog`、Esc 关闭、焦点移入关闭按钮并在关闭后返回触发按钮、滚动锁定）。
- `/history`：状态统计、刷新列表、加载骨架、空态、错误重试；详情页右栏复用 `TaskPanel` 显示同一数据源。
- 切换用户：登录成功与登出都 `resetAll()`；轮询 cleanup 负责 abort 在途请求。
- 顶栏新增“历史”入口；`README` 更新路由、测试数与已知限制。

验证：
- `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`：lint 0、tsc 0、46 tests、6 条路由（新增 `/history`）。
- 浏览器（`127.0.0.1:3100`，3000 被本机其他应用占用）：3 个任务同时存在时 1 处理中 + 2 等待，状态/阶段/结果互不串扰；全部终态后 6 秒内代理日志无新增任务查询；处理中刷新 `/history` 后历史恢复且阶段继续推进；停后端 6 秒卡片保留 `PROCESSING/GENERATING` 并出现“状态刷新失败 + 重试”，重启后自动恢复并收口为 `FAILED/SERVER_RESTART`；alice 登录后历史为空，访问 demo 任务详情/结果/事件均 404；结果对话框可打开、焦点在关闭按钮、关闭后无残留；375 px 下 `scrollWidth=375` 无横向溢出。

偏离与决策：
- 每轮按任务并发 `GET /api/tasks/{id}`，不调用 `/active` 批量接口：单任务失败可精确落到该任务的 `sync`，且版本合并逻辑不变。见 D13。
- 查询异常后不停止轮询，固定 2 秒重试；卡片仍显示最后一次成功快照，恢复后自动继续，符合演示预期。
- 历史首屏固定 50 条单页，不做分页/无限滚动；M8 若补分页，先扩展 `useTaskHistory`。
- 对话框没有使用原生 `<dialog>`：`showModal` 在 jsdom 不可用，自绘实现便于单测焦点与 Esc 行为。

遗留 / 风险：
- 多标签页各自轮询，无跨标签同步；其它标签新建任务后需“刷新列表”或重新进入历史页。
- 轮询间隔固定 2 秒，没有指数退避；长时间断网时每 2 秒产生一次失败请求。
- 失败任务的阶段轨迹只标记失败阶段，不区分“之前的阶段已完成”与“之后未执行”的视觉差异（文案已给出失败阶段）。

## 2026-10-28 · M8 测试补齐与 CI（1.5 人日）

目标：把既有 46 + 103 个测试固化成可重复门禁，补上迁移/种子的真实数据库冒烟。

完成：
- `.github/workflows/ci.yml`：`frontend`（Node 22 + npm cache）、`backend`（Temurin 21 + Maven cache）、`migration-smoke`（MySQL 8.4 service + Flyway + SQL 冒烟）三个 job；push/PR/手动触发，按分支取消旧运行。
- `scripts/ci/migration-smoke.sh`：校验种子（用户 2、启用模板 6、`simulation_config` 1）、三张任务表存在；插入 PENDING → 条件更新认领成功 1 行 → 重复认领 0 行；SUCCEEDED 后再按 `status='PROCESSING'` 更新 0 行。
- `.gitattributes`：脚本/SQL/YAML 强制 LF；修正 `backend/mvnw` 的 git 可执行位，保证 Linux CI 可直接 `./mvnw`。
- `test-report.md` 补全 TC-BE-01～22、TC-FE-01～16 的“用例 → 测试类/方法 → 结果”矩阵与三遍运行记录。
- `README.md` 增加 CI 徽章、三 job 说明与本地等价命令；requirements-status 更新 R-12 与 G8。

验证（连续三次）：
- 前端：lint 0、typecheck 0、46 tests、build 6 路由，三次一致。
- 后端：`mvn -B verify` 103 tests、0 失败，三次一致。
- 迁移冒烟：每次重建 `xiaoying_ci` 库 → `flyway:migrate` 7 个迁移 → `flyway:validate` → 脚本全 PASS，三次一致。

偏离与决策：
- 本地 Maven 用完全限定 goal（`org.flywaydb:flyway-maven-plugin:migrate`）而不是 `flyway:migrate`：本机仓库缺少前缀元数据时后者会报 `No plugin found for prefix`；CI 同样使用完全限定形式，避免环境差异。
- 迁移冒烟 SQL 通过 `mysql:8.4` 容器执行，不在 runner 上安装 mysql 客户端；本地与 CI 共用同一脚本，只改 `DB_HOST/DB_PORT`。
- 本仓库没有 GitHub 远端，无法实际触发 Actions；G8 记为“本地等价通过，远端待触发”，推送后需确认首次三 job 全绿。

遗留 / 风险：
- CI 徽章中的 `OWNER/REPOSITORY` 是占位符，推送后必须替换。
- GitHub Actions 首次运行需要下载 npm/Maven 依赖，耗时会明显高于本地缓存运行。

## 2026-10-28 · M9 容器化、README 与交付（1.5 人日）

目标：把“能跑”变成“别人能干净跑起来”，并留下可复制的验收路径。

完成：
- `backend/Dockerfile`：`maven:3.9-eclipse-temurin-21` 构建 → `eclipse-temurin:21-jre-jammy` 运行；安装 curl 供健康检查；`app` 非 root；`/data` 归属应用用户；`HEALTHCHECK` 调 `/actuator/health`。
- `frontend/Dockerfile`：deps/build/runner 三阶段；`node:22-alpine`；复制 `output: "standalone"` 产物、`public` 与 `.next/static`；`node` 非 root；`PORT=3000`、`HOSTNAME=0.0.0.0`。
- `docker-compose.yml`：mysql（healthcheck）→ backend（`depends_on: service_healthy`，`app-data:/data`）→ frontend（`BACKEND_URL=http://backend:8080`，healthcheck `/login`）；端口均可通过 env 覆盖。
- `docker-compose.dev.yml`：用 `build: !reset null`、`ports/volumes: !override` 覆盖交付服务，保持 Maven 镜像热挂载开发；前端加 `profiles: ["full"]`，默认只起 MySQL + 后端。
- `.dockerignore`：排除 `node_modules/.next/target/data` 等；`.gitattributes` 已保证脚本/SQL/YAML 为 LF。
- README 按需求 10.2 重写为 20 节，含 16 项对照、配置表、接口与示例、演示步骤、身份设计、重启边界、部署回滚、投入与取舍、AI 使用、第三方来源、交付与压缩包排除。
- 更新 requirements-status（R-13/R-14 完成、G9 通过）、test-report G9 17 项验收证据、decisions D15/D16。

验证：
- `docker compose down -v` 后 `FRONTEND_PORT=3100 docker compose up --build -d`：mysql/backend/frontend 全部 healthy；`/actuator/health` UP；`/login` 200。
- 浏览器完整演示：登录、分类、上传 2 图、提交、三阶段、结果预览（1200×800）、3/8/3 秒耗时、失败注入、3 任务串行、刷新恢复、关闭标签、停/启后端、alice 隔离、375 px、键盘登录。
- 交付镜像与空卷重启均验证；验收后恢复默认耗时并清空失败注入。

偏离与决策：
- 前端 runner 直接使用 `node:22-alpine` 的 `node` 用户，不额外创建用户，减少镜像层；后端 JRE 镜像创建独立 `app` 用户。
- 开发编排使用 Compose 的 `!reset/!override`（需要 Compose v2.24+）。README 已注明前置条件；旧版 Compose 用户可只用交付编排。
- 本机 3000 被其它应用占用，验收通过 `FRONTEND_PORT=3100` 完成；默认配置不改，保证交付环境仍是 3000。

问题与修复：
- 首次前端镜像构建失败：`package-lock.json` 含 12 个 extraneous 的 `lightningcss` 平台包，Linux `npm ci` 报 `EBADPLATFORM (android-arm64)`。`npm prune` + `npm install --package-lock-only` 删除 262 行遗留条目后构建通过；本地单测与构建复跑仍 46/46。
- 首次启动时 3100 仍被上一轮 Next dev 子进程占用；终止残留 node 进程后启动成功。

遗留 / 风险：
- GitHub Actions 尚未实际触发（无远端），首次推送后需确认三 job 全绿。
- 交付镜像未做漏洞扫描与镜像大小优化；后端运行时安装 curl 增加了少量体积。
- 结果对话框未实现完整焦点陷阱；Esc 与焦点返回已覆盖。
