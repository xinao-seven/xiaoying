# 素材制作网站 · 执行计划

> 文档版本：v1.0  
> 编写日期：2026-10-28  
> 依据：[requirements.md](./requirements.md) v1.1（需求唯一依据）  
> 读者：开发、测试、交付整理  
> 工期口径：单人全职人日（1 人日 = 6 小时有效工作）

---

## 0. 使用方式

- 本文是**施工计划**：回答"按什么顺序做、每步做完什么样算完成、怎么证明"。
- 需求判定标准以 `requirements.md` v1.1 为准；本文与需求文档冲突时，以需求文档为准，并回写本文。
- 每个阶段有出口门（G0～G9），**未过门不进入下一阶段**。出口门全部是命令或可观察结果，不是主观判断。
- 每个阶段必须同时产出三样东西：可运行代码、`docs/status/` 下的完成记录、通过门的 git tag。缺任意一项视为该阶段未完成。
- 任务优先级只取需求文档的 P0/P1；P2 一律不排期，时间富余时再议。

---

## 1. 执行总览

### 1.1 一句话目标

交付一个 `docker compose up --build` 即可启动的三服务系统（Next.js + Spring Boot + MySQL），完成"浏览模板 → 上传素材 → 提交 → 后台三阶段异步处理 → 前端轮询 → 历史与结果"的闭环，并附源码、迁移、种子、CI、README 与可复制的演示脚本。

### 1.2 里程碑

| 里程碑 | 名称 | 工期 | 依赖 | 出口门 |
|---|---|---|---|---|
| M0 | 立项与版本验证 | 0.5 | — | G0 |
| M1 | 后端地基：迁移、认证、错误模型 | 1.5 | M0 | G1 |
| M2 | 模板浏览与素材上传 | 1.0 | M1 | G2 |
| M3 | 任务受理与异步流水线 | 2.0 | M2 | G3 |
| M4 | 查询、历史、结果与模拟控制 | 1.0 | M3 | G4 |
| M5 | 前端地基与模板页面 | 1.5 | M2（模板接口） | G5 |
| M6 | 制作页：上传槽位与提交 | 2.0 | M5、M2 | G6 |
| M7 | 任务跟踪、历史、刷新恢复 | 2.0 | M4、M6 | G7 |
| M8 | 测试补齐与 CI | 1.5 | M3～M7 | G8 |
| M9 | 容器化、README、演示与交付 | 1.5 | M8 | G9 |
| — | 合计 | **14.5 人日** | — | 预留 1.5 人日缓冲 |

### 1.3 依赖与可并行点

```
M0 ─ M1 ─ M2 ─┬─ M3 ─ M4 ───────┐
              └─ M5 ─ M6 ────────┴─ M7 ─ M8 ─ M9
```

- M2 结束后，后端 M3→M4 与前端 M5 可并行；M5 的页面骨架也可在 M1 契约冻结后先用 mock 启动。
- M6 需要 M2 的上传接口和 M5 的页面骨架；M7 需要 M4 的查询接口和 M6 的提交链路。
- 单人执行时按 M0→M1→M2→M3→M4→M5→M6→M7→M8→M9 顺序，M5 可提前到 M3 之前插入，以尽早暴露前后端契约问题。

### 1.4 质量门定义

| 门 | 判定命令/动作 | 通过标准 |
|---|---|---|
| G0 | `npm run build`、`./mvnw -q -DskipTests package`、`docker compose up -d mysql && ./mvnw flyway:info` | 三处均成功；版本写入 README |
| G1 | `./mvnw verify` + curl 登录/登出/me | 测试绿；Cookie 属性正确；未登录 401 |
| G2 | curl 模板列表/详情/上传/取图 | 数据来自 MySQL；上传落盘；越权 404；413/415 可读 |
| G3 | curl 创建任务 + 查询 + 注入失败 | 立即返回；三阶段可观察；失败中断；终态不回退 |
| G4 | curl 历史/活跃/结果 + 模拟接口 | 分页正确；结果图可访问；查询前后 version 不变 |
| G5 | 浏览器打开 `/`、`/templates/{id}` | 分类筛选、卡片、详情、加载/空/错误态可用 |
| G6 | 浏览器完成上传与提交 | 槽位状态机正确；受理后立即出现任务卡片 |
| G7 | 浏览器多任务 + 刷新 + 停后端 | 互不串扰；刷新恢复；查询异常不置失败 |
| G8 | GitHub Actions 三 job | `frontend`、`backend`、`migration-smoke` 全绿 |
| G9 | 清空卷后 `docker compose up --build` + 9.4 全脚本 | 三服务健康；演示项全过；README 覆盖 R-13 |

---

## 2. 架构与关键决策（施工口径）

| # | 决策 | 施工要求 | 对应需求 |
|---|---|---|---|
| A1 | 数据库队列 + 调度器 + 有界线程池 | 提交只插 `PENDING`；`@Scheduled` 轮询认领；默认并发 1（串行），可配 | R-04、R-05 |
| A2 | 认领用条件更新 | `UPDATE tasks SET status='PROCESSING', stage='MATERIAL_CHECK', started_at=?, version=version+1 WHERE id=? AND status='PENDING'`，受影响行数=1 才提交线程池 | FR-PIPE-13 |
| A3 | 阶段先提交后等待 | 每个阶段：写 stage + version → 提交事务 → sleep → 执行 → 写事件；禁止先 sleep 再写 stage | FR-PIPE-02 |
| A4 | 失败计划创建时快照 | `fail_remaining>0` 时在创建事务内原子递减并消费；`fail_remaining=0` 且 `fail_stage` 非空时为持续失败模式。两种情况都把目标阶段写入 `tasks.planned_fail_stage`；worker 只读快照 | FR-SIM-05 |
| A5 | 模拟配置单一来源 | 运行时只读 `simulation_config` 表；`app.simulation.delays.*` 仅首次初始化 | FR-SIM-10 |
| A6 | 会话 Cookie + 拦截器 | 令牌 32 字节随机 → SHA-256 存 `sessions.token_hash`；Cookie `XIAOYING_SESSION`，`HttpOnly; SameSite=Lax; Path=/`；7 天 | R-10、NFR-SEC-01 |
| A7 | 素材/结果走受控下载 | 文件落 `{APP_DATA_DIR}` 卷；DB 存相对 key；`/api/materials/{id}/content` 与 `/api/tasks/{id}/result` 先校验归属再流式返回 | FR-MAT-09、FR-QUERY-04 |
| A8 | 前端同源代理 | `app/api/[...path]/route.ts` 转发到 `BACKEND_URL`，透传 Cookie 与 `Set-Cookie`，不承载业务逻辑 | FR-TECH-04 |
| A9 | 状态管理 | Redux Toolkit 2.x，`tasks` slice 按 id 归一化；轮询在页面级 hook，不进 store | R-07、R-08 |
| A10 | 事件表作为阶段证据 | `task_events` 记录 `STAGE_STARTED/STAGE_SUCCEEDED/STAGE_FAILED/TASK_SUCCEEDED` | FR-PIPE-01 验收 |
| A11 | 结果生成为 Java2D 拼图 | 读取 N 张素材 + caption，输出 1200×800 PNG；非真实 AI | R-04、R-05 |
| A12 | 查询严格只读 | 查询 Service 不调用任何写方法；Mapper 层查询 SQL 仅 SELECT | FR-PIPE-08 |

**提交时序（A1/A2/A3/A4）**

```
POST /api/tasks
  ├─ 校验（顺序见需求 3.4）
  ├─ 事务：插 tasks(PENDING) + task_materials + 快照 planned_fail_stage
  └─ 返回 201 TaskDTO                      ← 不等待任何阶段

Dispatcher（每 1s）
  ├─ SELECT id FROM tasks WHERE status='PENDING' ORDER BY created_at LIMIT n
  ├─ 对每个 id 条件更新认领（A2），affected=1 才 submit 到线程池
  └─ 线程池（默认 1 线程）

Worker
  for stage in [MATERIAL_CHECK, GENERATING, ASSEMBLING]:
      if planned_fail_stage == stage: markFailed(...); return
      updateStage(stage) [提交] → sleep(delay) → doStage() → 事件
      doStage 失败: markFailed(...); return
  markSucceeded(resultUrl, DONE)
```

---

## 3. 阶段详解

每个里程碑包含：目标、任务清单、产出、出口条件、**文档与提交**、风险。文档模板见第 14 节，提交规范与修复流程见第 15 节。

### M0 立项与版本验证（0.5 人日）

**目标**：证明所选版本互相兼容，锁定依赖，避免后期返工。

任务：

