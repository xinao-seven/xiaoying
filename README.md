# 小影素材站

浏览模板、上传素材、提交制作任务，任务在 Java 后台按「素材检查 → 模拟生成 → 结果整理」三阶段异步执行，前端可继续浏览并跟踪任务状态与结果。

> 当前进度：M0～M6（版本验证、迁移与认证、模板与素材、任务受理与异步流水线、查询/历史/结果/模拟控制、前端模板页与上传提交流程）。任务跟踪、历史与刷新恢复在 M7 接入；M8 测试与 CI、M9 容器交付待完成。

## 技术版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | 22 LTS（本地验证 24.18.0；M8 CI 与 M9 镜像将固定 22） | 前端运行时 |
| Next.js | 15.5.28 | App Router |
| React / React DOM | 19.2.9 | |
| TypeScript | 5.9.3 | strict |
| Redux Toolkit / react-redux | 2.13.0 / 9.3.1 | tasks slice 按 id 归一化 |
| Vitest | 3.2.7 | 前端单测 |
| ESLint | 9.39.4 | flat config |
| Java | 21 | |
| Spring Boot / Spring Framework | 3.5.16 / 6.2.19 | |
| MyBatis (starter) | 3.0.5 | |
| MySQL | 8.4 LTS | utf8mb4；驱动 9.7.0 |
| Flyway | 11.20.3 | 含 flyway-mysql |
| JUnit | 5.x（Boot 管理） | 后端单测 |
| Spring Security Crypto | 6.5.11 | 仅 BCrypt 哈希 |

实际解析版本以 `frontend/package-lock.json`、`backend/pom.xml` 为准。

## 目录结构

```
.
├── backend/                 # Spring Boot 3.5 + MyBatis
│   ├── src/main/java/com/xiaoying/
│   │   ├── auth/            # 会话、Cookie、拦截器
│   │   ├── common/          # 错误码、统一异常、ID、traceId
│   │   ├── config/          # 应用配置、MVC 配置
│   │   ├── material/        # 素材上传与受控读取
│   │   ├── pipeline/        # 调度器、三阶段 worker、结果拼接、重启对账
│   │   ├── simulation/      # 模拟配置与开发接口
│   │   ├── storage/         # 本地卷存储
│   │   ├── task/            # 任务创建、查询、历史与结果
│   │   └── template/        # 模板分类/列表/详情
│   └── src/main/resources/db/migration/   # Flyway V1～V7
├── frontend/                # Next.js 15 App Router
│   ├── app/                 # 页面、布局、全局样式与 /api 同源代理
│   ├── components/          # Providers、顶栏
│   ├── features/            # 登录、模板、上传槽位、提交、任务卡片
│   ├── store/               # Redux Toolkit tasks slice
│   ├── lib/                 # API client、类型、上传、格式化
│   ├── public/demo/         # 自生成示例封面
│   ├── test/                # Vitest setup、fixtures
│   ├── middleware.ts        # 无会话 Cookie 时跳登录
│   └── scripts/             # 封面生成脚本
├── docs/                    # 需求、执行计划、完成记录
├── pic/                     # 本地参考 UI 截图（不参与构建，不纳入交付）
├── docker-compose.yml       # 基础编排（MySQL；M9 补齐三服务）
├── docker-compose.dev.yml   # 开发编排：后端跑在 Maven 镜像里，无需本机 JDK
└── .env.example
```

## 快速开始

### 后端（推荐：全部跑在 Docker 里，无需本机 JDK）

```bash
# MySQL（宿主机端口 13306，避免与本机其他 MySQL 冲突）+ 后端 8080
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

curl -s http://localhost:8080/actuator/health   # {"status":"UP"}
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f backend
```

- 后端源码以绑定挂载方式放进 `maven:3.9-eclipse-temurin-21`，启动时自动编译并执行 Flyway。
- 首次启动需要下载 Maven 依赖，约 1～3 分钟；依赖缓存在 `maven-cache` 卷，后续启动很快。
- 修改 Java 代码后重启后端（会重新编译）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --force-recreate backend
```

- 在容器内跑测试（等价于 `./mvnw verify`）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm --no-deps backend \
  mvn -B verify
```

- 停止（保留数据库与素材卷）：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml down
```

- 连宿主机的 MySQL 客户端调试：`mysql -h 127.0.0.1 -P 13306 -uxiaoying -pxiaoying xiaoying`。

### 后端（本机已安装 JDK 21 时）

```bash
cd backend
./mvnw -B test             # 本机跑单测，不需要 MySQL/Docker
./mvnw -q -DskipTests package
./mvnw spring-boot:run     # http://localhost:8080
./mvnw flyway:info         # 默认连接 localhost:13306
```

数据库连接默认值为 `jdbc:mysql://localhost:13306/xiaoying`（应用与 flyway 插件一致，与 compose 的宿主机端口对应）；dev compose 通过 `SPRING_DATASOURCE_URL` 覆盖为 `mysql:3306`。需要时可覆盖：

