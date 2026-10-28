# 需求完成状态

> 依据：`docs/requirements.md` v1.1。规则：只写事实；证据写成可点开的文件:行、接口路径、commit 或测试名；未完成写「未开始」，无法运行验证写「未验证」并给原因。

| 需求 | 状态 | 证据 | 测试 | 备注 |
|---|---|---|---|---|
| R-01 技术栈与职责 | 完成 | `backend/pom.xml`（Boot 3.5.16 / MyBatis 3.0.5 / MySQL 8.4）、`frontend/package.json`（Next 15.5.28 / React 19.2.9 / TS 5.9.3）；模板、素材、任务、模拟接口均由 Java 提供；浏览器只访问 `:3000`，`frontend/app/api/[...path]/route.ts` 同源代理到 Java | `npm run lint/typecheck/test/build`、`mvn -B test` | 前端 M5/M6 页面已接通 Java 接口 |
| R-02 模板列表与模拟数据 | 完成 | `GET /api/templates`、`/categories`、`/{id}`；`V7__seed_demo.sql`（4 分类 6 模板）；前端 `TemplateList` 分类筛选、卡片、骨架/空态/错误重试，`TemplateDetail` 详情 | `TemplateControllerTest`、`TemplateServiceTest`、TC-FE-15/16 | 6 模板覆盖 1/2/3 张素材与 2/5/10 MB |
| R-03 详情、上传与校验 | 完成 | `POST /api/materials`、`GET /api/materials/{id}/content`；`features/upload/useUploadSlots`（槽位状态机、预览回收、单槽重试）、`uploadValidation`、`SubmitPanel` | `MaterialUploadServiceTest`、`MaterialControllerTest`、TC-FE-11/12 | 前后端均校验类型/大小；数量由槽位与“全部 UPLOADED”约束 |
| R-04 受理与异步执行 | 完成 | `POST /api/tasks`、`TaskCreateService`、`TaskDispatcher`、`TaskPipelineRunner`；8 s 生成配置下创建接口 13 ms 返回 201 | `TaskCreateServiceTest`、`TaskClaimServiceTest`、`TaskPipelineRunnerTest`、`RestartReconcilerTest` | 串行默认；信号量把在飞任务限制在并发度内，其余保持 PENDING |
| R-05 多步骤状态与查询 | 完成 | 阶段先提交后等待；`task_events` 记录 STARTED/SUCCEEDED/FAILED；查询只读 | `TaskPipelineRunnerTest`（TC-BE-01～08/18）、`TaskQueryServiceTest` | 事件接口 `/api/tasks/{id}/events`；结果图 1200×800 |
| R-06 失败与模拟控制 | 完成 | `GET/POST /api/dev/simulation`；创建时快照 `tasks.planned_fail_stage`；计数/持续失败两种模式 | `SimulationServiceTest`、`DevSimulationControllerTest`、TC-BE-02～05 | 默认关闭，dev compose 开启；关闭时 404 |
| R-07 状态表达与请求异常 | 完成 | `features/tasks/TaskCard`：等待/处理中/成功/失败/查询异常五类渲染；`store.tasks.sync` 与 `byId` 分离，查询失败只写 `sync`；`ResultDialog` 预览 | `TaskCard.test.tsx`（TC-FE-02/03/14）、`tasksSlice.test.ts` | 结果图 404 只显示占位，不改任务状态 |
| R-08 多任务与查询生命周期 | 完成 | `features/tasks/useTaskPolling`：按 id 并发查询、链式 `setTimeout`、终态停轮询、空集合不发请求、`id+version` 合并、卸载/切用户 abort + clearTimeout | `useTaskPolling.test.tsx`（TC-FE-01/04/05/06/07/13）、`tasksSlice.test.ts`（TC-FE-01/06） | 间隔 2 秒；查询异常后继续轮询，恢复即自动继续 |
| R-09 刷新恢复与结果记录 | 完成 | `useTaskHistory` + `TaskSync` 首屏拉历史与活跃任务；`/history` 页与详情页右栏共用 `TaskCard`；结果图预览与 404 占位 | `useTaskPolling.test.tsx`（TC-FE-08）、`TaskCard.test.tsx`（TC-FE-14）、G7 浏览器检查 | 历史首屏最近 50 条，暂不分页 |
| R-10 身份与个人记录 | 完成 | `/api/auth/*`；`LoginForm` 成功登录与 `TopBar` 登出均 `resetAll()`；素材、任务、结果均按 `user_id` 过滤，越权统一 404；轮询 cleanup abort 在途请求 | `SessionServiceTest`、`AuthControllerTest`、`TaskQueryServiceTest`、TC-FE-07、G7 alice 隔离检查 | 切换用户不残留上一账号任务 |
| R-11 视觉与可用性 | 完成 | 深色主题；列表/详情双栏/历史页；语义化按钮与 label、`aria-pressed`、`aria-current=step`、`role=alert/status`；结果对话框 Esc/焦点返回；375 px 无横向滚动 | 浏览器 G5/G6/G7 检查、TC-FE-03、TC-MAN-11/12（M9 复核） | `/history` 375 px 实测 `scrollWidth=375` |
| R-12 CI 与测试 | 未开始 | — | — | M8 |
| R-13 提交材料与说明 | 进行中 | `README.md`（版本表、结构、命令、接口、账号、素材来源） | — | 骨架已建；M9 补齐 16 项 |
| R-14 容器交付 | 未开始 | `docker-compose.yml`（当前仅 MySQL） | — | M9 |

