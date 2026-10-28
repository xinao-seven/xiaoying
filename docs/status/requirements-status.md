# 需求完成状态

> 依据：`docs/requirements.md` v1.1。规则：只写事实；证据写成可点开的文件:行、接口路径、commit 或测试名；未完成写「未开始」，无法运行验证写「未验证」并给原因。

| 需求 | 状态 | 证据 | 测试 | 备注 |
|---|---|---|---|---|
| R-01 技术栈与职责 | 进行中 | `backend/pom.xml`（Boot 3.5.16 / MyBatis 3.0.5 / MySQL 8.4）、`frontend/package.json`（Next 15.5.28 / React 19.2.9 / TS 5.9.3）；模板与素材接口由 Java 提供 | `npm run build`、`mvn -B test` | 技术栈与职责已落地；任务接口 M3/M4 |
| R-02 模板列表与模拟数据 | 进行中 | `GET /api/templates`、`/categories`、`/{id}`；`V7__seed_demo.sql`（4 分类 6 模板） | `TemplateControllerTest`、`TemplateServiceTest` | 后端完成；前端列表 M5 |
| R-03 详情、上传与校验 | 进行中 | `POST /api/materials`、`GET /api/materials/{id}/content`；落盘 `uploads/{userId}/{uuid}.{ext}` | `MaterialUploadServiceTest`、`MaterialControllerTest` | 后端完成；槽位状态机 M6 |
| R-04 受理与异步执行 | 未开始 | — | — | M3 |
| R-05 多步骤状态与查询 | 未开始 | — | — | M3/M4 |
| R-06 失败与模拟控制 | 未开始 | — | — | M4 |
| R-07 状态表达与请求异常 | 未开始 | — | — | M7 |
| R-08 多任务与查询生命周期 | 未开始 | — | — | M7 |
| R-09 刷新恢复与结果记录 | 未开始 | — | — | M4/M7 |
| R-10 身份与个人记录 | 进行中 | `/api/auth/login`、`/logout`、`/me`；`V1__users_sessions.sql`；`V7__seed_demo.sql`；素材越权 404 | `SessionServiceTest`、`AuthControllerTest`、`MaterialUploadServiceTest` | 认证与素材归属完成；任务查询隔离 M3/M4 |
| R-11 视觉与可用性 | 未开始 | `frontend/app` 深色主题骨架 | — | M5～M7 |
| R-12 CI 与测试 | 未开始 | — | — | M8 |
| R-13 提交材料与说明 | 进行中 | `README.md`（版本表、结构、命令、接口、账号、素材来源） | — | 骨架已建；M9 补齐 16 项 |
| R-14 容器交付 | 未开始 | `docker-compose.yml`（当前仅 MySQL） | — | M9 |

## 阶段门

| 门 | 状态 | 证据 |
|---|---|---|
| G0 | 通过 | commit `ab1e371`；`npm ci && npm run build` 成功；`mvn -B -DskipTests package` 成功；`flyway:info` 连上 MySQL 8.4 并列出 7 个待执行迁移 |
| G1 | 通过 | commit `9f9a6b9`；`./mvnw -B verify` 33 个测试全绿；curl 登录/登出/me 与 Cookie 属性符合；清空卷后重启应用自动完成 V1～V7 |
| G2 | 通过 | commit `2a289cc`；`GET /api/templates` 返回 6 条；分类筛选 2 条；上传 PNG 201 且落盘；用户 B 取 A 素材 404；伪造 PNG 415；11 MB 上传 413 JSON |
| G3 | 未开始 | — |