- [ ] 建目录：`frontend/`、`backend/`、`docs/`、`.github/workflows/`
- [ ] `frontend`：Next.js 15 App Router + TypeScript strict + ESLint；`package.json` 脚本：`dev/build/start/lint/typecheck/test`
- [ ] `backend`：Spring Boot 3.5.x + Java 21 + `mybatis-spring-boot-starter` + `flyway-core`/`flyway-mysql` + `mysql-connector-j` + `spring-security-crypto` + `actuator` + `validation`；生成 Maven Wrapper
- [ ] 建 `docs/requirements.md`、`docs/execution-plan.md`
- [ ] `docker-compose.yml` 骨架只含 `mysql:8.4`，验证 `flyway:info` 可连
- [ ] README 骨架：版本表、目录结构、待填章节清单
- [ ] 记录实际解析到的版本（`npm ls`、`./mvnw dependency:tree`）

产出：可编译的空项目、锁文件（`package-lock.json`）、`pom.xml`、wrapper、compose 骨架。

出口条件（G0）：

1. `npm run build` 成功。
2. `./mvnw -q -DskipTests package` 成功。
3. `./mvnw flyway:info` 连上 compose 中的 MySQL 8.4。
4. 版本与需求 2.1 一致或已在 README 记录偏差。

风险与对策：

- Next 15 + React 19 peer 依赖冲突 → 用 `npm ci` 验证干净安装，不接受 `--force`。
- Spring Boot 3.5 与 MyBatis starter 版本不匹配 → 先跑一个 `@Mapper` 空接口 + `@SpringBootTest`。
- Flyway 11 对 MySQL 8.4 需 `flyway-mysql` 模块 → G0 必须实际迁移一次。

**文档与提交**

- 文档：创建 `docs/status/` 四件套空模板（`requirements-status.md`、`dev-log.md`、`test-report.md`、`decisions.md`）；README 写入版本表与目录结构。
- 提交：`chore(repo): 初始化前后端骨架并锁定版本` → `docs(status): 建立完成记录与开发日志模板` → 打 tag `gate-m0`。

---

### M1 后端地基：迁移、认证、错误模型（1.5 人日）

**目标**：数据库可重建、认证可闭环、错误响应可解析。后续所有接口都建立在这三件事上。

任务：

- [ ] Flyway 迁移 V1～V7（顺序见第 5 节），本地执行通过
- [ ] 统一错误模型：`ErrorCode` 枚举、`ApiException`、`GlobalExceptionHandler`（含 `MaxUploadSizeExceededException` → 413）
- [ ] 会话：`SessionService`（签发/校验/删除/过期清理）、`CookieFactory`
- [ ] `AuthInterceptor` + `CurrentUserArgumentResolver`；放行 `/api/auth/login`、`/api/templates/**`、`/actuator/health`
- [ ] `POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`
- [ ] 配置类：`AppProperties`（数据目录、并发、轮询间隔）、`SimulationProperties`（初始耗时）
- [ ] 种子：2 用户（BCrypt）、3 分类 6 模板、`simulation_config` 单例行
- [ ] 健康检查暴露 `health`

产出：迁移脚本、认证三接口、错误码表、种子数据。

出口条件（G1）：

1. `./mvnw verify` 绿。
2. 错误密码返回 401 且响应为 JSON（`code/message/traceId`）。
3. 登录后 `Set-Cookie` 含 `HttpOnly`、`SameSite=Lax`、`Path=/`、`Max-Age=604800`。
4. 登出后携带旧 Cookie 访问 `/api/auth/me` 返回 401。
5. 重复执行迁移在空库上成功（`docker compose down -v` 后重来）。

测试：`SessionServiceTest`、`AuthControllerTest`（MockMvc）、`MigrationSmokeIT`（可选，本地跑）。

风险：拦截器路径匹配漏配导致模板接口 401 → 用 MockMvc 覆盖放行清单。

**文档与提交**

- 文档：`requirements-status.md` 勾选 R-01、R-10 的认证与隔离部分；`dev-log.md` 记录版本兼容结论与迁移执行结果；`test-report.md` 记录 G1 的五条命令与输出。
- 提交：`feat(db): 建立 V1-V7 迁移与演示种子` → `feat(auth): 会话登录登出与归属拦截` → `feat(common): 统一错误码与异常响应` → `test(auth): 覆盖会话、Cookie 属性与 401` → `docs(status): M1 完成记录` → 打 tag `gate-m1`。

---

### M2 模板浏览与素材上传（1.0 人日）

**目标**：模板数据从 MySQL 经 Java 输出；素材可上传、可落盘、可按归属取回。

任务：

- [ ] `TemplateMapper` / `TemplateService` / `TemplateController`
  - `GET /api/templates/categories`（分类 + 数量）
  - `GET /api/templates?category=`（仅 `enabled=1`，按 `sort_order, id`）
  - `GET /api/templates/{id}`（不存在/未启用 → 404）
- [ ] 示例封面：`frontend/public/demo/*.png`（6 张，自生成，README 注明来源）
- [ ] `POST /api/materials`：`multipart/form-data` 字段 `file`
  - 全局上限 10 MB；按模板默认类型校验（上传时不知道模板，按全局允许 `image/jpeg|png`）
  - 文件头检测（JPEG `FF D8 FF`，PNG `89 50 4E 47 0D 0A 1A 0A`）
  - `ImageIO.read` 取宽高，失败 → 400 `MATERIAL_UNREADABLE`
  - 落盘 `{APP_DATA_DIR}/uploads/{userId}/{uuid}.{ext}`；写 `materials` 行
- [ ] `GET /api/materials/{id}/content`：归属不符 → 404；返回 `Content-Type` + `Cache-Control: private`
- [ ] 素材状态机（DB 侧只有 `UPLOADED`；前端槽位状态机在 M6 实现）

产出：3 个模板接口、2 个素材接口、封面资源、素材表数据。

出口条件（G2）：

1. `curl /api/templates` 返回 6 条，`/api/templates?category=创意图片` 只返回该分类。
2. 上传 PNG 返回 201 且文件出现在数据卷；伪造 `.png` 的文本文件返回 415/400。
3. 用户 B 取用户 A 的素材 URL 返回 404。
4. 超过 10 MB 返回 413 JSON，不是 500 空白页。

测试：`TemplateControllerTest`、`MaterialUploadServiceTest`（fake storage）、`MaterialControllerTest`（413/415/归属）。

风险：`ImageIO` 对大图解码耗时 → 上传接口只解码取尺寸，完整校验留到 `MATERIAL_CHECK` 阶段。

**文档与提交**

- 文档：更新 R-02、R-03 状态；`dev-log.md` 记录文件头检测、413 处理与封面素材来源；`decisions.md` 记录素材存储与受控下载方案。
- 提交：`feat(template): 分类列表与模板详情接口` → `feat(material): 上传落盘与受控读取` → `test(material): 覆盖 413/415/不可解码/越权` → `docs(status): M2 完成记录` → 打 tag `gate-m2`。

---

### M3 任务受理与异步流水线（2.0 人日）

**目标**：本题核心。任务受理立即返回、三阶段顺序执行、失败中断、终态不可回退、查询不可推进。

任务：

- [ ] `TaskMapper`：`insertTask`、`insertTaskMaterial`、`claim`、`updateStage`、`markFailed`、`markSucceeded`、`bumpVersion`、查询
- [ ] `TaskCreateService`：按需求 3.4 的 10 步校验顺序；事务内快照失败计划；返回完整 DTO
- [ ] `TaskDispatcher`：`@Scheduled(fixedDelayString="${app.pipeline.poll-interval-ms:1000}")`，批量取 `PENDING`，逐个原子认领，提交线程池；拒绝时回退 `PENDING` 或标 `QUEUE_REJECTED`
- [ ] `TaskPipelineRunner`：三阶段 + `Sleeper` 接口 + `ResultComposer` 接口；每阶段写 stage → 提交 → sleep → 执行 → 事件
- [ ] `MATERIAL_CHECK`：文件存在、`ImageIO` 可解码、MIME/大小与模板一致
- [ ] `GENERATING`：按配置耗时等待；命中 `planned_fail_stage` 则失败；否则生成结果
- [ ] `ASSEMBLING`：写结果文件、记录 `result_url`/宽高；失败 → `RESULT_WRITE_FAILED`
- [ ] worker 顶层 `try/catch(Throwable)` → `INTERNAL_ERROR`，绝不留 `PROCESSING`
- [ ] `RestartReconciler`（`ApplicationRunner`）：启动时把残留 `PROCESSING` 标 `FAILED/SERVER_RESTART`
- [ ] 线程池配置 `app.pipeline.concurrency`（默认 1）、队列 100

产出：`POST /api/tasks`、调度器、流水线、结果文件、事件记录。

出口条件（G3）：

1. 创建接口在生成耗时 8 s 时仍毫秒级返回 201。
2. 关闭前端（只用 curl）任务照常走完三阶段。
3. 只重复调用 `GET /api/tasks/{id}` 不会推进阶段（对比 `version` 不变）。
4. 注入 `GENERATING` 失败：`failedStage=GENERATING`，`task_events` 无 `ASSEMBLING`。
5. 对终态任务再次触发 worker 无任何变化、无新事件。
6. 执行中 kill 后端再启动，残留任务变 `FAILED/SERVER_RESTART`，不永久卡住。