## 阶段门

| 门 | 状态 | 证据 |
|---|---|---|
| G0 | 通过 | commit `ab1e371`；`npm ci && npm run build` 成功；`mvn -B -DskipTests package` 成功；`flyway:info` 连上 MySQL 8.4 并列出 7 个待执行迁移 |
| G1 | 通过 | commit `9f9a6b9`；`./mvnw -B verify` 33 个测试全绿；curl 登录/登出/me 与 Cookie 属性符合；清空卷后重启应用自动完成 V1～V7 |
| G2 | 通过 | commit `2a289cc`；`GET /api/templates` 返回 6 条；分类筛选 2 条；上传 PNG 201 且落盘；用户 B 取 A 素材 404；伪造 PNG 415；11 MB 上传 413 JSON；毫秒精度修复复跑见 `gate-m2.1`（`aa43e72`） |
| G2.1 | 通过（审查修正复跑） | 39 tests；非 multipart → 415、不支持方法 → 405 均为 JSON；上传写库失败清理孤儿文件；见 `dev-log.md`「M2 后审查修正」与 `test-report.md` 对应小节。未单独打 tag，建议以 `gate-m2.2` 标记本次修正 |
| G3 | 通过 | commit `b8a8516`；103 个后端测试全绿；curl：创建 201/13 ms（8 s 生成配置）、PENDING→MATERIAL_CHECK→GENERATING→ASSEMBLING→DONE、查询 20 次 version/updated_at 不变、注入 GENERATING 失败无 ASSEMBLING、重启后残留任务 FAILED/SERVER_RESTART；证据见 `test-report.md` G3 |
| G4 | 通过 | commit `b8a8516`；3 任务 `total=3` 倒序、`active` 只含非终态、成功结果 200 PNG/失败结果 404、`Cache-Control: private, max-age=60`、8 s 耗时生效、failNext=1 一次失败后恢复、failNext=0 连续失败后清空恢复；证据见 `test-report.md` G4 |
| G5 | 通过 | 前端 lint 0/typecheck 0/26 tests/build 5 路由；浏览器登录、6 模板 4 分类、筛选与 5 次快速切换、模板 404、停后端错误态与恢复、375 px 无横向滚动、登出不需 Cookie 跳登录；证据见 `test-report.md` G5 |
| G6 | 通过 | 10 个上传/提交测试；浏览器 2 槽位、类型/大小拦截、真实 XHR 上传 2/2、替换/移除、停后端单槽失败后重试、提交后服务端总数 +1 且任务卡片立即出现；证据见 `test-report.md` G6 |
| G7 | 通过 | 46 个前端测试；浏览器 3 任务状态隔离、终态 6 秒无新轮询、处理中刷新后阶段继续、停后端保留状态+重试提示、重启自动恢复、alice 历史为空且 demo 任务 404、预览对话框焦点返回、375 px 无溢出；证据见 `test-report.md` G7 |

> 注：G0～G2 的「通过」行在首个提交 `ab1e371` 即已写入（G1/G2 早于对应代码提交），commit 证据到 `d3ca83b` 才补齐，晚于 `gate-m1`/`gate-m2` 标签。历史不改写；后续门按计划 14.3/15.4 执行「文档提交 → 打标签」。
