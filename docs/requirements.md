# 素材制作网站 需求文档

> 文档版本：v1.0（草案）  
> 编写日期：2026-10-28  
> 需求来源：全栈开发笔试题（R-01 ～ R-14）  
> 读者：开发、测试、验收人  
> 文档状态：待评审

---

## 0. 文档约定

### 0.1 优先级定义

| 优先级 | 含义 | 验收影响 |
|---|---|---|
| P0 / MUST | 核心需求，不实现即视为该模块未完成 | 必须通过 |
| P1 / SHOULD | 影响完整度、可靠性和体验 | 原则上应通过，未实现需在 README 说明 |
| P2 / MAY | 可选增强 | 不作为验收门槛 |

### 0.2 验收口径

- 以容器隔离环境中的实际运行结果为准，不以开发机上的本地进程为准；评审方也可选择只审源码，此时与运行相关的项按“未验证”记录。
- 源码可证明的实现与实际运行效果分别记录；无法运行验证的部分标记为“未验证”。
- 所有 P0 验收项通过、CI 为绿、README 覆盖 R-13 清单，视为达到交付标准。
- 题目明确不要求的扩展（消息队列、断点续跑、幂等、真实 AI、支付、会员等）不作为扣分项，但必须在 README 中说明取舍。

### 0.3 术语

| 术语 | 含义 |
|---|---|
| 模板（Template） | 一种制作预设，包含分类、封面、素材数量与格式要求 |
| 素材（Material） | 用户上传、已落盘到服务端的图片文件 |
| 任务（Task） | 一次制作请求，绑定模板与若干素材 |
| 阶段（Stage） | 任务内部的处理步骤：素材检查、模拟生成、结果整理 |
| 终态（Terminal） | `SUCCEEDED` 或 `FAILED`，不可再变更 |
| 活跃任务 | 非终态任务，即 `PENDING` 或 `PROCESSING` |
| 同步错误（Sync Error） | 前端查询任务状态失败，属于请求异常，不代表任务失败 |
| 模拟注入 | 通过开发配置让指定阶段延迟或失败，用于演示 |

### 0.4 交付与评审约定

| 事项 | 约定 |
|---|---|
| 交付渠道 | 通过收到本任务的**原沟通渠道**回复源码仓库链接或源码压缩包；如需澄清要求，同样走该渠道。 |
| 提交日期 | 按双方沟通确认的日期提交；确认后在 README 与 10.1 交付清单中记录。 |
| 参考截图 | UI-01～UI-03 为**条件项**：随题提供时参考其主布局；未提供时以 3.9 的页面结构建议为准。当前仓库未附截图，验收按后者执行。 |
| Compose | 题目中 R-14 为**推荐项**。本项目仍承诺交付 Compose；若 Compose 因环境原因不可用，允许按 README 的降级启动说明复核，不因此单独判未通过（见 9.1、9.6）。 |

---

## 1. 项目概述

### 1.1 背景

实现一个可独立运行的素材制作网站：用户浏览模板、上传图片、提交制作任务；任务在 Java 后台异步执行；用户在处理期间可继续使用网站，并在历史记录中查看任务状态和制作结果。

制作过程不调用真实 AI 服务。系统使用可控制耗时和成功/失败的模拟生成器完成业务闭环。

### 1.2 项目目标

1. 验证 Java 后端如何组织多步骤异步处理，并保证状态持久化、顺序执行、失败中断。
2. 验证前端如何管理上传、提交、等待、处理、成功、失败、查询异常等多类异步状态。
3. 验证工程交付能力：测试、CI、容器化、文档和可复现演示。

### 1.3 范围

包含：

- 模板分类、列表、详情。
- 图片选择、预览、替换、移除、上传。
- 任务创建、异步多阶段处理、状态查询、历史记录、结果预览。
- 用户身份与个人任务隔离。
- 模拟耗时与指定阶段失败。
- 前后端测试、CI、Docker Compose（题目推荐项，本项目仍交付）、README。

不包含：

- 真实 AI、支付、积分、会员、第三方账号登录。
- 模板 CRUD 管理后台。
- 任务删除、重新制作、下载、视频处理。
- 消息队列、Redis、分布式锁、集群部署。
- 服务重启后的断点续跑。
- 国际化、多区域部署、生产级运维。

### 1.4 角色

| 角色 | 能力 |
|---|---|
| 访客 | 浏览模板列表与详情；访问受保护操作时跳转登录 |
| 登录用户 | 上传素材、创建任务、查看自己的任务与历史、预览结果 |
| 演示/验收人员 | 通过开发接口调整耗时、注入阶段失败，观察系统行为 |

### 1.5 核心用户旅程

1. 用户登录。
2. 浏览模板列表，按分类筛选。
3. 进入模板详情，查看素材要求。
4. 选择 N 张图片，预览、替换或移除，等待上传完成。
5. 点击提交，接口立即返回任务 ID。
6. 页面展示任务的等待/处理状态及当前阶段，用户可继续浏览或创建其他任务。
7. 任务成功后展示结果图片；失败后展示失败阶段和原因。
8. 刷新页面或重新登录后，从服务端恢复历史记录，继续跟踪未完成任务。

---

## 2. 技术约束与总体架构

### 2.1 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 前端 | Next.js 15.x（App Router）、React 19、TypeScript 5.x | 使用 Node.js 22 LTS |
| 前端状态 | Redux Toolkit 2.x（`tasks` slice 按 id 归一化） | 纯函数 reducer/selector 便于单测；轮询由独立 hook 驱动 |
| 后端 | Java 21、Spring Boot 3.5.x、MyBatis（mybatis-spring-boot-starter 3.0.x） | REST API |
| 数据库 | MySQL 8.4 LTS | 字符集 `utf8mb4` |
| 迁移 | Flyway 11.x，包含 `flyway-mysql` | 建表与种子数据 |
| 测试 | JUnit 5、Mockito、AssertJ；Vitest、Testing Library | 单测为主 |
| CI | GitHub Actions | push / pull_request 触发 |
| 部署 | Docker Compose v2、Dockerfile | 前端 + 后端 + MySQL |

> 版本最终以仓库中的 `package.json`、`package-lock.json`、`pom.xml`、Maven Wrapper 为准。实现开始前必须先做一次空项目构建，确认所选版本相互兼容，并在 README 记录实际版本。

### 2.2 职责边界

| 编号 | 要求 |
|---|---|
| FR-TECH-01 | 前端必须使用 Next.js App Router、React、TypeScript。 |
| FR-TECH-02 | 后端必须使用 Java、Spring Boot、MyBatis，数据库必须为 MySQL。 |
| FR-TECH-03 | 模板、素材、任务、个人记录相关业务接口必须由 Java 提供；任务状态与历史必须持久化到 MySQL。 |
| FR-TECH-04 | Next.js 可以直接调用 Java，也可以提供接口中间层；业务执行和持久化不得仅由 Next.js 完成。 |
| FR-TECH-05 | 不得把模板、任务状态、历史记录只保存在前端内存、浏览器存储或 Next.js 进程内。 |
| FR-TECH-06 | 依赖版本必须锁定并提交锁文件；README 必须标明实际版本。 |

**设计决策**：浏览器统一访问 Next.js 同源路径 `/api/*`，由 Next.js 代理到 Java 后端。这样避免 CORS 与跨站 Cookie 问题；代理层不承载业务逻辑，只转发请求和响应。

### 2.3 部署形态

| 服务 | 镜像/构建 | 端口 | 依赖 |
|---|---|---|---|
| `mysql` | `mysql:8.4` | 仅容器网络（可选暴露 3306 便于调试） | 无 |
| `backend` | 多阶段 Dockerfile | 8080 | MySQL 健康后启动 |
| `frontend` | 多阶段 Dockerfile | 3000 | 后端健康后启动 |

数据卷：

- `mysql-data`：MySQL 数据目录。
- `uploads`：后端上传素材与生成结果目录。

---

## 3. 功能需求

### 3.1 身份与个人记录（FR-AUTH）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-AUTH-01 | P0 | 提供登录接口，使用用户名和密码换取服务端会话。 | 正确凭据返回当前用户并设置 Cookie；错误凭据返回 401 和统一文案。 |
| FR-AUTH-02 | P0 | 会话必须由服务端校验，Cookie 必须为 `HttpOnly`、`SameSite=Lax`、`Path=/`。 | 浏览器脚本无法读取会话令牌；刷新后仍保持登录。 |
| FR-AUTH-03 | P0 | 提供登出接口，服务端删除会话并清除 Cookie。 | 登出后访问受保护接口返回 401。 |
| FR-AUTH-04 | P0 | 提供当前用户接口。 | 未登录返回 401；已登录返回用户 id、用户名、显示名。 |
| FR-AUTH-05 | P0 | 所有素材、任务、历史、结果查询必须按当前会话用户过滤。 | 用户 B 无法读取用户 A 的任务、素材或结果；越权请求返回 404。 |
| FR-AUTH-06 | P1 | 提供至少两个种子账号，便于演示多用户隔离。 | README 列出账号与密码；登录后历史互不可见。 |
| FR-AUTH-07 | P1 | 前端在登出或切换用户时清空任务状态并取消在途请求。 | 切换后不出现上一用户的记录或加载态残留。 |
| FR-AUTH-08 | P2 | 登录失败次数限制、密码重置、注册。 | 不要求；如实现需在 README 说明。 |

**设计决策**：

- 使用种子账号 + BCrypt 密码哈希 + 服务端会话表。
- 不引入完整 Spring Security 过滤器链；使用 `spring-security-crypto` 做密码哈希，自定义拦截器解析会话。
- 会话有效期默认 7 天，过期后跳转登录。
- 演示账号示例：`demo / demo1234`、`alice / alice1234`。仅用于本地演示，README 明确标注。

---

### 3.2 模板浏览（FR-TPL）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-TPL-01 | P0 | 前端必须展示模板分类，并支持按分类筛选。 | 点击分类后列表更新；存在“全部”选项。 |
| FR-TPL-02 | P0 | 模板列表必须展示封面、标题、分类和素材数量要求。 | 卡片信息完整，点击可进入详情。 |
| FR-TPL-03 | P0 | 模板数据必须通过 Java 接口获取，不得只在前端写死。 | 断网或后端不可用时列表进入错误态，而不是继续显示写死数据。 |
| FR-TPL-04 | P0 | 模板详情必须展示标题、描述、封面、素材要求。 | 至少展示所需图片数量、允许的 MIME、单文件大小上限。 |
| FR-TPL-05 | P0 | 访问不存在的模板必须给出可理解的 404 反馈。 | 页面显示“模板不存在”并提供返回列表入口。 |
| FR-TPL-06 | P0 | 必须提供足够演示分类切换和不同素材数量要求的种子模板。 | 至少 3 个分类、6 个模板；`requiredImageCount` 覆盖 1～3。 |
| FR-TPL-07 | P0 | 封面必须是本地可访问的示例文件或合法可用资源。 | 容器启动后不依赖外网即可显示；README 说明素材来源。 |
| FR-TPL-08 | P1 | 列表和详情必须有加载中、空数据、请求失败三种状态。 | 加载骨架/占位、空分类文案、失败重试按钮。 |
| FR-TPL-09 | P1 | 分类筛选请求应可取消或丢弃旧响应，避免快速切换时旧分类覆盖新分类。 | 快速切换分类，最终展示与当前选中分类一致。 |
| FR-TPL-10 | P2 | 模板排序、搜索、收藏。 | 不要求。 |