测试：`TaskPipelineRunnerTest`（TC-BE-01～07、16～18）、`TaskCreateServiceTest`（TC-BE-09～14）、`TaskClaimServiceTest`（TC-BE-15）、`RestartReconcilerTest`（TC-BE-21/22）。全部使用 fake repository + fake `Sleeper` + fake `Clock`。

风险：

- 阶段更新与 sleep 顺序写反 → 用 fake sleeper 在等待中读 repository 断言（TC-BE-07）。
- 线程池拒绝导致任务无主 → 认领后立即 `try submit catch` 回退，写测试覆盖。
- 事务边界把 sleep 包进事务 → 连接池耗尽；`@Transactional` 只包写操作，sleep 在事务外。

**文档与提交**

- 文档：更新 R-04、R-05（核心项，附一次完整任务的 `task_events` 查询输出）；`dev-log.md` 记录认领竞态、事务边界与线程池拒绝的处理；`decisions.md` 记录 DB 队列 vs MQ、事件表 vs 仅阶段字段。
- 提交：`feat(task): 创建任务与十步校验链` → `feat(pipeline): 调度器与原子认领` → `feat(pipeline): 三阶段执行与失败中断` → `feat(pipeline): 重启对账与异常兜底` → `test(pipeline): 阶段顺序、失败中断、终态幂等` → `docs(status): M3 完成记录` → 打 tag `gate-m3`。

---

### M4 查询、历史、结果与模拟控制（1.0 人日）

**目标**：把后端状态变成前端可用的读接口，并提供可重复的演示开关。

任务：

- [ ] `GET /api/tasks/{id}`：归属校验（他人 → 404）；只读
- [ ] `GET /api/tasks?page=&size=`：按 `created_at DESC`，返回 `items/page/size/total`
- [ ] `GET /api/tasks/active`：仅当前用户 `PENDING/PROCESSING`
- [ ] `GET /api/tasks/{id}/result`：仅 `SUCCEEDED` 且本人；否则 404；`Cache-Control: private, max-age=60`
- [ ] `GET /api/tasks/{id}/events`（P1）：按 `id` 升序
- [ ] `SimulationService` + `DevSimulationController`：`GET/POST /api/dev/simulation`；受 `app.simulation.dev-api-enabled` 控制，关闭时 404
- [ ] 运行时改耗时：写 `simulation_config`，新任务按新值执行
- [ ] 失败注入：`failStage` + `failNext`（计数模式）；`failNext=0` 且 `failStage` 非空为持续失败模式；`failStage=null` 清空

产出：4 个读接口、2 个开发接口、模拟配置读写。

出口条件（G4）：

1. 三个任务分页 `total=3`，顺序为创建时间倒序。
2. `active` 只含非终态任务。
3. 成功任务结果 URL 返回 PNG；失败任务结果 URL 返回 404。
4. 查询前后 `version`、`updated_at` 不变。
5. `POST /api/dev/simulation {"delayGeneratingMs":8000}` 后新任务生成阶段约 8 s。
6. `failStage=GENERATING, failNext=1` 连续提交两次：第一次失败、第二次成功。
7. `failStage=GENERATING, failNext=0`（持续失败模式）连续提交两个任务均失败；`failStage=null` 清空后恢复成功。

测试：`TaskQueryServiceTest`（TC-BE-08/19/20）、`SimulationServiceTest`（快照与计数消费）。

**文档与提交**

- 文档：更新 R-05、R-06、R-09 的查询与模拟部分；`test-report.md` 记录 G4 七条验证；`dev-log.md` 记录"持续失败模式"的语义决定。
- 提交：`feat(task): 详情、历史、活跃与结果接口` → `feat(simulation): 开发接口与失败计划快照` → `test(query): 查询只读与用户隔离` → `docs(status): M4 完成记录` → 打 tag `gate-m4`。

---

### M5 前端地基与模板页面（1.5 人日）

**目标**：同源代理、认证、状态管理与模板浏览跑通，为制作页提供骨架。

任务：

- [ ] `app/api/[...path]/route.ts`：转发到 `BACKEND_URL`，透传 Cookie、`Set-Cookie`、`Content-Type`；上传走 `arrayBuffer` 转发
- [ ] `lib/api/client.ts`：统一 `fetch` 包装；401 → 清空 store + 跳 `/login`；错误体解析为 `ApiError`
- [ ] `/login` 页面：用户名/密码、错误文案、提交中禁用
- [ ] `app/layout.tsx`：Redux `Provider`、顶栏（产品名、用户名、登出）；`middleware.ts` 仅做"无 Cookie 跳登录"的粗判
- [ ] `store/tasksSlice.ts`：`byId`、`order`、`upsertTask`（按 `version` 合并）、`setSync`、`resetAll`
- [ ] `/` 模板列表：分类栏（含"全部"）、卡片网格、加载骨架、空态、错误重试
- [ ] `/templates/[id]`：标题/描述/封面/素材要求/上传区占位、404 反馈
- [ ] 分类快速切换时丢弃旧响应（`AbortController` + 请求序号）
- [ ] 基础样式：CSS 变量、深浅色对比、375 px 可用

产出：可登录、可浏览模板的前端。

出口条件（G5）：

1. `npm run lint`、`npm run typecheck`、`npm run build` 全过。
2. 断网/停后端时列表进入错误态，不显示写死数据。
3. 快速切换分类 5 次，最终列表与选中分类一致。
4. 不存在的模板显示"模板不存在"+ 返回列表。
5. 375 px 无横向滚动。

测试：`TemplateList.test.tsx`（TC-FE-15/16）、`tasksSlice.test.ts` 初版（version 合并）。

风险：Next 构建期请求后端 → 列表/详情用 Client Component 或 `dynamic = 'force-dynamic'`，CI 无后端也能 build。

**文档与提交**

- 文档：更新 R-02、R-11 的页面部分；`dev-log.md` 记录"构建期不依赖后端"的做法与代理透传 Cookie 的验证；`decisions.md` 记录 Redux Toolkit 选型。
- 提交：`feat(web): 同源代理与登录页` → `feat(web): 模板列表、分类筛选与详情` → `feat(web): tasks slice 与版本合并` → `test(web): 列表异常态与模板 404` → `docs(status): M5 完成记录` → 打 tag `gate-m5`。

---

### M6 制作页：上传槽位与提交（2.0 人日）

**目标**：R-03 的交互全部落地；提交成功后立刻看到任务，而不是停在"提交中"。

任务：

- [ ] `UploadSlot` 状态机：`EMPTY → SELECTED → UPLOADING → UPLOADED`，`ERROR` 可重试，任意态可移除，`UPLOADED` 可替换
- [ ] 本地预览：`URL.createObjectURL`，替换/移除/卸载时 `revokeObjectURL`
- [ ] 客户端校验：数量、MIME、大小；错误定位到具体槽位
- [ ] 选择即上传，槽位级进度/失败原因；失败可单独重试，不影响其他槽位
- [ ] `SubmitPanel`：所有槽位 `UPLOADED` 才可提交；提交中禁用按钮；`submitting` 与 `uploading` 分离
- [ ] 提交成功：`upsertTask(response)` → 清空 `submitting` → 触发跟踪（M7 接上）
- [ ] 提交失败：保留表单与素材，展示表单级错误
- [ ] 401：清理并跳登录

产出：制作页完整交互。

出口条件（G6）：

1. 2 图模板显示 2 个槽位；槽位编号清晰。
2. 选择图片立即预览；替换后旧图消失；移除后槽位为空。
3. 超数量/错类型/超大小在选中时提示，不发请求。
4. 上传失败槽位显示原因，点重试只重传该槽位。
5. 连续点击提交只产生 1 个任务。
6. 受理成功后按钮恢复、任务卡片立即出现。

测试：`UploadSlot.test.tsx`（TC-FE-11/12）、`SubmitPanel.test.tsx`（TC-FE-09/10）。

风险：`<img src="blob:">` 忘记 revoke 导致内存泄漏 → 在 `useEffect` 清理函数统一 revoke；测试中断言。

**文档与提交**

- 文档：更新 R-03、R-07 的上传与提交部分；`dev-log.md` 记录槽位状态机与 blob 回收；`test-report.md` 记录 G6 六条验证。
- 提交：`feat(web): 上传槽位状态机与客户端校验` → `feat(web): 提交流程与受理后插入任务` → `test(web): 上传校验、槽位重试与防重复提交` → `docs(status): M6 完成记录` → 打 tag `gate-m6`。

---

### M7 任务跟踪、历史、刷新恢复（2.0 人日）

**目标**：前端异步状态管理的全部考点：多任务隔离、终态停轮询、查询异常≠失败、晚到响应丢弃、切换用户清理、刷新恢复。

任务：