```bash
./mvnw flyway:info -Ddb.url='jdbc:mysql://localhost:3306/xiaoying?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' -Ddb.user=xiaoying -Ddb.password=xiaoying
```

> 宿主机 MySQL 端口由 `.env` 的 `MYSQL_PORT` 控制，默认 13306；容器内服务始终使用 `mysql:3306`。

### 仅数据库

```bash
docker compose up -d mysql
```

### 前端

```bash
cd frontend
npm ci
npm run dev                # http://localhost:3000
npm run lint && npm run typecheck && npm test && npm run build
```

- 浏览器只访问 `http://localhost:3000`；`app/api/[...path]/route.ts` 把 `/api/*` 代理到 `BACKEND_URL`（默认 `http://127.0.0.1:8080`），并透传 Cookie。
- 需要指向其他后端时：`BACKEND_URL=http://其它地址:8080 npm run dev`。
- 模板与详情页在客户端取数，`npm run build` 不依赖后端。

### 演示账号

| 用户名 | 密码 | 用途 |
|---|---|---|
| `demo` | `demo1234` | 主演示账号 |
| `alice` | `alice1234` | 验证用户隔离 |

仅用于本地演示，禁止用于生产。

## 数据库初始化

- 迁移脚本：`backend/src/main/resources/db/migration/V1__users_sessions.sql` ～ `V7__seed_demo.sql`。
- 应用启动时自动执行 Flyway；也可手动 `./mvnw flyway:migrate`。
- 种子包含 2 用户、4 分类 6 模板、`simulation_config` 单例行；不预置任务。
- 重置：

```bash
docker compose down -v && docker compose up -d mysql
```

## 主要接口（M0～M6 已实现）

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 否 | 登录，`{username,password}` → `{user}` + Cookie |
| POST | `/api/auth/logout` | 是 | 登出，204 + 清 Cookie |
| GET | `/api/auth/me` | 是 | 当前用户 |
| GET | `/api/templates/categories` | 否 | 分类及数量 |
| GET | `/api/templates?category=` | 否 | 启用中的模板 |
| GET | `/api/templates/{id}` | 否 | 模板详情 |
| POST | `/api/materials` | 是 | multipart `file` 上传 |
| GET | `/api/materials/{id}/content` | 是 | 按归属返回图片 |
| POST | `/api/tasks` | 是 | `{templateId,materialIds,params?}` → 201 任务（立即返回） |
| GET | `/api/tasks/{id}` | 是 | 任务详情（仅本人） |
| GET | `/api/tasks?page=&size=` | 是 | 历史，创建时间倒序，`items/page/size/total` |
| GET | `/api/tasks/active` | 是 | 当前用户非终态任务 |
| GET | `/api/tasks/{id}/result` | 是 | 成功结果 PNG，`private, max-age=60` |
| GET | `/api/tasks/{id}/events` | 是 | 阶段事件时间线，按 id 升序 |
| GET | `/api/dev/simulation` | 开发开关 | 当前模拟配置 |
| POST | `/api/dev/simulation` | 开发开关 | 调整耗时、注入失败、清空注入 |
| GET | `/actuator/health` | 否 | 健康检查 |

错误体统一为 `{code, message, fieldErrors?, traceId}`。

## 任务与异步流水线

提交任务只做校验与落库，立即返回 `201` 和完整任务对象；后台按「素材检查 → 模拟生成 → 结果整理」执行。默认串行（`APP_PIPELINE_CONCURRENCY=1`），其余任务保持 `PENDING`；可调高并发演示多任务并行。

```bash
# 创建任务
curl -s -b cookies.txt -H 'Content-Type: application/json' \
  -d '{"templateId":"demo-postcard","materialIds":["mat_xxx","mat_yyy"],"params":{"caption":"summer"}}' \
  http://127.0.0.1:8080/api/tasks

# 轮询
curl -s -b cookies.txt http://127.0.0.1:8080/api/tasks/tsk_xxx

# 阶段事件、历史、活跃、结果
curl -s -b cookies.txt http://127.0.0.1:8080/api/tasks/tsk_xxx/events
curl -s -b cookies.txt 'http://127.0.0.1:8080/api/tasks?page=0&size=20'
curl -s -b cookies.txt http://127.0.0.1:8080/api/tasks/active
curl -s -b cookies.txt -o result.png http://127.0.0.1:8080/api/tasks/tsk_xxx/result
```