**模板响应示例**：

```json
{
  "id": "demo-postcard",
  "title": "旅行明信片",
  "category": "创意图片",
  "coverUrl": "/demo/postcard-cover.png",
  "description": "上传两张图片，模拟生成一张旅行明信片",
  "requiredImageCount": 2,
  "acceptedMimeTypes": ["image/jpeg", "image/png"],
  "maxFileSizeBytes": 5242880
}
```

**模板字段要求**：

| 字段 | 类型 | 约束 |
|---|---|---|
| `id` | string | 稳定 slug，全局唯一 |
| `title` | string | 1～128 字符 |
| `category` | string | 非空 |
| `coverUrl` | string | 相对路径或同源可访问 URL |
| `description` | string | 可为空，最长 512 字符 |
| `requiredImageCount` | integer | ≥ 1，建议上限 6 |
| `acceptedMimeTypes` | string[] | 至少包含 `image/jpeg` 或 `image/png` |
| `maxFileSizeBytes` | integer | > 0，建议 ≤ 10 MB |

---

### 3.3 素材上传与校验（FR-MAT）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-MAT-01 | P0 | 详情页必须根据 `requiredImageCount` 渲染对应数量的上传位。 | 2 张模板显示 2 个槽位；槽位编号清晰。 |
| FR-MAT-02 | P0 | 支持选择图片并在本地立即预览。 | 选择后无需等待上传完成即可看到预览。 |
| FR-MAT-03 | P0 | 支持替换和移除已选图片。 | 替换后旧预览消失、新文件重新上传；移除后槽位恢复为空。 |
| FR-MAT-04 | P0 | 必须区分每个槽位的上传中、上传成功、上传失败状态。 | 上传中显示进度/加载态；失败显示原因并可重试。 |
| FR-MAT-05 | P0 | 客户端必须校验图片数量、类型和大小，并提供可理解反馈。 | 超数量、类型不符、超大小均在选中时提示。 |
| FR-MAT-06 | P0 | 服务端必须重新校验接收到的素材，不得只依赖客户端。 | 绕过前端直接请求接口时，非法素材仍被拒绝。 |
| FR-MAT-07 | P0 | 素材必须落盘到后端本地目录或容器数据卷，并返回服务端可用的 `materialId`。 | 提交任务时传的是 `materialId`，不是浏览器 `blob:` 地址。 |
| FR-MAT-08 | P0 | 素材必须记录所属用户；任务只能引用当前用户的素材。 | 引用他人素材返回 404（对外表现为不存在，不泄露存在性），且不创建任务。 |
| FR-MAT-09 | P0 | 素材文件必须可通过同源 URL 访问，供预览和历史展示。 | 容器重启且数据卷保留时仍可访问。 |
| FR-MAT-10 | P0 | 必须防止路径穿越和不安全文件名。 | 服务端使用 UUID 重命名，不直接拼原始文件名。 |
| FR-MAT-11 | P0 | 必须处理上传文件超过框架限制的异常。 | 返回 413 和可读 JSON，而不是 500 空白页。 |
| FR-MAT-12 | P1 | 图片类型应基于文件头检测，而不是只看 `Content-Type`。 | 伪造 Content-Type 的文件被拒绝。 |
| FR-MAT-13 | P1 | 素材检查阶段应验证文件存在且图片可解码。 | 损坏图片导致任务在 `MATERIAL_CHECK` 阶段失败。 |
| FR-MAT-14 | P1 | 移除槽位时，不得物理删除已被其他任务引用的素材文件。 | 已有历史任务的结果仍可访问。 |
| FR-MAT-15 | P2 | 上传取消、断点续传、多文件拖拽排序。 | 不要求。 |

**单槽位状态机**：

```
EMPTY -> SELECTED -> UPLOADING -> UPLOADED
                         |
                         v
                       ERROR -> UPLOADING（重试）
任意状态 -> EMPTY（移除）
UPLOADED -> UPLOADING（替换）
```

**上传规则**：

- 允许类型以模板 `acceptedMimeTypes` 为准，默认 `image/jpeg`、`image/png`。
- 单文件大小以模板 `maxFileSizeBytes` 为准；全局 multipart 上限建议设为 10 MB。
- 服务端检测文件头：JPEG `FF D8 FF`，PNG `89 50 4E 47 0D 0A 1A 0A`。
- 存储路径：`{uploadDir}/{userId}/{uuid}.{ext}`。
- 数据库保存相对 `storageKey`，不保存容器绝对路径。
- 上传接口只负责落盘和基础校验；完整图片解码可在素材检查阶段执行。

---

### 3.4 任务创建与受理（FR-TASK）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-TASK-01 | P0 | 只有所有槽位均上传成功、数量与模板要求一致时，提交按钮才可用。 | 缺图或上传中时按钮禁用，并有原因提示。 |
| FR-TASK-02 | P0 | 提交中必须禁用按钮，避免重复点击。 | 连续点击只产生一个任务。 |
| FR-TASK-03 | P0 | 提交接口完成校验和任务记录创建后立即返回任务 ID，不等待制作完成。 | 即使生成阶段配置为 8 s，创建接口仍在毫秒级返回任务对象，返回内容不含制作结果。 |
| FR-TASK-04 | P0 | 创建任务时必须写入 `PENDING` 状态和创建时间。 | 查询接口能立即读到该任务。 |
| FR-TASK-05 | P0 | 服务端必须校验模板存在、素材数量、素材归属、素材 MIME、素材大小、素材状态。 | 任一项不满足返回 400/404，且不创建任务。 |
| FR-TASK-06 | P0 | 服务端必须校验制作参数（若有），拒绝未知字段或超长字段。 | 非法参数返回 400 和字段级错误。 |
| FR-TASK-07 | P0 | 任务必须绑定模板和素材槽位顺序。 | 结果整理可按槽位顺序使用素材。 |
| FR-TASK-08 | P0 | 受理成功后前端必须立即展示任务，而不是继续显示“提交中”。 | 按钮恢复可用或显示已提交；新任务卡片出现。 |
| FR-TASK-09 | P1 | 提交失败时必须保留表单和已上传素材，允许用户修正后重试。 | 失败后无需重新上传全部图片。 |
| FR-TASK-10 | P2 | 服务端幂等键（`Idempotency-Key`）。 | 可选扩展；如实现需在 README 说明。 |

**创建任务请求示例**：

```json
{
  "templateId": "demo-postcard",
  "materialIds": ["mat_01", "mat_02"],
  "params": {
    "caption": "夏日旅行"
  }
}
```

**校验顺序**（建议）：

1. 登录态是否有效。
2. 模板是否存在且启用。
3. `materialIds` 数量是否等于 `requiredImageCount`。
4. 是否存在重复素材 id。
5. 每个素材是否属于当前用户。
6. 每个素材是否存在且状态为 `UPLOADED`。
7. 每个素材 MIME 是否在模板允许列表内。
8. 每个素材大小是否不超过模板上限。
9. `params` 是否为合法 JSON、字段是否已知、长度是否合规。
10. 写入 `tasks` 与 `task_materials`，状态 `PENDING`。

**响应**：

```json
{
  "id": "tsk_01J...",
  "templateId": "demo-postcard",
  "templateTitle": "旅行明信片",
  "status": "PENDING",
  "stage": null,
  "failedStage": null,
  "errorCode": null,
  "errorMessage": null,
  "resultUrl": null,
  "version": 1,
  "createdAt": "2026-10-28T10:00:00.000Z",
  "startedAt": null,
  "finishedAt": null
}
```

HTTP 建议使用 `201 Created` 或 `202 Accepted`，并在响应体返回完整任务对象，便于前端立即渲染。

---

### 3.5 异步多阶段处理（FR-PIPE）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-PIPE-01 | P0 | 后台必须按固定顺序执行：素材检查 → 模拟生成 → 结果整理。 | 事件日志/阶段字段顺序正确，不可跳步。 |
| FR-PIPE-02 | P0 | 每个阶段必须先在数据库中提交当前阶段，再执行该阶段工作和等待。 | 处理期间查询能看到 `MATERIAL_CHECK`、`GENERATING`、`ASSEMBLING`。 |
| FR-PIPE-03 | P0 | 阶段推进必须由 Java 后台执行，不得由前端定时切换文案，也不得由查询请求驱动。 | 关闭前端后任务继续；只调用查询接口不会推进阶段。 |
| FR-PIPE-04 | P0 | 任一阶段失败后必须立即停止后续阶段。 | 在 `GENERATING` 注入失败时，`ASSEMBLING` 不执行，事件日志无该阶段。 |
| FR-PIPE-05 | P0 | 失败时必须保存失败阶段、错误码和可理解的原因。 | 查询和 UI 能展示 `failedStage`、`errorMessage`。 |
| FR-PIPE-06 | P0 | 成功时必须保存可预览的结果地址。 | `resultUrl` 可访问且返回图片。 |
| FR-PIPE-07 | P0 | 终态不可回退。 | 已 `SUCCEEDED`/`FAILED` 的任务不会被后续查询或重复消费改回 `PROCESSING`。 |
| FR-PIPE-08 | P0 | 查询接口必须只读。 | 查询前后任务的 `version` 和 `updatedAt` 不变（查询本身不写库）。 |
| FR-PIPE-09 | P1 | 支持多任务排队；并行度为可选增强（题目不要求真正并行执行，串行队列即满足）。实现并行时线程池大小可配置。 | 串行下 3 个任务依次处理；若开启并行度 2，可观察到 2 个处理中、1 个等待中。 |
| FR-PIPE-10 | P0 | worker 顶层必须捕获所有异常并落库为失败，不能留下永久处理中。 | 模拟未捕获异常时任务变为 `FAILED`，原因可读。 |
| FR-PIPE-11 | P1 | 服务重启后必须对残留的 `PROCESSING` 任务做对账处理。 | 启动时将其标记为 `FAILED`（原因：服务重启中断）或安全地重新排队；README 说明。 |
| FR-PIPE-12 | P1 | `PENDING` 任务必须能被调度器重新拾起。 | 创建后立即重启后端，任务仍会被处理。 |
| FR-PIPE-13 | P1 | 同一任务不得被两个 worker 同时处理。 | 调度器使用条件更新原子认领；单实例下不出现重复阶段。 |
| FR-PIPE-14 | P2 | 断点续跑、失败重试、优先级队列、任务取消。 | 不要求。 |

**设计决策：数据库队列 + 调度器 + 有界线程池**

1. 提交接口只插入 `PENDING` 任务，事务提交后返回。
2. 后台调度器按固定间隔查询 `PENDING` 任务，按创建时间升序。
3. 使用条件更新原子认领：`UPDATE tasks SET status='PROCESSING', started_at=... WHERE id=? AND status='PENDING'`，受影响行数为 1 才提交给线程池。
4. 线程池 core/max 默认 1（串行，题目不要求真正并行），队列容量 100；通过 `app.pipeline.concurrency` 可调到 2 或更高，用于演示并行。
5. worker 逐步执行阶段，每次阶段更新独立提交。
6. 认领后若线程池拒绝，任务回退为 `PENDING` 或标记 `FAILED`（`QUEUE_REJECTED`），不得停留在无主状态。