- [ ] `useTaskPolling`：
  - 从 store 选 `PENDING/PROCESSING` 的 id
  - 空集合 → 清除定时器，不再发请求
  - 链式 `setTimeout`，上一轮全部结束再排下一轮
  - 每轮按任务并发查询（或调用 `/api/tasks/active` 批量）
  - 响应按 `id + version` 合并，旧版本丢弃
  - 单任务失败 → 只置 `sync.state='error'`，保留 `status/stage/result`
  - 终态立即移出跟踪集合
  - 卸载 / 登出 / 切用户 → `AbortController.abort()` + `clearTimeout`
- [ ] `TaskCard`：等待/处理中（阶段文案）/成功（结果图）/失败（阶段+原因）/查询错误（"状态刷新失败，重试"）
- [ ] 结果预览对话框：`<dialog>` 或等价实现，Esc 关闭，焦点返回触发按钮
- [ ] 历史区：详情页右侧面板 + `/history` 页复用列表
- [ ] 刷新恢复：首屏拉历史 + active，恢复后继续跟踪
- [ ] 切换用户：`resetAll()`，旧请求 abort
- [ ] 结果图 404：显示占位，不改任务状态

产出：可稳定跟踪多任务的任务中心。

出口条件（G7）：

1. 3 个任务并发，状态/阶段/错误/结果互不串扰。
2. 任务成功后 1 个轮询周期内停止请求（网络面板验证）。
3. 无活跃任务时无周期请求。
4. 停后端：卡片保留最后状态 + 重试提示；恢复后自动继续。
5. 处理中刷新页面：历史恢复且阶段继续变化。
6. demo 登出、alice 登录：看不到 demo 任务；直接访问其 URL 返回 404。
7. 晚到旧 `version` 响应不覆盖新状态。

测试：`useTaskPolling.test.tsx`（TC-FE-04/05/07/08/13）、`tasksSlice.test.ts`（TC-FE-01/06）、`TaskCard.test.tsx`（TC-FE-02/03/14）。

风险：fake timers 与 `fetch` mock 交叉导致挂起 → 统一用 `vi.useFakeTimers()` + `await vi.runOnlyPendingTimersAsync()`；轮询 hook 接受注入的 `fetcher` 与 `scheduler`，测试不依赖真实网络。

**文档与提交**

- 文档：更新 R-07、R-08、R-09 的跟踪与恢复部分；`decisions.md` 记录轮询 vs SSE；`dev-log.md` 记录晚到响应与卸载清理的复现方式；`test-report.md` 记录 G7 七条验证。
- 提交：`feat(web): 页面级轮询与按 version 合并` → `feat(web): 任务卡片、历史与结果预览` → `feat(web): 刷新恢复与用户切换清理` → `test(web): 多任务隔离、查询异常、生命周期` → `docs(status): M7 完成记录` → 打 tag `gate-m7`。

---

### M8 测试补齐与 CI（1.5 人日）

**目标**：把核心行为固化成可重复的自动化证据，并把 CI 变成真正的门禁。

任务：

- [ ] 对照第 7 节矩阵补齐缺失用例；每条用例注释写 TC 编号
- [ ] 删除不稳定断言（真实 sleep、真实网络、随机）
- [ ] `.github/workflows/ci.yml` 三个 job（见第 8 节）
- [ ] `migration-smoke`：MySQL service container + Flyway migrate + 种子 + 认领 SQL 冒烟
- [ ] 连续触发 3 次，确认无 flaky
- [ ] README 写入 CI 徽章与本地等价命令

出口条件（G8）：

1. `frontend`、`backend`、`migration-smoke` 全绿。
2. 从干净 checkout 重跑仍绿。
3. 核心用例（TC-BE-01～08、TC-FE-01～10）全部存在且通过。
4. 本地 `npm run lint && npm run typecheck && npm test && npm run build` 与 `./mvnw verify` 与 CI 一致。

风险：GitHub Actions 拉取 Maven/npm 慢 → 开启两个缓存；迁移 job 加 `--health-cmd` 等待 MySQL 就绪。

**文档与提交**

- 文档：`test-report.md` 全量登记 TC-BE-01～22、TC-FE-01～16 的结果与 CI 链接；`requirements-status.md` 更新 R-12；`dev-log.md` 记录 flaky 用例与去抖方式。
- 提交：`test(be): 补齐 TC-BE-18..22 与迁移冒烟` → `test(fe): 补齐 TC-FE-03/14/16` → `ci: 三 job 流水线与迁移冒烟` → `docs(status): M8 完成记录与 CI 链接` → 打 tag `gate-m8`。

---

### M9 容器化、README、演示与交付（1.5 人日）

**目标**：把"能跑"变成"别人能跑"，并留下可复制的验收路径。

任务：

- [ ] `backend/Dockerfile`：`maven:3.9-eclipse-temurin-21` 构建 → `eclipse-temurin:21-jre` 运行；非 root 用户
- [ ] `frontend/Dockerfile`：`node:22-alpine` 构建（`output: 'standalone'`）→ 运行
- [ ] `docker-compose.yml`：mysql（healthcheck）→ backend（`depends_on: service_healthy`）→ frontend
- [ ] 卷：`mysql-data`、`app-data`（uploads + results）
- [ ] `.env.example`、`.dockerignore`、`.gitignore`
- [ ] README 按需求 10.2 的 16 项逐条填写
- [ ] 清空卷后完整跑一遍 9.4 演示脚本，记录结果
- [ ] 打 tag / 记录验收 commit；整理压缩包（排除依赖与构建产物）

出口条件（G9）：

1. `docker compose down -v && docker compose up --build -d` 后三服务健康。
2. 9.4 全部小节按预期通过（含失败注入、查询异常、用户隔离、375 px）。
3. README 覆盖 10.2 全部 16 项，命令可直接复制执行。
4. 仓库无真实凭据、无外网运行时依赖。

**文档与提交**

- 文档：`requirements-status.md` 收口 R-01～R-14（未验证项写明原因）；`dev-log.md` 收尾；`test-report.md` 补齐手工用例结果；`decisions.md` 合并进 README 的取舍表；README 写入实际投入、已知限制、验收 commit。
- 提交：`build: 前后端 Dockerfile 与 compose 编排` → `docs: README 覆盖 R-13 十六项` → `docs(status): 交付快照、投入记录与验收 commit` → 打 tag `v1.0-submission`。
- 交付前检查：仓库无 `.env` 真实值、无 `node_modules/target/.next`、tag 指向的 commit 能干净构建。

---

## 4. 接口清单

### 4.1 通用约定

| 项 | 约定 |
|---|---|
| 基础路径 | `/api`（Next 同源代理到 Java） |
| 内容类型 | JSON `application/json; charset=utf-8`；上传 `multipart/form-data` |
| 字段命名 | camelCase |
| 时间 | ISO-8601 UTC，毫秒精度，如 `2026-10-28T10:00:00.000Z` |
| ID | 字符串：`usr_` / `mat_` / `tsk_` 前缀 + ULID（共 30 字符），与库表 VARCHAR(36) 匹配 |
| 认证 | Cookie `XIAOYING_SESSION` |
| 分页 | `page` 从 0 开始，`size` 默认 20、最大 100 |
| 错误体 | `{ code, message, fieldErrors?, traceId }` |
| 越权 | 资源归属不符与不存在统一 404 |

### 4.2 端点总表

| # | 方法 | 路径 | 认证 | 请求 | 成功响应 | 主要错误 | 实现阶段 |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 否 | `{username,password}` | 200 `{user}` + Set-Cookie | 401 `INVALID_CREDENTIALS` | M1 |
| 2 | POST | `/api/auth/logout` | 是 | — | 204 + 清 Cookie | 401 | M1 |
| 3 | GET | `/api/auth/me` | 是 | — | 200 `{id,username,displayName}` | 401 | M1 |
| 4 | GET | `/api/templates/categories` | 否 | — | 200 `{items:[{name,count}]}` | 500 | M2 |
| 5 | GET | `/api/templates` | 否 | `?category=` | 200 `{items:[Template]}` | 500 | M2 |
| 6 | GET | `/api/templates/{id}` | 否 | — | 200 `Template` | 404 `TEMPLATE_NOT_FOUND` | M2 |
| 7 | POST | `/api/materials` | 是 | multipart `file` | 201 `Material` | 413 `PAYLOAD_TOO_LARGE`、415 `MATERIAL_MIME_NOT_ALLOWED`、400 `MATERIAL_UNREADABLE` | M2 |
| 8 | GET | `/api/materials/{id}/content` | 是 | — | 200 图片流 | 404 | M2 |
| 9 | POST | `/api/tasks` | 是 | `{templateId,materialIds,params?}` | 201 `Task` | 400/404（见 4.5） | M3 |
| 10 | GET | `/api/tasks/{id}` | 是 | — | 200 `Task` | 404 | M4 |
| 11 | GET | `/api/tasks` | 是 | `?page=&size=` | 200 `{items,page,size,total}` | 400 | M4 |
| 12 | GET | `/api/tasks/active` | 是 | — | 200 `{items:[Task]}` | 401 | M4 |
| 13 | GET | `/api/tasks/{id}/result` | 是 | — | 200 图片流 | 404 | M4 |
| 14 | GET | `/api/tasks/{id}/events` | 是 | — | 200 `{items:[Event]}` | 404 | M4（P1） |
| 15 | GET | `/api/dev/simulation` | 开发开关 | — | 200 `SimulationConfig` | 404（未开启） | M4 |
| 16 | POST | `/api/dev/simulation` | 开发开关 | `{failStage?,failNext?,delay*Ms?}` | 200 `SimulationConfig` | 400/404 | M4 |
| 17 | GET | `/actuator/health` | 否 | — | 200 `{status:"UP"}` | — | M1 |

