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