**为什么不用 `@Async` 直接提交**：

- 提交事务尚未提交时，异步线程可能查不到任务。
- 同类内部调用 `@Async` 会绕过代理。
- 线程池拒绝策略处理不当会导致任务永久等待。
- 数据库队列让等待态天然可见，也方便重启后重新拾起。

**阶段执行伪代码**：

```
claim(task)                          // PENDING -> PROCESSING
for stage in [MATERIAL_CHECK, GENERATING, ASSEMBLING]:
    updateStage(task, stage)         // 提交后查询可见
    sleep(delay[stage])
    result = doStage(stage)
    if result.failed:
        markFailed(task, stage, result.code, result.message)
        return
markSucceeded(task, resultUrl)       // stage = DONE
```

**阶段职责**：

| 阶段 | 模拟工作 | 可能的失败 |
|---|---|---|
| `MATERIAL_CHECK` | 检查素材数量、文件存在、MIME、大小、图片解码 | 文件缺失、损坏、格式不符 |
| `GENERATING` | 等待模拟耗时；按预置结果生成图片；可注入失败 | `SIMULATED_FAILURE`、生成异常 |
| `ASSEMBLING` | 校验生成结果、写入结果文件、记录尺寸/地址 | 结果写入失败、结果不可读 |

---

### 3.6 任务查询与历史记录（FR-QUERY）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-QUERY-01 | P0 | 提供任务详情查询，返回状态、阶段、失败信息、结果地址。 | 字段完整，状态与数据库一致。 |
| FR-QUERY-02 | P0 | 提供当前用户历史任务列表，包含创建时间、状态、阶段、结果或失败原因。 | 刷新后仍能恢复；按创建时间倒序。 |
| FR-QUERY-03 | P0 | 提供活跃任务查询，供刷新后恢复跟踪。 | 只返回当前用户的非终态任务。 |
| FR-QUERY-04 | P0 | 成功结果必须支持图片预览。 | 历史卡片和详情弹窗均可显示图片。 |
| FR-QUERY-05 | P1 | 历史列表支持分页。 | 返回 `items/page/size/total`，前端可加载更多或翻页。 |
| FR-QUERY-06 | P0 | 查询结果必须包含单调递增的 `version`，供前端丢弃晚到响应。 | 同一任务旧版本响应不会覆盖新状态。 |
| FR-QUERY-07 | P0 | 查询接口暂时不可用时，前端不得把任务标记为失败。 | 状态保持不变，显示同步错误与重试入口。 |
| FR-QUERY-08 | P1 | 提供任务事件时间线（可选接口），用于展示阶段历史。 | 事件顺序与阶段推进一致。 |
| FR-QUERY-09 | P2 | 按状态筛选、关键字搜索。 | 不要求。 |

---

### 3.7 模拟控制（FR-SIM）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-SIM-01 | P0 | 三个阶段耗时必须可配置。 | 修改配置后处理时长明显变化。 |
| FR-SIM-02 | P1 | 支持运行时调整耗时，无需重启。 | 开发接口更新后，新任务按新耗时执行。 |
| FR-SIM-03 | P0 | 提供可重复触发指定阶段失败的模拟方式。 | 同一配置下连续创建任务，均在指定阶段失败。 |
| FR-SIM-04 | P0 | 失败后停止后续阶段，保存失败阶段和原因。 | 事件日志无后续阶段。 |
| FR-SIM-05 | P0 | 失败计划必须在任务创建时确定并持久化，保证可重复和可解释。 | 修改配置不影响已创建任务的既定失败计划。 |
| FR-SIM-06 | P0 | 模拟接口不得出现在正式页面或模板字段中。 | 前端页面无失败注入入口；接口受配置开关保护。 |
| FR-SIM-07 | P0 | 不允许随机失败。 | 无 `Math.random()` 等随机逻辑。 |
| FR-SIM-08 | P1 | 提供查询当前模拟配置的接口。 | 返回失败阶段、剩余次数、各阶段耗时。 |
| FR-SIM-09 | P2 | 全局慢速模式、按用户注入、按模板注入。 | 不要求。 |
| FR-SIM-10 | P1 | 模拟配置只能有一个权威来源。 | 运行时以 `simulation_config` 表为准，`app.simulation.delays.*` 只用于首次初始化；不存在两处配置同时生效。 |

**模拟配置项**：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `app.simulation.delays.material-check-ms` | 800 | 素材检查阶段耗时（仅用于 `simulation_config` 首次初始化） |
| `app.simulation.delays.generating-ms` | 2500 | 模拟生成阶段耗时（同上） |
| `app.simulation.delays.assembling-ms` | 800 | 结果整理阶段耗时（同上） |
| `app.simulation.dev-api-enabled` | `false`（compose 演示环境设为 `true`） | 是否开放开发接口 |
| `simulation_config.fail_stage` | `null` | 下一个/下 N 个任务的失败阶段 |
| `simulation_config.fail_remaining` | 0 | 剩余注入次数；为 0 且 `fail_stage` 非空时表示持续失败模式 |

**开发接口**：

```
GET  /api/dev/simulation
POST /api/dev/simulation
```

请求示例：

```json
{
  "failStage": "GENERATING",
  "failNext": 1,
  "delayMaterialCheckMs": 800,
  "delayGeneratingMs": 5000,
  "delayAssemblingMs": 800
}
```

行为：

- `failNext` 设置为 1 时，下一个创建的任务在 `GENERATING` 阶段失败，随后自动归零。
- `failStage` 非空且 `failNext=0` 时进入“持续失败”模式：之后创建的每个任务都在该阶段失败，直到显式清空（`failStage=null`）或重新设置 `failNext>0`。
- 任务创建时将当时的 `failStage` 快照到 `tasks.planned_fail_stage`。
- 未消费的注入计划保留，直到被任务消费或显式清空（`failStage=null`）。
- 开发接口默认关闭；仅在 `dev`/`demo` 配置下开启，README 说明风险。

---

### 3.8 前端异步状态管理（FR-FE）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-FE-01 | P0 | 必须区分至少六种状态：素材上传中、提交请求中、任务等待中、任务处理中、任务成功、任务失败。 | 不同状态有不同文案和视觉反馈，不共用一个 `loading`。 |
| FR-FE-02 | P0 | 每个任务必须有独立的记录，按任务 id 索引。 | 多任务并发更新时互不覆盖。 |
| FR-FE-03 | P0 | 只对非终态任务自动跟踪。 | 任务进入 `SUCCEEDED`/`FAILED` 后停止请求。 |
| FR-FE-04 | P0 | 没有活跃任务时不得继续轮询。 | 网络面板无周期性请求。 |
| FR-FE-05 | P0 | 离开相关页面、登出、切换用户时必须取消在途请求并清理定时器。 | 无卸载后 setState、无旧用户数据闪现。 |
| FR-FE-06 | P0 | 刷新页面后必须从后端恢复历史，并继续跟踪未完成任务。 | 刷新后处理中任务仍持续更新。 |
| FR-FE-07 | P0 | 晚到响应不得覆盖新状态。 | 用 `version` 或请求序号丢弃旧响应。 |
| FR-FE-08 | P0 | 查询异常必须与任务失败分开：只设置同步错误，保留最后一次已知状态和数据。 | 停掉后端时卡片仍显示原状态，附“刷新失败，重试”。 |
| FR-FE-09 | P0 | 提交成功后必须清空提交中状态，并立即插入任务。 | 按钮不再转圈；任务卡片出现。 |
| FR-FE-10 | P0 | 切换用户时必须清空旧用户任务、历史和同步状态。 | 新用户看不到旧用户任务。 |
| FR-FE-11 | P1 | 结果图片加载失败时显示占位，不改变任务状态。 | 图片 404 不影响任务显示为成功。 |
| FR-FE-12 | P1 | 前端状态逻辑应可单元测试，避免依赖真实定时器和网络。 | reducer/selector 可独立测试。 |
| FR-FE-13 | P2 | 页面隐藏时暂停轮询，恢复可见时立即刷新。 | 可选。 |

**前端任务视图模型**：

```ts
type TaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED'
type TaskStage = 'MATERIAL_CHECK' | 'GENERATING' | 'ASSEMBLING' | 'DONE'

type TaskView = {
  id: string
  templateId: string
  templateTitle: string
  status: TaskStatus
  stage: TaskStage | null
  failedStage: TaskStage | null
  errorCode: string | null
  errorMessage: string | null
  resultUrl: string | null
  version: number
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  sync: {
    state: 'idle' | 'refreshing' | 'error'
    message?: string
    lastSyncedAt?: string
  }
}
```

**状态管理选型**：Redux Toolkit 2.x。`tasks` slice 按 id 归一化存储，`sync` 作为任务级子状态；轮询由独立 hook 驱动，不放进 store。选择理由：reducer/selector 为纯函数，可直接覆盖 TC-FE-01～10，且不把轮询生命周期与组件渲染耦合。

**轮询算法要求**：

1. 从 store 选择 `status` 为 `PENDING` 或 `PROCESSING` 的任务 id。
2. 若集合为空，清除定时器并停止请求。
3. 使用链式 `setTimeout`，上一轮请求全部结束后再安排下一轮，避免请求重叠。
4. 每轮为每个活跃任务发起查询，或调用批量活跃任务接口。
5. 响应按 `id + version` 合并；旧版本丢弃。
6. 单个任务查询失败：只把该任务 `sync.state` 设为 `error`，保留 `status/stage/result`，下一轮继续尝试。
7. 组件卸载、用户切换、登出时 abort 所有请求并清除定时器。
8. 任务进入终态后立即从跟踪集合移除。

**状态与文案映射**：

| 场景 | 展示 | 不应展示 |
|---|---|---|
| 槽位上传中 | “上传中…” | “提交中” |
| 提交请求中 | 按钮 loading，禁用 | 任务失败 |
| 任务等待 | “等待处理” | “处理中” |
| 任务处理中 | “处理中 · 正在生成” | 百分比（不要求） |
| 任务成功 | 结果图 + “已完成” | 轮询指示 |
| 任务失败 | 失败阶段 + 原因 | 同步错误 |
| 查询失败 | “状态刷新失败，点击重试” | 任务失败 |

---