### 4.3 关键 DTO

**Task**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 任务 ID |
| `templateId` / `templateTitle` | string | 模板标识与标题 |
| `status` | `PENDING\|PROCESSING\|SUCCEEDED\|FAILED` | 整体状态 |
| `stage` | `MATERIAL_CHECK\|GENERATING\|ASSEMBLING\|DONE\|null` | 当前阶段；`PENDING` 时为 null |
| `failedStage` | stage 或 null | 失败阶段 |
| `errorCode` / `errorMessage` | string 或 null | 稳定错误码与可读原因 |
| `resultUrl` | string 或 null | 成功后为 `/api/tasks/{id}/result` |
| `version` | int | 每次写入 +1，前端用于丢弃旧响应 |
| `createdAt` / `startedAt` / `finishedAt` | ISO 时间或 null | 时间线 |
| `sync` | 客户端派生 | 不属于服务端响应 |

**Template**

`id, title, category, coverUrl, description, requiredImageCount, acceptedMimeTypes[], maxFileSizeBytes`

**Material**

`id, url, mimeType, sizeBytes, width, height, createdAt`

**SimulationConfig**

`failStage, failRemaining, delayMaterialCheckMs, delayGeneratingMs, delayAssemblingMs, updatedAt`

**错误体**

```json
{
  "code": "MATERIAL_COUNT_MISMATCH",
  "message": "需要 2 张图片，当前 1 张",
  "fieldErrors": { "materialIds": "数量不匹配" },
  "traceId": "req_01J..."
}
```

### 4.4 状态码

| 码 | 使用场景 |
|---|---|
| 200 | 查询/更新成功 |
| 201 | 创建任务、上传素材 |
| 204 | 登出 |
| 400 | 参数/业务校验失败（数量、重复、参数非法、图片不可解码） |
| 401 | 未登录/会话过期/凭据错误 |
| 404 | 模板/任务/素材不存在或不属于当前用户；开发接口未开启 |
| 413 | 超过模板或全局大小上限 |
| 415 | MIME 不在允许列表 |
| 500 | 未预期错误（`INTERNAL_ERROR`） |
| 503 | 队列拒绝（`QUEUE_REJECTED`，仅日志/监控使用） |

### 4.5 创建任务校验顺序与错误码

| 序 | 校验 | 失败返回 |
|---|---|---|
| 1 | 登录态 | 401 |
| 2 | 模板存在且 enabled | 404 `TEMPLATE_NOT_FOUND` |
| 3 | `materialIds` 数量 = `requiredImageCount` | 400 `MATERIAL_COUNT_MISMATCH` |
| 4 | 无重复 id | 400 `DUPLICATE_MATERIAL` |
| 5 | 每个素材属于当前用户 | 404 `MATERIAL_FORBIDDEN` |
| 6 | 素材存在且 `UPLOADED` | 404 `MATERIAL_NOT_FOUND` |
| 7 | MIME 在模板允许列表 | 415 `MATERIAL_MIME_NOT_ALLOWED` |
| 8 | 大小不超模板上限 | 413 `MATERIAL_TOO_LARGE` |
| 9 | `params` 合法（未知字段/超长拒绝） | 400 `INVALID_PARAMS` |
| 10 | 写 `tasks` + `task_materials`，快照失败计划 | — |

### 4.6 代理与静态资源

- 浏览器只访问 `http://localhost:3000`，所有 `/api/*` 由 Next Route Handler 转发到 `BACKEND_URL`（compose 内为 `http://backend:8080`）。
- 上传转发用 `arrayBuffer`（≤10 MB，无需流式）；必须透传 `Cookie` 与响应 `Set-Cookie`。
- 封面用 `frontend/public/demo/*.png`，同源静态资源，不经后端。
- 素材/结果图片用 `<img src="/api/...">`，Cookie 自动携带，无需 blob。

---

## 5. 数据模型与迁移

### 5.1 表清单

| 表 | 作用 | 关键索引 |
|---|---|---|
| `users` | 账号 | `username` UNIQUE |
| `sessions` | 服务端会话 | PK `token_hash`；`user_id` |
| `templates` | 模板 | PK `id`；`category` |
| `materials` | 素材 | PK `id`；`(user_id, created_at)` |
| `tasks` | 任务 | PK `id`；`(user_id, created_at DESC)`；`(status, created_at)` |
| `task_materials` | 任务-素材槽位 | PK `(task_id, material_id)`；UNIQUE `(task_id, slot_index)` |
| `task_events` | 阶段事件 | `(task_id, id)` |
| `simulation_config` | 模拟配置单例 | PK 固定 1 |

字段定义见需求 5.2，施工时以迁移 SQL 为最终事实。

### 5.2 迁移顺序

| 文件 | 内容 |
|---|---|
| `V1__users_sessions.sql` | 用户与会话 |
| `V2__templates.sql` | 模板表 |
| `V3__materials.sql` | 素材表 |
| `V4__tasks.sql` | 任务与 `task_materials` |
| `V5__task_events.sql` | 事件表 |
| `V6__simulation_config.sql` | 单例配置表 |
| `V7__seed_demo.sql` | 2 用户、3 分类 6 模板、`simulation_config` 初始行 |

规则：

- 只用 versioned 迁移，不改已提交脚本；修正用新增 `V8__...`。
- 种子模板 `requiredImageCount` 覆盖 1/2/3；`maxFileSizeBytes` 覆盖 2/5/10 MB。
- 不预置任务记录，保证"空历史"演示；如需预置，结果文件必须真实存在。
- 所有时间列 `DATETIME(3)`，应用层统一 UTC。

### 5.3 状态落库规则

- `PENDING`：`stage IS NULL`、`started_at IS NULL`。
- `PROCESSING`：`stage ∈ {MATERIAL_CHECK, GENERATING, ASSEMBLING}`。
- `SUCCEEDED`：`stage='DONE'`、`result_url NOT NULL`、`failed_stage IS NULL`。
- `FAILED`：`failed_stage` 或系统级 `error_code`，`error_message NOT NULL`。
- 每次写入 `version = version + 1`；终态更新带 `WHERE status='PROCESSING'` 条件，影响 0 行即放弃（终态不可回退）。
- `planned_fail_stage` 创建后只读。

---

## 6. 代码结构

### 6.1 后端

```
backend/src/main/java/com/xiaoying/
  config/        AppProperties, WebConfig, ExecutorConfig, SchedulingConfig
  common/        ApiError, ErrorCode, ApiException, GlobalExceptionHandler, Ids, ClockConfig
  auth/          AuthController, SessionService, AuthInterceptor, CurrentUser, CookieFactory
  template/      TemplateController, TemplateService, TemplateMapper, Template
  material/      MaterialController, MaterialService, MaterialMapper, FileHeader, LocalStorage
  task/          TaskController, TaskCreateService, TaskQueryService, TaskMapper, Task, TaskView
  pipeline/      TaskDispatcher, TaskPipelineRunner, Stage, Sleeper, ResultComposer, RestartReconciler
  simulation/    SimulationService, SimulationMapper, DevSimulationController
  storage/       StorageService, StorageProperties
```

分层约定：Controller 只做 DTO/状态码；Service 承载校验与事务；Mapper 只写 SQL；Pipeline 不依赖 Controller。

### 6.2 前端

```
frontend/
  app/
    layout.tsx, page.tsx, login/page.tsx
    templates/[id]/page.tsx
    history/page.tsx
    api/[...path]/route.ts
  components/    TopBar, CategoryFilter, TemplateCard, UploadSlot, SubmitPanel, TaskCard, ResultDialog
  features/
    templates/   useTemplates.ts, TemplateList.tsx
    upload/      useUpload.ts, uploadValidation.ts
    tasks/       useTaskPolling.ts, TaskList.tsx
  store/         index.ts, tasksSlice.ts, selectors.ts
  lib/           api/client.ts, api/types.ts, format.ts
  test/          setup.ts, fixtures.ts
```

约束：

- 轮询只在"详情页/历史页"挂载期间运行；离开页面即 abort（满足 FR-FE-05）。
- `lib/api/types.ts` 与第 4 节 DTO 一一对应，接口变更先改类型再改实现。
- 组件内不直接 `fetch`，统一走 `lib/api/client.ts`，便于注入 mock。

### 6.3 关键交互时序

**轮询**

