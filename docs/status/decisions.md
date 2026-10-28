# 决策记录

> 追加后不修改已定稿条目；每条含背景、备选项、结论、影响。

## D1 会话方案：服务端会话表 + HttpOnly Cookie（2026-10-28）

- 背景：需要演示多用户隔离，且不引入完整 Spring Security 过滤器链。
- 备选：JWT、OAuth、完整 Spring Security。
- 结论：32 字节随机令牌，SHA-256 存 `sessions`，Cookie `XIAOYING_SESSION`（HttpOnly、SameSite=Lax、Path=/、7 天）。
- 影响：可在服务端立即失效；库中无明文令牌；多实例需共享会话表（当前单实例假设）。

## D2 素材存储：本地数据卷 + 受控下载（2026-10-28）

- 背景：素材与结果需要落盘、可预览，且必须按归属校验。
- 备选：对象存储、直接静态目录暴露。
- 结论：写入 `{APP_DATA_DIR}/uploads/{userId}/{uuid}.{ext}`，数据库只存相对 key；读取走 `/api/materials/{id}/content`，先查归属再流式返回。
- 影响：无需外网依赖；越权与不存在统一 404；将来换对象存储只需替换 `StorageService`。

## D3 上传校验：文件头判定类型（2026-10-28）

- 背景：`Content-Type` 可伪造。
- 结论：只认 JPEG `FF D8 FF` 与 PNG `89 50 4E 47 0D 0A 1A 0A`，再用 `ImageIO` 解码取尺寸；解码失败返回 400 `MATERIAL_UNREADABLE`。
- 影响：上传阶段即拒绝伪造文件；完整业务校验仍留在 M3 的 `MATERIAL_CHECK`。

## D4 模拟配置单一来源：`simulation_config` 表（2026-10-28）

- 背景：需求 FR-SIM-10 要求运行期只有一个权威来源，避免配置文件与数据库同时生效。
- 备选：只读 `app.simulation.delays.*`；只读数据库；两者合并。
- 结论：运行期只读 `simulation_config` 单例表；`app.simulation.delays.*` 只在 M4 首次初始化该表时使用。`SimulationProperties` 因此推迟到 M4 与初始化逻辑一起实现，M1 不引入未使用的配置类。
- 影响：开发接口改耗时立即对新任务生效；已创建任务的失败计划仍由 `tasks.planned_fail_stage` 快照决定。

## D5 补回 SimulationProperties：只作初始化输入（2026-10-28）

- 背景：M1 计划要求 `SimulationProperties`；M1 先推迟（D4）以避免出现两处配置同时生效。
- 备选：继续不建配置类；让运行期直接读配置类。
- 结论：新增 `config/SimulationProperties` 绑定 `app.simulation.delays.*` 与 `dev-api-enabled`，仅作为 M4 首次初始化 `simulation_config` 的输入；阶段耗时与失败计划在运行期仍只读表。
- 影响：配置类存在但不参与运行期读取；M4 初始化必须先判断单例行是否存在，不能覆盖开发接口已修改的值。

## D6 框架级异常映射 415/405（2026-10-28）

- 背景：非 multipart 的 `POST /api/materials` 与不支持的方法原先落入兜底 `Exception` 处理器，返回 500。
- 备选：保持 500；把两者都映射成 400。
- 结论：新增 `UNSUPPORTED_MEDIA_TYPE`(415) 与 `METHOD_NOT_ALLOWED`(405) 错误码，并在 `GlobalExceptionHandler` 中映射 Spring 的 `HttpMediaTypeNotSupportedException` / `HttpRequestMethodNotSupportedException`；上传文件类型错误仍用 `MATERIAL_MIME_NOT_ALLOWED`。
- 影响：客户端错误不再显示为服务端错误；错误体结构与其他接口一致。

## D7 异步执行：DB 队列 + 信号量 + 有界线程池（2026-10-28）

- 背景：提交接口必须立即返回；任务状态要持久化、可观察、可恢复；题目不要求真正并行，串行队列即可。
- 备选：`@Async` 直接提交；MQ/Redis；数据库队列 + 调度器。
- 结论：`POST /api/tasks` 只插 `PENDING`；`@Scheduled` 轮询，条件更新原子认领，提交到 core=max=`app.pipeline.concurrency` 的有界线程池。调度器用信号量把“已认领未完成”的任务数限制在并发度内，其余保持 `PENDING`；提交被拒绝时回退 `PENDING`。
- 影响：等待态天然可见；重启可重新拾起 `PENDING`；串行时不会把所有任务都标成 `PROCESSING`。单实例假设，多实例会重复消费。

## D8 失败计划：创建时快照 + 单例表三种状态（2026-10-28）

- 背景：FR-SIM-05 要求失败计划在创建时确定并持久化，配置变更不得影响已创建任务；同时要支持“一次”和“持续”两种注入。
- 备选：worker 运行时读配置；给 `tasks` 加独立快照表；在 `simulation_config` 上用 `fail_stage` + `fail_remaining` 表达。
- 结论：任务创建事务内 `SELECT ... FOR UPDATE` 锁 `simulation_config` 单例行，把 `fail_stage` 写入 `tasks.planned_fail_stage`；`fail_remaining>0` 时计数减一，减到 0 同时清空 `fail_stage`；`fail_stage` 非空且 `remaining=0` 表示持续失败；显式 `failStage=null` 清空两者。
- 影响：一张表同时表达“计数模式/持续失败/已清空”；修改配置不影响已创建任务；worker 只读任务快照。

## D9 阶段证据：阶段字段 + `task_events` 事件表（2026-10-28）

- 背景：验收需要证明阶段顺序、失败中断和“先提交后等待”，而前端只需要当前阶段。
- 备选：只用 `tasks.stage`；只用事件表；两者并存。
- 结论：`tasks.stage` 供前端轮询；`task_events` 记录 `STAGE_STARTED/STAGE_SUCCEEDED/STAGE_FAILED/TASK_SUCCEEDED`，按自增 id 升序返回。阶段更新先独立提交，再 sleep，再执行工作。
- 影响：多一张表和每阶段写事件的开销；换来可追溯的阶段证据和 TC-BE-01～07 的可验证性。

## D10 结果生成：Java2D 拼图 + 受控下载（2026-10-28）

- 背景：题目不要求真实 AI，但结果必须可预览、可刷新后恢复。
- 备选：复制原图；调用外部图像服务；Java2D 本地拼图。
- 结论：`GENERATING` 用 Java2D 生成 1200×800 拼图，`ASSEMBLING` 编码 PNG 写入 `{APP_DATA_DIR}/results/{taskId}.png`，数据库存 `/api/tasks/{id}/result`；读取前校验 `SUCCEEDED` 与归属。
- 影响：无外网依赖；图片尺寸与布局固定；中文字体依赖运行环境，缺失时回退英文标题，不影响结果文件。