### 3.9 界面与可用性（FR-UI）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-UI-01 | P0 | 模板列表页应包含顶栏、分类筛选、卡片网格，布局清晰。 | 若随题提供 UI-01，主结构与之一致；未提供时按 3.9 页面结构建议验收。均不要求逐像素还原。 |
| FR-UI-02 | P0 | 模板详情页应包含封面/预览、标题、描述、素材要求、上传位和提交按钮。 | 若提供 UI-02，主结构与之一致；否则按 3.9 页面结构建议验收。 |
| FR-UI-03 | P0 | 历史/结果区域应展示任务卡片、处理状态、失败原因或成功图片。 | 若提供 UI-03，主结构与之一致；否则按 3.9 页面结构建议验收。 |
| FR-UI-04 | P0 | 所有异步状态必须有清晰反馈。 | 上传、提交、等待、处理、成功、失败、查询异常均可区分。 |
| FR-UI-05 | P0 | 按钮、输入、对话框使用语义化元素。 | 使用 `<button>`、`<label>`、`<dialog>` 或等价可访问实现。 |
| FR-UI-06 | P0 | 保留键盘焦点；图标按钮和上传控件必须有可理解标签。 | Tab 可遍历；图标按钮有 `aria-label`；上传 input 有关联 label。 |
| FR-UI-07 | P0 | 小屏下可完成主要操作。 | 375 px 宽度下无横向滚动，可上传、提交、查看历史。 |
| FR-UI-08 | P0 | 加载中、空数据、请求失败、404 必须有可理解反馈。 | 骨架/占位、空态文案、错误重试、返回入口。 |
| FR-UI-09 | P1 | 深色主题应有足够对比度，焦点样式明显。 | 文字、边框、按钮、禁用态可辨识。 |
| FR-UI-10 | P0 | 不实现题目范围外的功能。 | 不出现积分、会员、账单、删除账号、下载、重新制作等半成品入口。 |
| FR-UI-11 | P2 | 动效、骨架屏细节、空状态插画。 | 可选。 |

**页面结构建议**：

- 顶栏：产品名、当前用户名、登出。若参考图含语言切换、积分、会员信息，按题目不做。
- 模板列表：一层分类筛选即可，不照搬参考图中的两层筛选。
- 模板详情（桌面）：左侧制作面板，右侧历史面板；小屏上下堆叠。
- 历史卡片：封面/结果图、状态或失败原因、创建时间；处理中可展示阶段文案。
- 结果预览：点击成功卡片打开对话框，支持 Esc 关闭和焦点返回。

---

### 3.10 错误处理（FR-ERR）

| 编号 | 优先级 | 需求 | 验收要点 |
|---|---|---|---|
| FR-ERR-01 | P0 | 前端必须区分上传错误、提交错误、任务失败、查询错误、登录失效。 | 五类错误有不同文案和处理方式。 |
| FR-ERR-02 | P0 | 查询错误不得修改任务状态、阶段、结果或失败原因。 | 后端停机时任务仍显示最后一次已知状态。 |
| FR-ERR-03 | P0 | 查询错误必须提供恢复途径。 | 显示重试按钮；下一轮轮询自动恢复。 |
| FR-ERR-04 | P0 | 服务端错误响应必须是可解析的 JSON，包含稳定错误码和可读消息。 | 不返回 HTML 错误页或空响应体。 |
| FR-ERR-05 | P0 | 401 时必须跳转登录并清理本地任务状态。 | 会话过期后不继续轮询。 |
| FR-ERR-06 | P0 | 上传/提交失败后必须保留用户已填写内容和已上传素材。 | 用户无需从头开始。 |
| FR-ERR-07 | P0 | 失败任务的失败原因必须来自服务端持久化记录，而不是前端猜测。 | 刷新后失败原因仍存在。 |
| FR-ERR-08 | P1 | 前端对未知错误提供兜底文案和重试入口。 | 网络断开、502、超时均有反馈。 |

**错误分层**：

| 类型 | 触发 | 处理 |
|---|---|---|
| 上传错误 | 单文件上传失败 | 槽位级错误，允许重试/替换 |
| 提交错误 | 创建任务请求失败 | 表单级错误，保留数据，允许重试 |
| 任务失败 | 服务端 `status=FAILED` | 展示失败阶段和原因，不自动重试 |
| 查询错误 | 轮询请求失败 | 任务级同步错误，保留状态，提供重试 |
| 认证错误 | 401 | 跳转登录，清理状态 |

---

## 4. 状态机与业务规则

### 4.1 任务状态

| 状态 | 含义 | 允许的下一状态 |
|---|---|---|
| `PENDING` | 已受理，等待 worker | `PROCESSING`、`FAILED`（调度失败/系统错误） |
| `PROCESSING` | worker 正在执行阶段 | `PROCESSING`（阶段推进）、`SUCCEEDED`、`FAILED` |
| `SUCCEEDED` | 全部阶段成功 | 无 |
| `FAILED` | 某阶段失败或系统错误 | 无 |

### 4.2 阶段

| 阶段 | 含义 | 下一阶段 |
|---|---|---|
| `MATERIAL_CHECK` | 素材检查 | `GENERATING` |
| `GENERATING` | 模拟生成 | `ASSEMBLING` |
| `ASSEMBLING` | 结果整理 | `DONE` |
| `DONE` | 全部完成 | 无 |

### 4.3 状态转换表

| 当前状态 | 事件 | 新状态 | 新阶段 | 写入字段 |
|---|---|---|---|---|
| `PENDING` | 调度器认领 | `PROCESSING` | `MATERIAL_CHECK` | `started_at`、`version+1` |
| `PROCESSING` | 素材检查开始 | `PROCESSING` | `MATERIAL_CHECK` | `version+1` |
| `PROCESSING` | 素材检查通过 | `PROCESSING` | `GENERATING` | `version+1` |
| `PROCESSING` | 模拟生成通过 | `PROCESSING` | `ASSEMBLING` | `version+1` |
| `PROCESSING` | 结果整理通过 | `SUCCEEDED` | `DONE` | `result_url`、`finished_at`、`version+1` |
| `PROCESSING` | 任一阶段失败 | `FAILED` | 保持失败阶段 | `failed_stage`、`error_code`、`error_message`、`finished_at`、`version+1` |
| `PENDING` | 调度失败/系统错误 | `FAILED` | 无 | `error_code`、`error_message`、`finished_at`、`version+1` |
| `SUCCEEDED` | 任意写入尝试 | 不变 | 不变 | 拒绝 |
| `FAILED` | 任意写入尝试 | 不变 | 不变 | 拒绝 |

### 4.4 不变量

1. 查询接口不写数据库。
2. 只有调度器和 worker 可以修改 `status`、`stage`、`failed_stage`、`error_*`、`result_*`。
3. `SUCCEEDED` 蕴含 `stage=DONE`、`result_url != null`、`failed_stage is null`。
4. `FAILED` 蕴含 `failed_stage != null` 或 `error_code` 为系统级错误、`error_message != null`。
5. `PENDING` 蕴含 `stage is null`、`started_at is null`。
6. `PROCESSING` 蕴含 `stage ∈ {MATERIAL_CHECK, GENERATING, ASSEMBLING}`。
7. `version` 每次更新必须递增，且永不减少。
8. `created_at <= started_at <= finished_at`（允许部分为空）。
9. 任务的 `user_id`、`template_id`、素材绑定在创建后不可变。
10. 已进入终态的任务不得被重新处理。
11. 阶段只能前进，不能回退。
12. 被非终态任务引用的素材不得物理删除。

### 4.5 失败规则

- 失败原因必须面向用户可理解，例如：
  - `素材检查失败：第 2 张图片无法解码`
  - `模拟生成失败：生成服务返回错误（模拟注入）`
  - `结果整理失败：无法写入结果文件`
- 失败后不再执行后续阶段。
- 失败任务不提供自动重试或“重新制作”按钮。
- 查询失败不属于任务失败，不得写入 `FAILED`。

### 4.6 并发与幂等

- 调度器使用条件更新认领任务，保证同一任务只被一个 worker 处理。
- 单实例部署；不实现分布式锁。多实例会导致重复消费，README 明确说明。
- 前端通过禁用按钮防止重复提交；服务端幂等键为可选扩展。
- 重复调用查询接口不会改变任务。
- 重复执行 worker 对终态任务无副作用。

### 4.7 业务校验规则

| 规则 | 客户端 | 服务端 | 错误码 |
|---|---|---|---|
| 模板存在 | 是 | 是 | `TEMPLATE_NOT_FOUND` |
| 素材数量等于要求 | 是 | 是 | `MATERIAL_COUNT_MISMATCH` |
| 素材不重复 | 是 | 是 | `DUPLICATE_MATERIAL` |
| 素材属于当前用户 | 否 | 是 | `MATERIAL_FORBIDDEN` |
| 素材存在且已上传 | 否 | 是 | `MATERIAL_NOT_FOUND` |
| MIME 在允许列表 | 是 | 是 | `MATERIAL_MIME_NOT_ALLOWED` |
| 大小不超过模板上限 | 是 | 是 | `MATERIAL_TOO_LARGE` |
| 图片可解码 | 可提示 | 是（素材检查阶段） | `MATERIAL_UNREADABLE` |
| 参数合法 | 可选 | 是 | `INVALID_PARAMS` |
| 文件大小不超过全局上限 | 是 | 是 | `PAYLOAD_TOO_LARGE` |

---

## 5. 数据模型

### 5.1 实体关系

```
users 1 ── N sessions
users 1 ── N materials
users 1 ── N tasks
templates 1 ── N tasks
tasks N ── N materials  (task_materials)
tasks 1 ── N task_events
```

### 5.2 表结构

#### `users`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | `usr_` + ULID（共 30 字符） |
| `username` | VARCHAR(64) | UNIQUE NOT NULL | 登录名 |
| `display_name` | VARCHAR(64) | NOT NULL | 展示名 |
| `password_hash` | VARCHAR(100) | NOT NULL | BCrypt |
| `created_at` | DATETIME(3) | NOT NULL | UTC |

#### `sessions`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `token_hash` | CHAR(64) | PK | 会话令牌 SHA-256 |
| `user_id` | VARCHAR(36) | NOT NULL, INDEX | 所属用户 |
| `expires_at` | DATETIME(3) | NOT NULL | 过期时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |

#### `templates`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | VARCHAR(64) | PK | slug |
| `title` | VARCHAR(128) | NOT NULL | 标题 |
| `category` | VARCHAR(64) | NOT NULL, INDEX | 分类 |
| `cover_url` | VARCHAR(255) | NOT NULL | 同源相对路径 |
| `description` | VARCHAR(512) | NULL | 描述 |
| `required_image_count` | INT | NOT NULL, CHECK > 0 | 所需图片数 |
| `accepted_mime_types` | JSON / VARCHAR(255) | NOT NULL | 允许的 MIME |
| `max_file_size_bytes` | BIGINT | NOT NULL, CHECK > 0 | 单文件上限 |
| `enabled` | BOOLEAN | NOT NULL DEFAULT TRUE | 是否可见 |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 排序 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |

#### `materials`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | `mat_` + ULID（共 30 字符） |
| `user_id` | VARCHAR(36) | NOT NULL, INDEX | 所属用户 |
| `original_name` | VARCHAR(255) | NULL | 原始文件名，仅展示 |
| `storage_key` | VARCHAR(255) | NOT NULL | 相对存储路径 |
| `mime_type` | VARCHAR(64) | NOT NULL | 检测后的 MIME |
| `size_bytes` | BIGINT | NOT NULL | 文件大小 |
| `width` | INT | NULL | 图片宽度 |
| `height` | INT | NULL | 图片高度 |
| `status` | VARCHAR(16) | NOT NULL | `UPLOADED` |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |

索引：`(user_id, created_at)`。

