# 小影素材站

浏览模板、上传素材、提交制作任务，任务在 Java 后台按「素材检查 → 模拟生成 → 结果整理」三阶段异步执行，前端可继续浏览并跟踪任务状态与结果。

> 当前进度：M0～M2（版本验证、迁移与认证、模板与素材接口）。前端页面在 M5 接入。

## 技术版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | 22 LTS（本地验证 24.18.0，CI/容器固定 22） | 前端运行时 |
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
│   │   ├── storage/         # 本地卷存储
│   │   └── template/        # 模板分类/列表/详情
│   └── src/main/resources/db/migration/   # Flyway V1～V7
├── frontend/                # Next.js 15 App Router
│   ├── app/                 # 页面与全局样式
│   ├── lib/                 # 工具函数
│   ├── public/demo/         # 自生成示例封面
│   └── scripts/             # 封面生成脚本
├── docs/                    # 需求、执行计划、完成记录
├── pic/                     # 前端参考 UI（仅参考布局）
├── docker-compose.yml       # 目前仅 MySQL，M9 补齐三服务
└── .env.example
```

## 快速开始

### 仅数据库（当前阶段）

```bash
docker compose up -d mysql
```

### 后端

```bash
cd backend
./mvnw -q -DskipTests package
./mvnw spring-boot:run     # http://localhost:8080
./mvnw flyway:info         # 需要 MySQL 已启动
```

覆盖数据库连接：

```bash
./mvnw flyway:info -Ddb.url='jdbc:mysql://localhost:3306/xiaoying?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' -Ddb.user=xiaoying -Ddb.password=xiaoying
```

> 宿主机 3306 已被占用时，在仓库根目录的 `.env`（不入库）写入 `MYSQL_PORT=13306`，
> 再使用 `jdbc:mysql://localhost:13306/xiaoying?...`。容器内服务始终使用 `mysql:3306`。

没有本地 JDK 时，可用官方 Maven 镜像执行与 `./mvnw` 等价的命令：

```bash
docker run --rm -v "$PWD/backend:/ws" -w /ws -v xiaoying-m2:/root/.m2 \
  maven:3.9-eclipse-temurin-21 mvn -B verify
```

本次 M0～M2 的构建、测试与 Flyway 校验均在 `maven:3.9-eclipse-temurin-21` 容器中完成。

### 前端

```bash
cd frontend
npm ci
npm run dev                # http://localhost:3000
npm run lint && npm run typecheck && npm test && npm run build
```

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

## 主要接口（M0～M2 已实现）

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
| GET | `/actuator/health` | 否 | 健康检查 |

错误体统一为 `{code, message, fieldErrors?, traceId}`。

## 素材与示例资源

- 素材落盘：`{APP_DATA_DIR}/uploads/{userId}/{uuid}.{ext}`，数据库只存相对 key。
- 结果目录（M3 起）：`{APP_DATA_DIR}/results/`。
- 示例封面由 `frontend/scripts/generate-covers.mjs` 自绘生成（纯 Node + zlib，无第三方素材），可重跑：`npm run covers`。

## 待补充（M3 起）

- 任务受理与异步流水线（M3）
- 查询、历史、结果与模拟控制（M4）
- 前端登录、模板页、制作页、跟踪与历史（M5～M7）
- 测试补齐与 CI（M8）
- 容器化、README 十六项与演示脚本（M9）
- 实际投入、取舍、已知限制、AI 使用说明、第三方来源（M9）

需求追踪与过程证据见 `docs/status/`。