```
mount → GET /api/tasks/active + GET /api/tasks?page=0
     → 合并到 store（按 id+version）
     → 若活跃集合非空，schedule(1500ms)
     → 每轮：Promise.allSettled(每任务 GET /api/tasks/{id})
         ├─ 成功：version 更大才合并；终态移出跟踪
         └─ 失败：该任务 sync.error；保留 status/stage/result
     → 活跃集合空 → clearTimeout，停止
unmount / logout → abort 全部 + clearTimeout + resetAll（登出时）
```

**失败注入**

```
POST /api/dev/simulation {failStage:"GENERATING", failNext:1}
POST /api/tasks
  └─ 事务内 SELECT ... FOR UPDATE → fail_remaining 1→0，快照 planned_fail_stage='GENERATING'
worker: MATERIAL_CHECK 正常 → 到 GENERATING 命中快照 → markFailed(STAGE_FAILED) → return
后续创建的任务 planned_fail_stage=NULL，正常成功

持续失败模式：{failStage:"GENERATING", failNext:0}
  └─ 每个新任务都快照 planned_fail_stage='GENERATING'，直到 failStage=null
```

---

## 7. 测试计划

### 7.1 策略与确定性

| 层 | 工具 | 覆盖 | 进 CI |
|---|---|---|---|
| 后端单测 | JUnit 5 + Mockito + AssertJ | 流水线、校验、状态机、只读、归属 | 是 |
| 后端 Web 层 | MockMvc | 状态码、错误体、Cookie 属性 | 是 |
| 迁移冒烟 | Flyway + MySQL service | 建表、种子、认领 SQL | 是 |
| 前端单测 | Vitest + Testing Library + jsdom | reducer、轮询、组件状态、上传校验 | 是 |
| 手工/演示 | 浏览器 + curl | 全链路、异常、隔离、小屏 | 否 |

确定性要求：

- 后端注入 `Sleeper`（记录调用顺序，不真睡）与 `Clock`（固定时间）；repository 用内存 fake，不连库。
- 前端用 `vi.useFakeTimers()` + 注入 `fetcher`；不真实等待、不真实网络。
- 禁止 `Math.random()` 决定成功/失败；禁随机测试数据（用固定 fixture）。

### 7.2 后端用例矩阵

| 用例 | 场景 | 阶段 | 测试类 |
|---|---|---|---|
| TC-BE-01 | 三阶段顺序 + 成功 | M3 | `TaskPipelineRunnerTest` |
| TC-BE-02/03/04 | 各阶段失败中断 | M3 | `TaskPipelineRunnerTest` |
| TC-BE-05 | 失败原因持久化 | M3 | `TaskPipelineRunnerTest` |
| TC-BE-06 | 终态幂等 | M3 | `TaskPipelineRunnerTest` |
| TC-BE-07 | 先提交后等待（fake sleeper 中读库） | M3 | `TaskPipelineRunnerTest` |
| TC-BE-08 | 查询只读 | M4 | `TaskQueryServiceTest` |
| TC-BE-09～11 | 数量/MIME/大小校验 | M3 | `TaskCreateServiceTest` |
| TC-BE-12 | 素材归属 → 404 | M3 | `TaskCreateServiceTest` |
| TC-BE-13 | 模板不存在 | M3 | `TaskCreateServiceTest` |
| TC-BE-14 | 参数非法 | M3 | `TaskCreateServiceTest` |
| TC-BE-15 | 并发认领只有一个成功 | M3 | `TaskClaimServiceTest` |
| TC-BE-16 | 未捕获异常兜底 | M3 | `TaskPipelineRunnerTest` |
| TC-BE-17 | 结果文件生成 | M3 | `ResultComposerTest` |
| TC-BE-18 | 多任务隔离 | M3 | `TaskPipelineRunnerTest` |
| TC-BE-19 | 历史排序分页 | M4 | `TaskQueryServiceTest` |
| TC-BE-20 | 用户隔离 | M4 | `TaskQueryServiceTest` |
| TC-BE-21/22 | 重启对账 / PENDING 可恢复 | M3 | `RestartReconcilerTest` |
| 补充 | 登录/登出/会话过期 | M1 | `SessionServiceTest`、`AuthControllerTest` |
| 补充 | 413/415/不可解码 | M2 | `MaterialControllerTest` |
| 补充 | 模拟配置单一来源与快照可重复 | M3/M4 | `SimulationServiceTest` |

### 7.3 前端用例矩阵

| 用例 | 场景 | 阶段 | 测试文件 |
|---|---|---|---|
| TC-FE-01 | 多任务隔离 | M7 | `store/tasksSlice.test.ts` |
| TC-FE-02 | 查询异常 ≠ 任务失败 | M7 | `features/tasks/useTaskPolling.test.tsx` |
| TC-FE-03 | 失败与同步错误区分渲染 | M7 | `components/TaskCard.test.tsx` |
| TC-FE-04/05 | 终态停跟踪 / 无活跃不轮询 | M7 | `useTaskPolling.test.tsx` |
| TC-FE-06 | 晚到响应丢弃 | M7 | `tasksSlice.test.ts` |
| TC-FE-07/13 | 用户切换 / 卸载清理 | M7 | `useTaskPolling.test.tsx` |
| TC-FE-08 | 刷新恢复 | M7 | `useTaskPolling.test.tsx` |
| TC-FE-09/10 | 提交成功切换 / 防重复 | M6 | `SubmitPanel.test.tsx` |
| TC-FE-11/12 | 上传校验 / 失败重试 | M6 | `UploadSlot.test.tsx` |
| TC-FE-14 | 结果图 404 | M7 | `TaskCard.test.tsx` |
| TC-FE-15/16 | 列表异常态 / 模板 404 | M5 | `TemplateList.test.tsx` |

### 7.4 手工用例

按需求 8.5 的 TC-MAN-01～15 执行，M9 阶段逐条跑并记录结果；其中 TC-MAN-06 默认串行、并发度为可选项。

### 7.5 不做的测试

- 不写端到端浏览器自动化（题目不要求）。
- 不追求覆盖率数字；以核心用例存在且通过为准。
- 不对真实 AI/外部服务做契约测试（本系统不依赖）。

---

## 8. CI 设计

### 8.1 工作流

文件：`.github/workflows/ci.yml`  
触发：`push`、`pull_request`；并发组按分支取消旧运行。

```yaml
jobs:
  frontend:
    steps: checkout → setup-node(22, npm cache) → npm ci
           → npm run lint → npm run typecheck → npm test -- --run
           → npm run build
  backend:
    steps: checkout → setup-java(21, maven cache) → ./mvnw -B verify
  migration-smoke:
    services: mysql:8.4 (health: mysqladmin ping)
    steps: checkout → setup-java(21, maven cache)
           → ./mvnw -B flyway:migrate -Dflyway.url=$URL -Dflyway.user=$USER -Dflyway.password=$PASS
           → 执行 seed 校验 SQL（模板数≥6、用户数=2、simulation_config 有行）
           → 认领 SQL 冒烟（插入 PENDING → 条件更新 → 再更新影响 0 行）
```

### 8.2 约束

- `frontend`/`backend` 不启动 MySQL；后端单测不连库。
- 前端 build 不请求后端：模板页为 Client Component，或在 build 阶段强制动态渲染。
- `migration-smoke` 为必过项；迁移或种子写错在此暴露。
- 允许 npm 与 Maven 分别缓存；不缓存 `node_modules`/`~/.m2` 以外的构建产物。

### 8.3 门禁

- 合并前三个 job 必须绿。
- 默认分支保护：禁止直接 push（如仓库支持）。

---

## 9. 容器与部署

### 9.1 镜像

| 服务 | 构建 | 运行 | 说明 |
|---|---|---|---|
| backend | `maven:3.9-eclipse-temurin-21` | `eclipse-temurin:21-jre` | 多阶段；非 root；暴露 8080 |
| frontend | `node:22-alpine` | `node:22-alpine` | `output:'standalone'`；暴露 3000 |
| mysql | — | `mysql:8.4` | 数据卷 `mysql-data` |

### 9.2 Compose

| 服务 | 端口 | 健康检查 | 依赖 | 卷 |
|---|---|---|---|---|
| mysql | 3306（默认不对外） | `mysqladmin ping` | — | `mysql-data:/var/lib/mysql` |
| backend | 8080 | `/actuator/health` | mysql healthy | `app-data:/data` |
| frontend | 3000 | HTTP 200 `/` | backend healthy | — |

环境变量（`.env.example`，仅演示值）：

| 变量 | 说明 |
|---|---|
| `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | 数据库初始化 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/xiaoying?useSSL=false&allowPublicKeyRetrieval=true` |
| `APP_DATA_DIR` | 容器内 `/data` |
| `APP_PIPELINE_CONCURRENCY` | 默认 1 |
| `APP_SIMULATION_DEV_API_ENABLED` | 演示环境 `true`，默认 `false` |
| `BACKEND_URL` | 前端代理目标 `http://backend:8080` |