#### `tasks`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | `tsk_` + ULID（共 30 字符） |
| `user_id` | VARCHAR(36) | NOT NULL, INDEX | 所属用户 |
| `template_id` | VARCHAR(64) | NOT NULL | 模板 |
| `status` | VARCHAR(16) | NOT NULL, INDEX | 任务状态 |
| `stage` | VARCHAR(32) | NULL | 当前阶段 |
| `failed_stage` | VARCHAR(32) | NULL | 失败阶段 |
| `error_code` | VARCHAR(64) | NULL | 错误码 |
| `error_message` | VARCHAR(512) | NULL | 可读原因 |
| `result_url` | VARCHAR(255) | NULL | 结果相对地址 |
| `result_width` | INT | NULL | 结果宽度 |
| `result_height` | INT | NULL | 结果高度 |
| `params_json` | JSON | NULL | 制作参数 |
| `planned_fail_stage` | VARCHAR(32) | NULL | 模拟失败快照 |
| `version` | INT | NOT NULL DEFAULT 0 | 乐观版本 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `started_at` | DATETIME(3) | NULL | 开始处理 |
| `finished_at` | DATETIME(3) | NULL | 结束时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 最后更新 |

索引：`(user_id, created_at DESC)`、`(status, created_at)`。

#### `task_materials`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `task_id` | VARCHAR(36) | PK 之一 | 任务 |
| `material_id` | VARCHAR(36) | PK 之一 | 素材 |
| `slot_index` | INT | NOT NULL | 槽位顺序 |

唯一约束：`(task_id, slot_index)`。

#### `task_events`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | PK | 自增 |
| `task_id` | VARCHAR(36) | NOT NULL, INDEX | 任务 |
| `stage` | VARCHAR(32) | NOT NULL | 阶段 |
| `event_type` | VARCHAR(32) | NOT NULL | `STAGE_STARTED`/`STAGE_SUCCEEDED`/`STAGE_FAILED`/`TASK_SUCCEEDED` |
| `message` | VARCHAR(512) | NULL | 说明 |
| `created_at` | DATETIME(3) | NOT NULL | 时间 |

索引：`(task_id, id)`。

#### `simulation_config`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INT | PK，固定为 1 | 单例配置 |
| `fail_stage` | VARCHAR(32) | NULL | 目标失败阶段 |
| `fail_remaining` | INT | NOT NULL DEFAULT 0 | 剩余注入次数 |
| `delay_material_check_ms` | INT | NOT NULL | 耗时 |
| `delay_generating_ms` | INT | NOT NULL | 耗时 |
| `delay_assembling_ms` | INT | NOT NULL | 耗时 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

> **配置来源唯一**：耗时的权威来源是 `simulation_config` 单例表（启动时由 `app.simulation.delays.*` 初始化），开发接口直接修改该表；不实现“同时读配置文件和数据库”的双来源逻辑。失败计划必须在任务创建时快照到 `tasks.planned_fail_stage`，保证可重复。

### 5.3 种子数据

要求：

- 2 个用户：`demo / demo1234`、`alice / alice1234`，密码使用 BCrypt 哈希。
- 至少 3 个分类：如“创意图片”“宠物写真”“节日贺卡”“婚礼情侣”。
- 至少 6 个模板，`requiredImageCount` 覆盖 1、2、3；`maxFileSizeBytes` 覆盖 2 MB、5 MB、10 MB。
- 所有封面文件随仓库提交，容器内可访问；不得依赖外网。
- 默认不预置任务记录，保留“空历史”状态供演示；也可预置 1 条成功、1 条失败记录，但结果文件必须真实存在。

### 5.4 文件存储

```
{APP_DATA_DIR}/
  uploads/{userId}/{uuid}.{ext}
  results/{taskId}.png
```

- `uploads` 和 `results` 位于同一个数据卷。
- 数据库只保存相对 `storageKey` / `result_url`。
- 结果 URL 建议为 `/api/tasks/{taskId}/result`，由后端校验归属后返回文件。
- 素材 URL 建议为 `/api/materials/{id}/content`，同样校验归属。
- 封面可以是前端 `public/demo/*` 或后端静态资源，但必须是同源可访问路径。

---

## 6. 接口规格

### 6.1 通用约定

- 基础路径：`/api`。
- 请求/响应：`application/json; charset=utf-8`；上传为 `multipart/form-data`。
- 字段命名：camelCase。
- 时间：ISO-8601 UTC，例如 `2026-10-28T10:00:00.000Z`。
- ID：字符串。
- 认证：Cookie `XIAOYING_SESSION`。
- 分页：`page` 从 0 开始，`size` 默认 20，最大 100。
- 错误响应统一格式：

```json
{
  "code": "MATERIAL_COUNT_MISMATCH",
  "message": "需要 2 张图片，当前 1 张",
  "fieldErrors": {
    "materialIds": "数量不匹配"
  },
  "traceId": "req_01J..."
}
```

- HTTP 状态码：

| 状态码 | 场景 |
|---|---|
| 200 | 查询/更新成功 |
| 201 | 创建任务、上传素材成功 |
| 204 | 登出成功 |
| 400 | 参数/业务校验失败 |
| 401 | 未登录或会话过期 |
| 403 | 无权限（仅用于非资源归属场景；资源归属与“不存在”统一用 404） |
| 404 | 模板/任务/素材不存在或不属于当前用户 |
| 409 | 冲突（可选幂等） |
| 413 | 文件过大 |
| 415 | 不支持的媒体类型 |
| 500 | 未预期服务端错误 |
| 503 | 服务暂不可用（如队列拒绝） |

### 6.2 接口清单

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 否 | 登录 |
| POST | `/api/auth/logout` | 是 | 登出 |
| GET | `/api/auth/me` | 是 | 当前用户 |
| GET | `/api/templates/categories` | 否 | 分类及数量 |
| GET | `/api/templates` | 否 | 模板列表，支持 `category` |
| GET | `/api/templates/{id}` | 否 | 模板详情 |
| POST | `/api/materials` | 是 | 上传素材 |
| GET | `/api/materials/{id}/content` | 是 | 素材内容 |
| POST | `/api/tasks` | 是 | 创建任务 |
| GET | `/api/tasks/{id}` | 是 | 任务详情 |
| GET | `/api/tasks` | 是 | 历史列表 |
| GET | `/api/tasks/active` | 是 | 活跃任务 |
| GET | `/api/tasks/{id}/result` | 是 | 结果图片 |
| GET | `/api/tasks/{id}/events` | 是 | 事件时间线（可选） |
| GET | `/api/dev/simulation` | 开发开关 | 查询模拟配置 |
| POST | `/api/dev/simulation` | 开发开关 | 更新模拟配置 |
| GET | `/actuator/health` | 否 | 健康检查 |

### 6.3 关键接口

#### POST `/api/auth/login`

请求：

```json
{ "username": "demo", "password": "demo1234" }
```

响应 200：

```json
{
  "user": {
    "id": "usr_01J...",
    "username": "demo",
    "displayName": "Demo User"
  }
}
```

响应头：`Set-Cookie: XIAOYING_SESSION=...; HttpOnly; SameSite=Lax; Path=/; Max-Age=604800`。

#### GET `/api/templates?category=创意图片`

响应 200：

```json
{
  "items": [
    {
      "id": "demo-postcard",
      "title": "旅行明信片",
      "category": "创意图片",
      "coverUrl": "/demo/postcard-cover.png",
      "description": "上传两张图片，模拟生成一张旅行明信片",
      "requiredImageCount": 2,
      "acceptedMimeTypes": ["image/jpeg", "image/png"],
      "maxFileSizeBytes": 5242880
    }
  ]
}
```

#### POST `/api/materials`

请求：`multipart/form-data`，字段名 `file`。

响应 201：

```json
{
  "id": "mat_01J...",
  "url": "/api/materials/mat_01J.../content",
  "mimeType": "image/png",
  "sizeBytes": 245760,
  "width": 1200,
  "height": 1600
}
```

错误：

- 413 `PAYLOAD_TOO_LARGE`
- 415 `MATERIAL_MIME_NOT_ALLOWED`
- 400 `MATERIAL_UNREADABLE`

#### POST `/api/tasks`

请求：

```json
{
  "templateId": "demo-postcard",
  "materialIds": ["mat_01", "mat_02"],
  "params": { "caption": "夏日旅行" }
}
```

响应 201：任务对象（见 3.4）。

#### GET `/api/tasks/{id}`

响应 200：

```json
{
  "id": "tsk_01J...",
  "templateId": "demo-postcard",
  "templateTitle": "旅行明信片",
  "status": "PROCESSING",
  "stage": "GENERATING",
  "failedStage": null,
  "errorCode": null,
  "errorMessage": null,
  "resultUrl": null,
  "version": 4,
  "createdAt": "2026-10-28T10:00:00.000Z",
  "startedAt": "2026-10-28T10:00:01.000Z",
  "finishedAt": null
}
```

成功：

```json
{
  "status": "SUCCEEDED",
  "stage": "DONE",
  "resultUrl": "/api/tasks/tsk_01J.../result",
  "version": 6,
  "finishedAt": "2026-10-28T10:00:10.000Z"
}
```

失败：

```json
{
  "status": "FAILED",
  "stage": "GENERATING",
  "failedStage": "GENERATING",
  "errorCode": "SIMULATED_FAILURE",
  "errorMessage": "模拟生成失败：生成服务返回错误",
  "version": 5,
  "finishedAt": "2026-10-28T10:00:08.000Z"
}
```

#### GET `/api/tasks?page=0&size=20`

