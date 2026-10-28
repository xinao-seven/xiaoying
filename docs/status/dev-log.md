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
- 宿主机 3306 被其他项目占用，compose 端口改为 `${MYSQL_PORT:-3306}:3306`，本地 `.env` 使用 13306；容器内仍是 `mysql:3306`。
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