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