响应 200：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total": 0
}
```

#### GET `/api/tasks/active`

响应 200：`{ "items": [ ... ] }`，只包含 `PENDING`/`PROCESSING`。

#### GET `/api/tasks/{id}/result`

- 成功任务返回 `image/png` 或 `image/jpeg`。
- 未成功、非本人、文件不存在返回 404。
- 响应头建议：`Cache-Control: private, max-age=60`。

#### GET/POST `/api/dev/simulation`

见 3.7。开启条件：`app.simulation.dev-api-enabled=true`；未开启返回 404。

### 6.4 错误码

| 错误码 | HTTP | 含义 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 通用参数校验失败 |
| `TEMPLATE_NOT_FOUND` | 404 | 模板不存在或未启用 |
| `MATERIAL_COUNT_MISMATCH` | 400 | 素材数量不符 |
| `DUPLICATE_MATERIAL` | 400 | 素材重复 |
| `MATERIAL_NOT_FOUND` | 404 | 素材不存在或未上传完成 |
| `MATERIAL_FORBIDDEN` | 404 | 素材不属于当前用户（对外与不存在一致，不泄露存在性） |
| `MATERIAL_MIME_NOT_ALLOWED` | 415 | 类型不支持 |
| `MATERIAL_TOO_LARGE` | 413 | 文件超过模板限制 |
| `MATERIAL_UNREADABLE` | 400 | 图片无法解码 |
| `PAYLOAD_TOO_LARGE` | 413 | 超过全局上传限制 |
| `INVALID_PARAMS` | 400 | 制作参数非法 |
| `SIMULATED_FAILURE` | — | 模拟阶段失败（任务内错误码） |
| `RESULT_WRITE_FAILED` | — | 结果文件写入失败 |
| `SERVER_RESTART` | — | 服务重启导致处理中断 |
| `QUEUE_REJECTED` | — | 调度/线程池拒绝 |
| `INTERNAL_ERROR` | 500 | 未预期错误 |

---

## 7. 非功能需求

| 编号 | 类别 | 要求 |
|---|---|---|
| NFR-PERF-01 | 性能 | 创建任务接口只做校验与落库，返回不包含任何制作等待；生成阶段配置为 8 s 时仍应立即返回。 |
| NFR-PERF-02 | 性能 | 任务查询接口 p95 < 100 ms。 |
| NFR-PERF-03 | 性能 | 前端轮询间隔建议 1～2 秒；单页活跃任务建议不超过 20 个。 |
| NFR-PERF-04 | 性能 | 单文件上传上限 10 MB；模板默认 5 MB。 |
| NFR-REL-01 | 可靠性 | 任务状态、阶段、失败信息、结果地址必须持久化，服务重启不丢已有记录。 |
| NFR-REL-02 | 可靠性 | worker 必须捕获所有异常，不能留下永久 `PROCESSING`。 |
| NFR-REL-03 | 可靠性 | 终态不可回退。 |
| NFR-REL-04 | 可靠性 | 单实例假设；不支持多实例并行消费，README 说明。 |
| NFR-SEC-01 | 安全 | 会话 Cookie 必须 `HttpOnly`、`SameSite=Lax`；密码使用 BCrypt。 |
| NFR-SEC-02 | 安全 | 所有资源和任务查询必须校验归属，禁止越权。 |
| NFR-SEC-03 | 安全 | 上传文件必须重命名、限制类型和大小，防止路径穿越。 |
| NFR-SEC-04 | 安全 | 仓库不得包含真实凭据；`.env.example` 仅提供演示值。 |
| NFR-SEC-05 | 安全 | 开发模拟接口默认关闭，仅演示环境开启。 |
| NFR-A11Y-01 | 可访问性 | 主要流程可键盘完成；焦点可见；图标按钮有可理解标签。 |
| NFR-A11Y-02 | 可访问性 | 上传控件有 label；状态变化使用 `aria-live`；对话框管理焦点。 |
| NFR-A11Y-03 | 可访问性 | 深色主题文本与背景对比度满足基本可读性。 |
| NFR-MAINT-01 | 可维护性 | 后端分层清晰：controller / service / mapper / pipeline / config。 |
| NFR-MAINT-02 | 可维护性 | 前端业务状态逻辑与展示组件分离，便于单测。 |
| NFR-MAINT-03 | 可维护性 | 数据库变更通过迁移脚本管理，不手工改表。 |
| NFR-OBS-01 | 可观测性 | 日志包含 `taskId`、`stage`、`status`、错误码。 |
| NFR-OBS-02 | 可观测性 | 提供健康检查接口。 |
| NFR-COMPAT-01 | 兼容性 | 支持现代 Chromium/Firefox/Safari 最近两个大版本。 |
| NFR-COMPAT-02 | 兼容性 | Node.js 22、Java 21、MySQL 8.4、Docker Compose v2。 |
| NFR-LICENSE-01 | 合规 | 示例图片、字体、图标必须有明确来源或为自生成；README 列出来源与许可。 |
| NFR-CI-01 | CI | push 和 pull_request 触发。 |
| NFR-CI-02 | CI | 前端执行 lint、TypeScript 类型检查、测试、构建。 |
| NFR-CI-03 | CI | 后端执行测试和构建。 |
| NFR-CI-04 | CI | 单测 job 不依赖真实 MySQL；后端单测使用 fake/mock。 |
| NFR-CI-05 | CI | 从干净 checkout 可重复通过。 |
| NFR-CI-06 | CI | 独立 `migration-smoke` job 使用 MySQL 服务容器执行 Flyway 迁移与种子导入，并对认领 SQL 做冒烟；该 job 为必过项，防止迁移脚本或种子数据带病交付。 |
| NFR-DOC-01 | 文档 | README 覆盖 R-13 全部条目。 |
| NFR-DOC-02 | 文档 | 提供 R-01～R-14 需求追踪表。 |
| NFR-DEPLOY-01 | 部署 | （推荐，P1）`docker compose up --build` 可启动前端、后端、MySQL 并完成初始化；未提供 Compose 时退化为 NFR-DEPLOY-04，不单独判未通过。 |
| NFR-DEPLOY-02 | 部署 | 提供健康检查、依赖顺序、数据卷和非敏感配置示例。 |
| NFR-DEPLOY-03 | 部署 | README 提供部署与回滚步骤。 |
| NFR-DEPLOY-04 | 部署 | 无 Compose 时，README 必须给出等价的构建、启动、配置、初始化步骤，可在容器环境中复现同等结果。 |

---

## 8. 测试需求

### 8.1 测试策略

| 层级 | 范围 | 工具 | 是否进 CI |
|---|---|---|---|
| 后端单元测试 | 状态机、流水线顺序、失败中断、校验、查询只读、终态幂等、归属 | JUnit 5、Mockito、AssertJ | 是 |
| 前端单元测试 | 任务 store、轮询启停、查询异常、晚到响应、用户切换、上传校验 | Vitest、Testing Library | 是 |
| 契约/集成测试 | 可选；不要求启动 MySQL | 可选 | 可选 |
| 手工/演示验收 | Compose 全链路、失败注入、刷新恢复、多用户 | 浏览器 + curl | 人工 |

测试要求：

- 测试必须确定性，不使用真实 sleep、随机数或真实网络。
- 后端流水线测试注入 fake repository、fake clock/sleeper、fake result composer。
- 前端测试使用 fake timers 和 mocked fetch。
- 测试命名或注释需关联 TC 编号，便于验收追溯。
- 不强制覆盖率数值，但第 8.2、8.3 的 P0 用例必须存在。

### 8.2 后端测试用例

| 编号 | 场景 | 前置/步骤 | 预期 |
|---|---|---|---|
| TC-BE-01 | 正常流水线顺序 | 无失败注入，执行完整流水线 | 阶段事件顺序为 `MATERIAL_CHECK → GENERATING → ASSEMBLING → DONE`；终态 `SUCCEEDED`；`resultUrl` 非空 |
| TC-BE-02 | `MATERIAL_CHECK` 失败中断 | 注入素材检查失败 | 状态 `FAILED`；`failedStage=MATERIAL_CHECK`；`GENERATING`、`ASSEMBLING` 未执行 |
| TC-BE-03 | `GENERATING` 失败中断 | 注入生成失败 | 状态 `FAILED`；`failedStage=GENERATING`；`ASSEMBLING` 未执行 |
| TC-BE-04 | `ASSEMBLING` 失败 | 注入结果整理失败 | 状态 `FAILED`；`failedStage=ASSEMBLING`；无 `TASK_SUCCEEDED` 事件 |
| TC-BE-05 | 失败原因持久化 | 注入失败后查询任务 | `errorCode`、`errorMessage` 非空且可读 |
| TC-BE-06 | 终态幂等 | 对 `SUCCEEDED`/`FAILED` 任务再次调用处理 | 状态、阶段、结果不变；无新事件 |
| TC-BE-07 | 阶段先提交后等待 | 使用 fake sleeper，在等待时读取 repository | 能读到当前阶段；阶段更新已提交 |
| TC-BE-08 | 查询只读 | 调用任务查询/历史查询 | repository 写操作次数为 0；`version` 不变 |
| TC-BE-09 | 素材数量校验 | 提交数量少于/多于要求 | 400 `MATERIAL_COUNT_MISMATCH`；不创建任务 |
| TC-BE-10 | MIME 校验 | 上传/提交不允许的类型 | 415 或 400；不创建任务 |
| TC-BE-11 | 大小校验 | 文件超过模板上限 | 413；不创建任务 |
| TC-BE-12 | 素材归属校验 | 用户 B 引用用户 A 的素材 | 404；不创建任务；响应体不含素材信息 |
| TC-BE-13 | 模板不存在 | 提交不存在的模板 id | 404 `TEMPLATE_NOT_FOUND`；不创建任务 |
| TC-BE-14 | 参数校验 | `params` 含未知字段或超长值 | 400 `INVALID_PARAMS`；字段错误可读 |
| TC-BE-15 | 原子认领 | 两个调度器并发认领同一 `PENDING` 任务 | 只有一个成功；不产生重复阶段 |
| TC-BE-16 | 异常兜底 | 阶段执行抛出未捕获异常 | 任务变为 `FAILED`，`INTERNAL_ERROR`，不残留 `PROCESSING` |
| TC-BE-17 | 成功结果文件 | 流水线成功 | 结果文件存在、可读、尺寸记录正确 |
| TC-BE-18 | 多任务隔离 | 同时处理两个任务，一个失败一个成功 | 状态、阶段、错误、结果互不影响 |
| TC-BE-19 | 历史排序与分页 | 创建多个任务后分页查询 | 按 `createdAt` 倒序；`total` 正确 |
| TC-BE-20 | 用户隔离 | 用户 B 查询用户 A 的任务/结果 | 404；不返回任何 A 的数据 |
| TC-BE-21 | 重启对账 | 模拟存在 `PROCESSING` 残留任务，启动应用 | 按设计标记 `FAILED`（`SERVER_RESTART`）或安全重排；不永久卡住 |
| TC-BE-22 | 等待任务可恢复 | 创建 `PENDING` 后重启调度器 | 任务被重新拾起并处理 |

### 8.3 前端测试用例

| 编号 | 场景 | 步骤 | 预期 |
|---|---|---|---|
| TC-FE-01 | 多任务隔离 | 交替 dispatch 任务 A、B 的状态更新 | A、B 各自保留自己的 status/stage/error/result |
| TC-FE-02 | 查询异常不等于任务失败 | 任务为 `PROCESSING`，查询返回 500/网络错误 | `sync.state=error`；`status` 仍为 `PROCESSING`；保留阶段 |
| TC-FE-03 | 任务失败与查询异常区分 | 分别渲染 `FAILED` 与 `sync.error` | 失败显示失败阶段/原因；同步错误显示重试；样式和文案不同 |
| TC-FE-04 | 终态停止跟踪 | 任务更新为 `SUCCEEDED`/`FAILED` | 从活跃集合移除；不再发起查询 |
| TC-FE-05 | 无活跃任务不轮询 | 所有任务终态或列表为空 | 不安排下一次定时器；fetch 调用次数不增加 |
| TC-FE-06 | 晚到响应丢弃 | 先应用 `version=5`，再到达 `version=4` 的响应 | 状态保持 version 5 |
| TC-FE-07 | 用户切换清理 | 切换用户/登出 | store 清空；旧请求被 abort；不出现旧任务 |
| TC-FE-08 | 刷新恢复 | 初始加载返回两条非终态任务 | store 恢复；轮询启动；终态任务不跟踪 |
| TC-FE-09 | 提交成功状态切换 | 提交请求返回 201 | `submitting=false`；任务立即插入；开始跟踪 |
| TC-FE-10 | 防重复提交 | 提交中再次点击 | 只发起一次请求 |
| TC-FE-11 | 上传客户端校验 | 数量不符、类型不符、超大小 | 对应槽位/表单显示错误；不发起上传 |
| TC-FE-12 | 上传失败重试 | 上传接口返回 500 | 槽位显示失败；点击重试重新上传；其他槽位不受影响 |
| TC-FE-13 | 卸载清理 | 轮询中卸载组件 | 清除定时器；abort 请求；无卸载后更新 |
| TC-FE-14 | 结果图加载失败 | 结果 URL 404 | 显示占位；任务仍为 `SUCCEEDED` |
| TC-FE-15 | 列表异常状态 | 模板接口失败/空数据 | 显示错误态/空态和重试入口 |
| TC-FE-16 | 404 模板 | 详情接口返回 404 | 显示不存在提示和返回列表入口 |

### 8.4 CI 要求

工作流文件：`.github/workflows/ci.yml`。

触发：`push`、`pull_request`。

前端 job：

1. `actions/checkout`
2. `actions/setup-node`，Node 22，启用 npm 缓存
3. `npm ci`
4. `npm run lint`
5. `npm run typecheck`（`tsc --noEmit`）
6. `npm test`（Vitest，运行一次）
7. `npm run build`

后端 job：

1. `actions/checkout`
2. `actions/setup-java`，Java 21，启用 Maven 缓存
3. `./mvnw -B verify`（Windows 本地可用 `mvnw.cmd`）

要求：

- 单测 job 不启动 MySQL 服务；后端单测不依赖数据库。
- 增加独立 `migration-smoke` job：以 `mysql:8.4` service 容器执行 Flyway 迁移与种子导入，并对认领 SQL（条件更新 + 受影响行数）做一次冒烟；该 job 为必过项。
- 前端 build 不请求 Java 后端；动态数据使用 Client Component 或强制动态渲染。
- 工作流从干净 checkout 可重复通过。
- 允许分别缓存 npm 和 Maven 依赖。

### 8.5 手工/演示测试

| 编号 | 场景 | 步骤 | 预期 |
|---|---|---|---|
| TC-MAN-01 | 干净启动 | 清空容器与卷后 `docker compose up --build` | 三个服务健康；前端可访问；种子模板可见 |
| TC-MAN-02 | 成功闭环 | 登录、选模板、上传 N 张图、提交 | 立即返回任务；阶段依次变化；最终显示结果图 |
| TC-MAN-03 | 阶段可观察 | 将生成耗时调至 8 秒后提交 | 查询/UI 能看到 `MATERIAL_CHECK → GENERATING → ASSEMBLING` |
| TC-MAN-04 | 指定阶段失败 | 注入 `failStage=GENERATING, failNext=1`，提交 | 任务在 `GENERATING` 失败；无 `ASSEMBLING`；原因可见 |
| TC-MAN-05 | 耗时调整 | 通过开发接口/环境变量调整耗时 | 处理时长变化；无需改代码 |
| TC-MAN-06 | 多任务 | 连续创建 3 个长任务（串行默认；可选并发度 2） | 串行时依次处理；并发度 2 时 2 个处理中、1 个等待中；状态互不串扰 |
| TC-MAN-07 | 刷新恢复 | 长任务处理中刷新页面 | 历史恢复；任务继续更新 |
| TC-MAN-08 | 关闭页面不中断 | 提交长任务后关闭标签页，等待后重新打开 | 任务已完成或仍在处理；结果可见 |
| TC-MAN-09 | 查询异常 | `docker compose stop backend`，观察前端 | 显示同步错误；任务不变成失败；重启后恢复 |
| TC-MAN-10 | 多用户隔离 | demo 创建任务，登出，登录 alice | alice 看不到 demo 的任务；直接访问 demo 任务 URL 返回 404 |
| TC-MAN-11 | 小屏可用 | 375 px 宽度完成上传、提交、查看历史 | 无横向滚动；主要操作可完成 |
| TC-MAN-12 | 键盘/可访问性 | 仅用键盘完成登录、上传、提交；检查图标按钮标签 | 焦点可见；控件可操作；标签可读 |
| TC-MAN-13 | 空历史 | 新用户登录查看历史 | 显示空状态文案，不报错 |
| TC-MAN-14 | 上传失败 | 上传超大/伪造类型文件 | 前端提示或服务端 413/415；表单保留 |
| TC-MAN-15 | 重启对账 | 处理中重启后端 | 残留任务按设计变为失败（原因可读）或安全重排；不永久卡住 |

---

## 9. 验收标准

### 9.1 验收环境

- 干净克隆或解压源码，不依赖宿主机已安装 Node/Java/MySQL。
- 首选 `docker compose up --build` 启动；若 Compose 不可用，按 README 的构建、启动、配置、初始化说明在容器环境中复核，不单独判此条未通过。
- 浏览器访问 `http://localhost:3000`。
- 使用 README 中的演示账号登录。
- 验收人不在宿主机直接执行项目脚本；运行、测试均在容器隔离环境完成。