### 9.3 启动与回滚

启动：`docker compose up --build -d` → `docker compose ps` → 访问 `http://localhost:3000`。

回滚：

1. `git checkout <上一个验收 tag>`。
2. 若迁移已前进，先备份卷：`docker run --rm -v xiaoying_mysql-data:/data -v $PWD:/backup alpine tar czf /backup/db.tgz /data`。
3. `docker compose down`（不删卷）→ `up --build -d`。
4. 数据库不兼容时 `down -v` 重建（会清空数据，仅演示环境可接受），README 注明。

---

## 10. 演示脚本（M9 验收时逐条执行）

| 步骤 | 命令/操作 | 预期 |
|---|---|---|
| 1 启动 | `docker compose down -v && docker compose up --build -d` | 三服务 healthy |
| 2 登录 | `demo / demo1234` | 进入列表页，顶栏显示用户名 |
| 3 浏览 | 切换分类、点卡片 | 列表随分类变化；进入详情 |
| 4 成功闭环 | 上传 2 图 → 提交 | 立即出现任务；阶段依次变化；结果图可预览 |
| 5 调耗时 | `curl -X POST :8080/api/dev/simulation -d '{"delayGeneratingMs":8000}'` | 新任务生成阶段约 8 s |
| 6 注入失败 | `-d '{"failStage":"GENERATING","failNext":1}'` 后提交 | 任务在 GENERATING 失败；无 ASSEMBLING；原因可读 |
| 7 多任务 | 连续提交 3 个长任务 | 依次处理（或并发 2 时 2+1）；互不串扰 |
| 8 刷新恢复 | 处理中刷新 | 历史恢复，阶段继续更新 |
| 9 关页面 | 提交后关标签，稍后重开 | 任务已完成或仍在处理 |
| 10 查询异常 | `docker compose stop backend` | 显示同步错误；任务不失败；`start` 后恢复 |
| 11 用户隔离 | demo 建任务 → 登出 → alice 登录 | 看不到 demo 任务；直接访问 URL 404 |
| 12 小屏/键盘 | 375 px + 仅键盘 | 无横向滚动；焦点可见；可上传提交 |

---

## 11. 交付映射

| 题目 | 交付阶段 | 主要证据 | 测试 |
|---|---|---|---|
| R-01 技术栈与职责 | M0/M1 | 版本表、锁文件、代理层 | CI |
| R-02 列表与模拟数据 | M2/M5 | 6 模板 3 分类、Java 接口 | TC-FE-15/16 |
| R-03 详情上传校验 | M2/M6 | 槽位状态机、双端校验、落盘 | TC-BE-09～12、TC-FE-11/12 |
| R-04 受理与异步 | M3 | 201 立即返回、后台继续 | TC-BE-15/16/22 |
| R-05 多步骤状态 | M3/M4 | 阶段提交、事件表、查询只读 | TC-BE-01～08 |
| R-06 失败与模拟 | M4 | dev 接口、快照、中断 | TC-BE-02～05、TC-MAN-04 |
| R-07 状态表达与异常 | M7 | 六类状态、sync 分离 | TC-FE-02/03 |
| R-08 多任务与生命周期 | M7 | id 索引、version、abort | TC-FE-01/04/05/06/07/13 |
| R-09 刷新恢复与历史 | M4/M7 | 历史接口、active、结果图 | TC-FE-08/14 |
| R-10 身份与个人记录 | M1 | 会话表、种子账号、404 隔离 | TC-BE-12/20、TC-MAN-10 |
| R-11 视觉与可用性 | M5～M7 | 三页布局、语义化、焦点 | TC-MAN-11/12 |
| R-12 CI 与测试 | M8 | 三 job、核心用例 | 全部 |
| R-13 交付材料 | M9 | README 16 项、投入/取舍 | 文档走查 |
| R-14 容器交付 | M9 | Compose + Dockerfile + 降级说明 | TC-MAN-01 |

> 表中每一行的"主要证据"都必须能在 `docs/status/requirements-status.md` 找到对应条目（文件:行 / 接口 / commit hash / 测试名）。

---

## 12. 排期与投入记录

按人日排，实际日期从 M0 开始顺延；若前后端并行（两人），M5～M7 可与 M2～M4 重叠，总日历工期约 9 天。

| 阶段 | 内容 | 预估 | 实际 | 备注 |
|---|---|---|---|---|
| M0 | 立项与版本验证 | 0.5 | 待填 | |
| M1 | 迁移、认证、错误模型 | 1.5 | 待填 | |
| M2 | 模板与素材 | 1.0 | 待填 | |
| M3 | 受理与流水线 | 2.0 | 待填 | 核心风险段 |
| M4 | 查询/历史/模拟 | 1.0 | 待填 | |
| M5 | 前端地基与模板页 | 1.5 | 待填 | |
| M6 | 上传与提交 | 2.0 | 待填 | |
| M7 | 跟踪/历史/恢复 | 2.0 | 待填 | |
| M8 | 测试与 CI | 1.5 | 待填 | |
| M9 | 容器/README/交付 | 1.5 | 待填 | |
| 缓冲 | 返工与联调 | 1.5 | 待填 | |
| 合计 | | **16.0** | 待填 | 需求 10.3 要求写入 README |

取舍记录同步维护在 README（与需求 10.4 模板一致），至少覆盖：轮询 vs SSE、DB 队列 vs MQ、重启对账 vs 断点续跑、会话 Cookie vs OAuth、Java2D 拼图 vs 复制原图、串行 vs 并行、事件表 vs 仅阶段字段。

每个阶段的"实际"列同时记入 `docs/status/dev-log.md`，M9 汇总进 README 的投入表。

---

## 13. 风险登记册

| 风险 | 触发信号 | 影响 | 缓解 | 兜底 |
|---|---|---|---|---|
| 查询误推进状态 | 轮询后 version 变化 | 高（直接判未通过） | 查询层只读 + TC-BE-08 | 拆分读写 Service，代码评审 |
| 阶段不可观察 | 轮询总看到同一阶段 | 高 | 先提交后 sleep + TC-BE-07 | 缩短轮询到 500 ms 调试 |
| 查询异常被当失败 | 停后端后卡片变失败 | 高 | 独立 sync 状态 + TC-FE-02/03 | 代码评审强制检查 reducer |
| 多任务串扰 | A 的结果出现在 B | 高 | 按 id 归一化 + version | 写回归用例 |
| 终态回退 | 已成功任务又变处理中 | 高 | 更新带 `status` 条件 | 数据库层拒绝 |
| Compose 起不来 | 健康检查失败 | 高 | M9 前每周跑一次干净启动 | README 降级说明 |
| 迁移/种子错误 | CI 绿但 compose 失败 | 中 | `migration-smoke` job | 本地 `down -v` 重跑 |
| 前端 build 依赖后端 | CI 构建失败 | 中 | Client Component / 强制动态 | 构建期 mock |
| 上传大文件内存 | 容器 OOM | 中 | 10 MB 上限 + 413 | 代理层限制 |
| 轮询泄漏 | 离开页面仍有请求 | 中 | abort + 清理定时器 | React StrictMode 下验证 |
| 版本不兼容 | 构建报错 | 低 | M0 门禁 | 回退已记录版本 |
| 素材版权 | 无来源说明 | 低 | 自生成 + README 注明 | 替换为生成图 |

---

## 14. 文档体系

### 14.1 文件清单

| 文件 | 作用 | 更新时机 | 最终去向 |
|---|---|---|---|
| `docs/status/requirements-status.md` | 需求完成矩阵：R-01～R-14 与 AC 的状态、证据、测试、未验证原因 | 每个 gate 结束 | README 完成度章节 |
| `docs/status/dev-log.md` | 开发记录：每个里程碑的目标、完成项、验证、偏离、问题与修复 | 每阶段结束；关键修复后追加 | 保留，作为过程证据 |
| `docs/status/test-report.md` | 各 gate 执行的自动化与手工用例结果 | G1～G9 每次 | README 测试章节 |
| `docs/status/decisions.md` | 决策记录：背景、备选项、结论、影响 | 做出决策当次 | README 取舍表 |

四份文档在 M0 建立空模板；之后 `requirements-status` 按行更新，`dev-log` 与 `test-report` 只追加，`decisions` 追加后不修改已定稿条目。M9 汇总进 README，但不删除过程文档。

### 14.2 阶段完成记录模板（追加到 `dev-log.md`）

```markdown
**M3 任务受理与异步流水线（<日期>，2.0 人日）**

目标：受理立即返回；三阶段顺序执行；失败中断；终态不可回退。

完成：
- POST /api/tasks、调度器、PipelineRunner、事件写入、重启对账

验证：
- ./mvnw -Dtest=TaskPipelineRunnerTest test → 12 passed
- curl /api/tasks/{id} 连续 20 次，version 与 updated_at 不变

偏离与决策：
- 采用 DB 队列 + 串行 worker，未用 @Async；见 decisions#D3

问题与修复：
- 阶段更新未提交导致前端看不到阶段 → commit `<hash>`（fix/pipeline-stage-commit）

遗留 / 风险：
- 多实例不支持；README 已说明
```

