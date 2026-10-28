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