### 9.2 验收方式

1. 阅读 README、源码入口、依赖脚本、迁移脚本、CI 配置。
2. 执行 Compose 启动，按 9.4 演示脚本逐项操作。
3. 执行 CI 等价命令或查看 CI 结果。
4. 对照 9.3 的验收清单逐项记录通过/未通过/未验证。
5. 对无法运行验证的部分标记“未验证”，不直接判定为未实现。

### 9.3 功能验收清单

| 编号 | 对应 | 验收内容 | 通过标准 |
|---|---|---|---|
| AC-01 | R-01 | 技术栈与职责 | 前端 Next.js App Router + React + TS；后端 Java + Spring Boot + MyBatis + MySQL；模板/素材/任务/记录由 Java 提供；任务与历史在 MySQL |
| AC-02 | R-02 | 模板列表与模拟数据 | 分类、卡片、封面、标题、筛选、详情跳转均可用；数据来自 Java；至少 3 分类、6 模板、素材数量 1～3 |
| AC-03 | R-03 | 详情与上传 | 按数量渲染上传位；预览/替换/移除可用；上传中/失败可见；前后端均校验数量/类型/大小；素材落盘可用 |
| AC-04 | R-04 | 受理与异步 | 提交立即返回 taskId；关闭页面任务继续；多任务可排队/并行 |
| AC-05 | R-05 | 多步骤状态 | 三步顺序正确；阶段可见且持久化；失败/结果持久化；查询只读；终态不回退 |
| AC-06 | R-06 | 失败与耗时控制 | 可重复指定阶段失败；失败后停止后续；原因可读；耗时可通过配置/开发接口调整；README 有操作说明 |
| AC-07 | R-07 | 状态表达与请求异常 | 上传中/提交中/等待/处理/成功/失败可区分；查询异常不标记任务失败；保留数据并提供重试 |
| AC-08 | R-08 | 多任务与生命周期 | 多任务互不串扰；自动跟踪未完成任务；终态停止；无任务不轮询；离开/切换用户清理请求 |
| AC-09 | R-09 | 刷新恢复与历史 | 历史含创建时间/状态/结果或失败原因；刷新恢复并继续跟踪；成功结果可预览 |
| AC-10 | R-10 | 身份与个人记录 | 身份方案可演示；用户只能看到自己的素材/任务/结果；README 说明设计 |
| AC-11 | R-11 | 视觉与可用性 | 三页布局清晰；状态反馈完整；语义化控件；键盘焦点；上传/图标按钮标签；小屏可操作 |
| AC-12 | R-12 | CI 与关键测试 | GHA 在 push/PR 执行前端 lint/typecheck/test/build 和后端 test/build；核心测试覆盖步骤顺序、失败中断、多任务隔离、查询异常与任务失败区分 |
| AC-13 | R-13 | 提交材料与说明 | README 覆盖版本、结构、命令、配置、初始化、接口、状态机、演示、身份、完成度、限制、投入、部署回滚、AI 使用、第三方来源 |
| AC-14 | R-14 | 容器交付（推荐项） | 首选 `docker compose up --build` 启动前端/后端/MySQL 并初始化数据；若 Compose 不可用，按 README 的等价步骤可复核运行，同样视为满足；Dockerfile 与非敏感配置示例为加分项 |

### 9.4 演示脚本

#### 9.4.1 启动

```bash
docker compose up --build -d
docker compose ps
```

预期：`mysql`、`backend`、`frontend` 均为 healthy/running；前端可访问。

#### 9.4.2 成功闭环

1. 打开 `http://localhost:3000`，使用 `demo / demo1234` 登录。
2. 选择“旅行明信片”模板。
3. 上传 2 张图片，观察预览、替换、移除、上传中状态。
4. 点击提交。
5. 观察任务立即出现在历史区，状态从 `PENDING` 变为 `PROCESSING`，阶段依次为 `MATERIAL_CHECK → GENERATING → ASSEMBLING`。
6. 成功后点击结果图预览。

#### 9.4.3 阶段耗时调整

```bash
curl -s -X POST http://localhost:8080/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"delayGeneratingMs": 8000}'
```

提交新任务，观察生成阶段停留约 8 秒。

#### 9.4.4 指定阶段失败

```bash
curl -s -X POST http://localhost:8080/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStage": "GENERATING", "failNext": 1}'
```

提交新任务，预期：

- 任务在 `GENERATING` 阶段变为 `FAILED`。
- `failedStage=GENERATING`，原因可读。
- 不出现 `ASSEMBLING` 阶段。
- 后续任务不受影响。

#### 9.4.5 多任务与排队

1. 将生成耗时设为 8000 ms。
2. 连续创建 3 个任务。
3. 串行配置（默认）预期：任务依次进入 `PROCESSING`，其余保持 `PENDING`，状态互不串扰。
4. 可选：将后端并发度设为 2 后重复，预期 2 个 `PROCESSING`、1 个 `PENDING`。

#### 9.4.6 刷新恢复与关闭页面

1. 提交长任务后刷新页面。
2. 预期：历史恢复，任务继续显示阶段变化。
3. 再提交长任务后关闭标签页，等待后重新打开。
4. 预期：任务已完成或仍在处理，结果可查看。

#### 9.4.7 查询异常与任务失败的区分

```bash
docker compose stop backend
```

预期：

- 前端任务卡片显示“状态刷新失败，重试”。
- 任务不会变成 `FAILED`。
- 已成功的结果图仍显示。

```bash
docker compose start backend
```

预期：查询恢复，任务继续更新或保持终态。

#### 9.4.8 用户隔离

1. 使用 demo 创建任务。
2. 登出，使用 alice 登录。
3. 预期：看不到 demo 的任务。
4. 直接访问 demo 任务详情/结果 URL。
5. 预期：404。

#### 9.4.9 小屏与键盘

1. 将浏览器宽度设为 375 px。
2. 完成上传、提交、查看历史。
3. 仅用 Tab/Enter/Space 完成登录和上传。
4. 预期：无横向滚动；焦点可见；上传控件可操作。

### 9.5 通过标准（Definition of Done）

必须同时满足：

1. AC-01～AC-13 全部通过；AC-14 为推荐项：提供 Compose 则须通过，未提供则须满足 NFR-DEPLOY-04 的等价说明。
2. CI 在默认分支的 push 和 PR 上均为绿（含 `migration-smoke`）。
3. README 覆盖 R-13 全部条目，演示命令可直接复制执行。
4. `docker compose up --build` 在干净环境可启动且种子数据可用；若 Compose 不可用，README 的降级启动说明必须能复现同等结果。
5. 核心测试（TC-BE-01～TC-BE-08、TC-FE-01～TC-FE-10）存在且通过。
6. 无 P0 级缺陷：查询推进状态、查询异常被当作任务失败、任务串扰、终态回退、已提供 Compose 但无法启动。
7. 已知限制（重启恢复、单实例、无幂等、无删除/重做等）在 README 中如实说明。
8. 交付物包含源码、锁文件、迁移脚本、种子数据、CI、README；提供 Compose 时含 Compose 与 Dockerfile。
9. 无真实凭据、无付费素材、无外网运行时依赖。