### 14.3 需求完成状态表模板（`requirements-status.md`）

| 需求 | 状态 | 证据 | 测试 | 备注 |
|---|---|---|---|---|
| R-04 | 完成 | `TaskCreateController.java:42`、commit `<hash>` | TC-BE-15 | 串行实现 |
| R-06 | 部分 | `/api/dev/simulation` | TC-BE-02～05 | 未做按用户注入（P2） |

规则：

- 只写事实；未做的写"未开始"，做不了的写"未验证"并给原因（如无截图、无运行环境）。
- "证据"必须可点开：文件:行、接口路径、commit hash、测试名。
- 每个 gate 结束当天更新对应行，不允许最后一次性补齐。

### 14.4 文档与代码的关系

- 文档变更与对应代码放在同一 PR；`docs(status)` 提交单独一条，信息里写里程碑编号。
- README 是对外交付面，`docs/status/*` 是过程面；两者冲突时以 README 为准，并回写 status。
- 阶段完成记录的"验证"一节必须能直接复制执行，禁止写"已测试通过"这类无证据描述。

---

## 15. Git 工作流、提交与修复

### 15.1 分支与合并

- `main` 始终可构建、CI 绿；仓库支持时开启分支保护，禁止直接 push。
- 每个里程碑一个分支：`feat/mN-<slug>`（如 `feat/m3-pipeline`）；修复用 `fix/<slug>`。
- 通过 PR 合并，PR 描述固定四段：目标 / 变更 / 验证命令 / 关联需求与风险。
- 推荐 rebase merge 保留提交颗粒度；若选择 squash，PR 描述必须保留逐条提交信息，否则过程证据丢失。

### 15.2 提交信息规范

格式：`<type>(<scope>): <摘要> [R-xx / TC-xx]`

| type | 用途 |
|---|---|
| feat | 新功能 |
| fix | 缺陷修复 |
| test | 仅增改测试 |
| docs | 文档（含 `docs(status)`） |
| ci | 流水线 |
| build | 构建、Docker、依赖 |
| refactor | 不改变行为的重构 |
| chore | 初始化、杂项 |

示例：

- `feat(pipeline): 三阶段执行与失败中断 [R-05]`
- `fix(web): 按 version 丢弃晚到响应 [TC-FE-06]`
- `docs(status): M3 完成记录`

### 15.3 提交粒度

- 一个提交做一件事；功能与其测试同提交；重构与行为变更分开。
- 每个提交必须可编译；不提交半成品功能（修复提交例外，见 15.5）。
- 大块变更拆开：V1～V7 迁移一条、种子一条、认证一条、错误模型一条，便于单独回滚。

### 15.4 各阶段提交与标签总表

| 阶段 | 分支 | 提交数（约） | 关键提交 | 门标签 |
|---|---|---|---|---|
| M0 | `chore/bootstrap` | 2 | 骨架与版本锁定；status 模板 | `gate-m0` |
| M1 | `feat/m1-foundation` | 5 | 迁移、认证、错误模型、测试、记录 | `gate-m1` |
| M2 | `feat/m2-catalog` | 4 | 模板接口、素材上传、测试、记录 | `gate-m2` |
| M3 | `feat/m3-pipeline` | 6 | 创建、认领、流水线、对账、测试、记录 | `gate-m3` |
| M4 | `feat/m4-query` | 4 | 查询、模拟、测试、记录 | `gate-m4` |
| M5 | `feat/m5-web-base` | 5 | 代理、模板页、store、测试、记录 | `gate-m5` |
| M6 | `feat/m6-upload` | 4 | 槽位、提交、测试、记录 | `gate-m6` |
| M7 | `feat/m7-tracking` | 5 | 轮询、卡片历史、恢复、测试、记录 | `gate-m7` |
| M8 | `ci/m8-gates` | 4 | 补测、CI、迁移冒烟、记录 | `gate-m8` |
| M9 | `build/m9-delivery` | 3 | 镜像与编排、README、交付快照 | `v1.0-submission` |

具体提交信息见各里程碑的"文档与提交"。

### 15.5 修复流程（红 → 绿 → 记录 → 复跑）

1. **复现**：用 curl、浏览器或单测复现，把命令与现象写进 `dev-log.md` 的"问题与修复"。
2. **先写失败测试**：新增或修改用例，确认在修复前失败（红）。红态只在本地或 PR 内存在，不合并到 `main`。
3. **最小修复**：只改导致问题的代码，不顺手重构。
4. **提交**：修复与其回归测试放在同一 PR；可拆两条（`test: 复现…` + `fix: …`），合并时整体绿。
5. **复跑门禁**：若修复发生在某一门之后，复跑该门并打 `gate-mN.1`；已过门的证据不删除，只追加。
6. **记录**：更新 `test-report.md` 的用例状态、`requirements-status.md` 的相关行；同类风险写入风险登记册。

### 15.6 常见修复预案

| 阶段 | 触发 | 现象 | 修复提交示例 | 复跑 |
|---|---|---|---|---|
| M1 | 拦截器放行清单漏配 | 模板接口返回 401 | `fix(auth): 修正放行路径匹配 [TC-BE-20]` | G1 |
| M2 | 超限返回 HTML | 413 空白页 | `fix(material): 超限返回 JSON 413 [TC-BE-11]` | G2 |
| M3 | 阶段不可观察 | 轮询总停在同一阶段 | `fix(pipeline): 阶段更新改为独立事务提交 [TC-BE-07]` | G3 |
| M3 | 任务卡在处理中 | 线程池拒绝后无主 | `fix(pipeline): 认领后拒绝回退 PENDING [FR-PIPE-09]` | G3 |
| M3 | 终态回退 | 已成功又变处理中 | `fix(pipeline): 终态更新加 status 条件 [FR-PIPE-07]` | G3 |
| M4 | 查询推进状态 | 查询后 `version` 自增 | `fix(query): 移除查询路径上的状态写入 [TC-BE-08]` | G4 |
| M5 | 分类快速切换 | 旧响应覆盖新分类 | `fix(web): 用 AbortController 丢弃旧列表响应 [TC-FE-15]` | G5 |
| M6 | 替换图片 | 旧预览泄漏、内存增长 | `fix(web): 替换槽位时 revoke 旧 objectURL [TC-FE-12]` | G6 |
| M7 | 晚到响应覆盖 | 状态回退到旧值 | `fix(web): 按 id+version 丢弃旧响应 [TC-FE-06]` | G7 |
| M7 | 卸载后仍有请求 | 控制台告警、请求泄漏 | `fix(web): 卸载时 abort 轮询并清理定时器 [TC-FE-13]` | G7 |
| M7 | 查询异常被当失败 | 停后端后卡片变失败 | `fix(web): 查询异常只置 sync.error [TC-FE-02]` | G7 |
| M7 | 多用户串数据 | 切换用户后看到旧记录 | `fix(web): 登出重置 store 并 abort 在途请求 [TC-FE-07]` | G7 |
| M8 | 迁移/种子失败 | `migration-smoke` 红 | `fix(ci): 等待 MySQL 就绪并修正种子 [NFR-CI-06]` | G8 |
| M9 | 健康检查误判 | 容器起但前端 502 | `fix(compose): 健康检查改用 wget 探活并加超时` | G9 |

### 15.7 标签与验收 commit

- `gate-m0`～`gate-m9` 为附注标签，打在该门通过的 commit 上；M9 另打 `v1.0-submission`。
- README 记录验收 tag 与 `git rev-parse HEAD` 的输出，保证评审能定位到确切代码。
- 门后修复用 `gate-mN.1`、`gate-mN.2`，不移动原标签。

### 15.8 历史卫生

- 首个提交就提交 `.gitignore`：`node_modules/`、`target/`、`.next/`、`.env`、`*.log`、运行期 `uploads/`、`results/`。
- 不提交真实凭据；`.env.example` 只放演示值。若误提交，立即轮换凭据并用 `git filter-repo` 清理，README 记录处理过程。
- 不提交大文件；示例封面单张控制在 200 KB 以内。

### 15.9 回滚

- 代码回滚用 `git revert <commit>` 保留历史；不要对已推送的 `main` 执行 `reset --hard`。
- 交付回退：检出上一个 `gate-mN` 标签重新构建；数据库迁移已前进时按 9.3 先备份卷，再决定是否重建。

---

## 16. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-28 | 初版：基于 requirements.md v1.1 的里程碑、接口、数据、测试、CI、容器与交付计划 |
| v1.1 | 2026-10-28 | 每个里程碑补充"文档与提交"（完成记录、开发日志、需求状态、测试报告、决策记录）；新增文档体系（第 14 节）与 Git 工作流/提交规范/修复流程（第 15 节）；补充阶段门标签与验收 commit 约定 |