### 模拟耗时与失败

开发接口默认关闭；dev compose 通过 `APP_SIMULATION_DEV_API_ENABLED=true` 开启。关闭时接口返回 404。

```bash
# 生成阶段改为 8 秒（对新任务生效）
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' -d '{"delayGeneratingMs":8000}'

# 下一个任务在生成阶段失败一次
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' -d '{"failStage":"GENERATING","failNext":1}'

# 持续失败：直到显式清空
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' -d '{"failStage":"GENERATING","failNext":0}'

# 清空注入（同时清零剩余次数）
curl -s -X POST http://127.0.0.1:8080/api/dev/simulation \
  -H 'Content-Type: application/json' -d '{"failStage":null}'
```

失败计划在创建任务时快照到 `tasks.planned_fail_stage`；之后修改配置不会影响已创建任务。计数模式消费到 0 会自动清空，持续失败模式需显式 `failStage=null`。

### 重启行为

- `PENDING`：重启后由调度器重新拾起。
- `PROCESSING`：启动对账标记为 `FAILED`，`errorCode=SERVER_RESTART`，不实现断点续跑。
- 单实例假设；多实例会重复消费，README 后续补充。

> 本机若 `localhost` 解析到 `::1` 且被其他进程占用，请把命令里的 `localhost` 换成 `127.0.0.1`。

## 素材与示例资源

- 素材落盘：`{APP_DATA_DIR}/uploads/{userId}/{uuid}.{ext}`，数据库只存相对 key。
- 结果目录（M3 起）：`{APP_DATA_DIR}/results/`。
- 示例封面由 `frontend/scripts/generate-covers.mjs` 自绘生成（纯 Node + zlib，无第三方素材），可重跑：`npm run covers`。

## 测试

后端单测不连库，使用 fake repository、fake sleeper、fake clock：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm --no-deps backend mvn -B test
```

当前 103 个测试，覆盖任务创建校验、原子认领、阶段顺序、失败中断、先提交后等待、终态幂等、重启对账、查询只读、历史分页、用户隔离、失败计划快照、Java2D 结果文件。

前端单测不依赖真实网络与定时器：

```bash
cd frontend
npm run lint && npm run typecheck && npm test && npm run build
```

当前 26 个测试，覆盖 tasks slice 的 version 合并与查询错误隔离、模板列表异常/空态/分类竞态、模板 404、上传类型与大小校验、单槽重试、预览 URL 回收、提交防重复与受理后立即插入任务。

## 待补充（M7 起）

- 任务跟踪、历史、刷新恢复与结果预览（M7）
- 测试补齐与 CI（M8）
- 容器化、README 十六项与演示脚本（M9）
- 实际投入、取舍、已知限制、AI 使用说明、第三方来源（M9）

## 已知限制（截至 M6）

- 单实例：多实例会重复消费任务；不实现分布式锁。
- 重启恢复：处理中的任务标记为失败，不实现断点续跑。
- 并发：默认串行；`APP_PIPELINE_CONCURRENCY` 可调，用于演示并行。
- 幂等：仅前端防重复提交，无服务端 `Idempotency-Key`。
- 结果生成：Java2D 模拟拼图，不调用真实 AI；中文标题依赖运行环境字体，缺失时回退英文标题。
- 开发模拟接口不鉴权，仅限本地/演示环境。
- 前端任务列表当前只展示本次会话提交的任务；自动轮询、历史恢复与结果预览在 M7 实现。

需求追踪与过程证据见 `docs/status/`。

## 门标签

| 标签 | 里程碑 | commit |
|---|---|---|
| `gate-m0` | 立项与版本验证 | `ab1e371` |
| `gate-m1` | 迁移、认证、错误模型 | `9f9a6b9` |
| `gate-m2` | 模板与素材 | `2a289cc` |
| `gate-m2.1` | 上传时间毫秒精度修复复跑 | `aa43e72` |
| `gate-m3` | 任务受理与异步流水线 | 代码 `b8a8516`；完成记录见本次 docs 提交 |
| `gate-m4` | 查询、历史、结果与模拟控制 | 代码 `b8a8516`；完成记录见本次 docs 提交 |
| `gate-m5` | 前端地基与模板页面 | 代码 `05a7d71`、测试 `b8dcfe8`；完成记录见本次 docs 提交 |
| `gate-m6` | 制作页上传槽位与提交 | 代码 `05a7d71`、测试 `b8dcfe8`；完成记录见本次 docs 提交 |

后端构建、测试与迁移验证使用 `maven:3.9-eclipse-temurin-21` 容器（本机无 JDK），
前端在 Node 24.18 下验证，CI 与容器镜像将固定 Node 22。