### 9.6 未通过判定

出现以下任一情况，视为对应验收项未通过：

- 查询接口会推进任务阶段或状态。
- 前端把查询失败显示为任务失败，或清空已有任务信息。
- 多任务状态下发生串扰。
- 阶段推进仅由前端定时器驱动。
- 任务状态或历史只存在内存/浏览器中。
- 非本人可读取他人任务或结果。
- 已提供 Compose 却无法在干净环境启动，或运行依赖宿主机预装服务，且 README 无可用降级说明。
- CI 失败或缺少前端 lint/typecheck/build、后端 test/build。
- README 缺少 R-13 的关键条目且无法通过源码解释。

---

## 10. 交付物清单

### 10.1 源码与配置

- [ ] `frontend/` 完整源码、`package.json`、`package-lock.json`
- [ ] `backend/` 完整源码、`pom.xml`、Maven Wrapper
- [ ] `backend/src/main/resources/db/migration/` 建表与种子 SQL
- [ ] `frontend/public/demo/` 或后端静态目录中的示例封面
- [ ] `.github/workflows/ci.yml`
- [ ] `docker-compose.yml`
- [ ] `frontend/Dockerfile`、`backend/Dockerfile`
- [ ] `.dockerignore`、`.gitignore`
- [ ] `.env.example`（仅演示值）
- [ ] `README.md`
- [ ] `docs/requirements.md`（本文档）
- [ ] 验收 commit 或 tag，并在 README 注明
- [ ] 源码压缩包（如走压缩包交付）不包含 `node_modules/`、`target/`、`.next/`、`dist/` 等构建缓存与依赖目录
- [ ] 通过原沟通渠道提交仓库链接或压缩包，并在 README 记录双方确认的提交日期
- [ ] 若使用非默认 Compose 文件名（如 `compose.demo.yml`），README 给出等价命令：`docker compose -f compose.demo.yml up --build -d`

### 10.2 README 必须包含

1. 项目简介与截图（可选）。
2. 技术版本：Next.js、React、TypeScript、Node、Java、Spring Boot、MyBatis、MySQL、Flyway、Redux Toolkit、Vitest、JUnit。
3. 项目结构说明。
4. 构建、测试、启动命令。
5. 前后端与数据库连接方式。
6. 配置项表及无真实凭据示例。
7. 数据库初始化、迁移、种子数据和素材准备方式。
8. 主要接口及请求/响应字段。
9. 任务状态、阶段、后台执行方式。
10. 成功、指定阶段失败、调整耗时、多任务、刷新恢复的演示步骤。
11. 用户身份与个人记录设计及演示方法。
12. 已完成/未完成项、已知限制、实际投入与取舍。
13. 重启恢复边界说明。
14. 部署与回滚基本步骤。
15. AI 使用说明与具体工作流示例。
16. 第三方脚手架、开源代码、素材来源及复用范围。

### 10.3 实际投入记录模板

| 日期 | 阶段 | 内容 | 实际工时 |
|---|---|---|---|
| 2026-10-28 | 需求分析 | 需求拆解、架构与风险分析 | 待填 |
| — | 开发 | 后端/前端/容器 | 待填 |
| — | 验证 | 单测、CI、端到端演示 | 待填 |
| — | 交付整理 | README、演示脚本、压缩包 | 待填 |

### 10.4 取舍记录模板

| 决策 | 选择 | 原因 | 未选择方案 |
|---|---|---|---|
| 状态更新方式 | 轮询 | 实现和清理简单，满足题目 | SSE/WebSocket |
| 异步执行 | DB 队列 + 进程内 worker | 可见等待态、易恢复、无额外中间件 | MQ、Redis |
| 重启恢复 | 启动对账 | 题目不要求断点续跑 | checkpoint |
| 身份 | 种子账号 + 会话 Cookie | 可演示隔离，成本低 | OAuth、完整 Spring Security |
| 结果生成 | Java2D 模拟拼图 | 可预览、非真实 AI | 直接复制原图 |
| 阶段可观察证据 | 阶段字段 + `task_events` 事件表 | 事件表便于验收追溯阶段顺序，代价是一张表和每次阶段写入 | 只用阶段字段 |
| 并行度 | 串行队列为默认，线程池大小可配 | 题目不要求真正并行；可配置便于演示多任务 | 固定高并发 |
| 幂等 | 前端防重 + 可选扩展 | 非必需 | 服务端幂等键 |

---

## 11. 风险、假设与限制

### 11.1 假设

- 单实例部署；不实现分布式调度。
- 浏览器可访问前端端口；前后端通过同源代理通信。
- 验收环境可拉取 Docker 镜像和依赖。
- 示例账号仅用于演示，不用于生产。
- MySQL 数据卷和上传数据卷会被保留；`down -v` 会清空。

### 11.2 已知限制

| 限制 | 影响 | 处理 |
|---|---|---|
| 服务重启时处理中的任务会中断 | 任务可能变为 `FAILED`（`SERVER_RESTART`） | 启动对账 + README 说明；不实现断点续跑 |
| 单实例 | 多实例会重复消费 | 明确假设；不实现分布式锁 |
| 轮询延迟 | 状态最多延迟一个轮询周期 | 间隔 1～2 秒；README 说明 |
| 无服务端幂等 | 极端重复请求可能创建多个任务 | 前端禁用；可选 `Idempotency-Key` |
| 无对象存储 | 文件依赖本地卷 | Compose 挂载数据卷；README 说明 |
| 无病毒扫描/图片净化 | 仅演示用途 | 限制类型和大小；README 说明 |
| 无速率限制 | 演示环境可被高频请求 | 非生产；README 说明 |
| 未使用 CSRF Token | 依赖 SameSite=Lax 和同源代理 | 演示级安全；README 说明 |
| 无任务删除/重做/下载 | 超出题目范围 | 不实现 |
| 无国际化 | 单一语言 | README 说明 |
| 未使用真实 AI | 结果为模拟拼图 | README 说明 |

### 11.3 主要风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 查询接口意外推进状态 | 中 | 高 | 查询层只读；测试 TC-BE-08 |
| 阶段更新未提交导致不可观察 | 中 | 高 | 每阶段独立提交；测试 TC-BE-07 |
| 前端把查询错误当任务失败 | 中 | 高 | 独立 `sync` 状态；测试 TC-FE-02/03 |
| 多任务状态串扰 | 中 | 高 | 按 id 索引 + version；测试 TC-FE-01/06 |
| Compose 启动顺序/初始化失败 | 中 | 高 | 健康检查、数据卷、README 重置说明；TC-MAN-01 |
| CI 因后端/DB 依赖失败 | 中 | 中 | 前端不在 build 时请求后端；后端单测不连库 |
| 上传文件在容器重启后丢失 | 中 | 中 | 上传目录挂数据卷 |
| 结果/封面 URL 使用内部地址 | 中 | 中 | 统一相对路径 + 同源代理 |
| 版本不兼容 | 低 | 中 | 开工前空项目验证并锁定版本 |
| 素材版权问题 | 低 | 中 | 使用自生成或 CC0 素材并注明来源 |

---

## 12. 需求追踪矩阵

| 题目 | 需求 ID | 测试 ID | 验收 ID |
|---|---|---|---|
| R-01 技术栈与职责 | FR-TECH-01～06 | TC-BE-*、TC-FE-*、CI | AC-01 |
| R-02 列表与模拟数据 | FR-TPL-01～10 | TC-FE-15、TC-MAN-01/02/13 | AC-02 |
| R-03 详情、上传与校验 | FR-MAT-01～15、FR-TASK-01/05/06 | TC-BE-09～14、TC-FE-11/12/16、TC-MAN-14 | AC-03 |
| R-04 受理与异步执行 | FR-TASK-03/04、FR-PIPE-09/10/12 | TC-BE-15/16/22、TC-MAN-06/08 | AC-04 |
| R-05 多步骤状态与查询 | FR-PIPE-01～08、FR-QUERY-01～08 | TC-BE-01～08/17/18、TC-FE-04/06 | AC-05 |
| R-06 失败与模拟控制 | FR-SIM-01～09 | TC-BE-02～05、TC-MAN-03/04/05 | AC-06 |
| R-07 状态表达与请求异常 | FR-FE-01/08、FR-ERR-01～08 | TC-FE-02/03、TC-MAN-09 | AC-07 |
| R-08 多任务与查询生命周期 | FR-FE-02～07/10/12 | TC-FE-01/04/05/06/07/13、TC-MAN-06 | AC-08 |
| R-09 刷新恢复与结果记录 | FR-QUERY-02/04、FR-FE-06、FR-FE-11 | TC-FE-08/14、TC-MAN-07/08/13 | AC-09 |
| R-10 用户身份与个人记录 | FR-AUTH-01～08 | TC-BE-12/20、TC-FE-07/10、TC-MAN-10 | AC-10 |
| R-11 参考界面与交互 | FR-UI-01～11、NFR-A11Y-* | TC-MAN-11/12 | AC-11 |
| R-12 CI 与关键测试 | NFR-CI-01～05、第 8 章 | TC-BE-*、TC-FE-* | AC-12 |
| R-13 提交材料与说明 | NFR-DOC-01/02、第 10 章 | 文档走查 | AC-13 |
| R-14 容器交付 | NFR-DEPLOY-01～04 | TC-MAN-01 | AC-14 |

> 条件项：R-11 的 UI-01～03、R-14 的 Compose 按 0.4 的约定处理；未提供截图或未使用 Compose 时，不单独判未通过，但必须提供等价说明。

---

## 13. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| v1.0 | 2026-10-28 | 初稿：需求、状态机、数据模型、接口、测试、验收 | — |
| v1.1 | 2026-10-28 | 依题目对齐评审修正：R-14 改为推荐项并补降级路径；UI-01～03 改为条件项；补交付渠道/提交日期；CI 增迁移冒烟；并发降为 P1；403/404 统一为 404；模拟配置单一来源；状态管理选型与可验证判据补全 | — |

---

## 附录 A：需求完成状态模板

实现完成后，在 README 或本附录中填写：

| 模块 | 状态 | 说明 |
|---|---|---|
| 模板浏览 | 未开始/进行中/完成 | |
| 素材上传 | 未开始/进行中/完成 | |
| 任务受理与异步处理 | 未开始/进行中/完成 | |
| 失败/耗时模拟 | 未开始/进行中/完成 | |
| 前端状态与轮询 | 未开始/进行中/完成 | |
| 历史与刷新恢复 | 未开始/进行中/完成 | |
| 身份与隔离 | 未开始/进行中/完成 | |
| 测试与 CI | 未开始/进行中/完成 | |
| Compose 交付 | 未开始/进行中/完成 | |
| README 与演示 | 未开始/进行中/完成 | |

## 附录 B：AI 使用说明模板

- 使用的工具/模型：
- 任务拆解方式：
- 提供给 AI 的上下文（需求、schema、接口契约、约束）：
- AI 生成/修改的范围：
- 人工验证方式（单测、CI、容器演示）：
- 发现并修正的问题（举一个具体例子）：
- 未采纳的 AI 建议及原因：