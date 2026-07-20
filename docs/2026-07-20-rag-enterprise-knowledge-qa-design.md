# 张喆闻 RAG 企业知识库问答系统设计

## 1. 背景与目标

在当前仓库中新增一个前后端分离的教学型 RAG 企业内部知识库问答系统，项目名称固定为“张喆闻 RAG企业知识库问答系统”。系统应形成完整业务闭环，同时控制复杂度，适合学习 Spring AI 2.0、COLA light、文档向量化、Redis Stack 检索和 Vue 3 管理后台。

项目对应现有的两个一级目录：

- `rag-demo-backend`：Java 25、Spring Boot 4、Spring AI 2.0 后端。
- `rag-demo-frontend`：Vue 3、TypeScript 前端。

## 2. 范围

### 2.1 功能范围

- 用户登录、Token 刷新、退出登录和角色鉴权。
- 管理员用户管理、知识库管理、文档管理和数据看板。
- 普通用户浏览已启用知识库、创建会话、进行 RAG 问答并查看本人历史会话。
- 支持 `.txt`、`.md`、`.pdf`、`.doc`、`.docx` 文件上传、解析、切片、向量化、失败重试和删除。
- 每个知识库可包含多个文档，每个会话只绑定一个知识库，并检索该知识库内所有 `READY` 文档。
- 基于阿里云百炼 `qwen3.7-plus` 流式生成答案，使用 `qwen3.7-text-embedding` 生成 2560 维向量。
- 回答提供来源引用；无可靠证据时明确拒答。

### 2.2 非目标

- 不实现知识库类别。
- 不实现多知识库或类别范围检索。
- 不实现多租户、细粒度文档授权、审批流、消息队列、重排序模型或多 Agent 协作。
- 不实现自主规划或工具调用；“Agent”限定为多轮会话、RAG 检索、流式回答和来源引用。
- 不提供前后端容器镜像或 `docker-compose.yml`，直接连接现有 MySQL 与 Redis Stack。
- 不专门设计移动端管理后台。

## 3. 技术基线

### 3.1 后端

- Java 25。
- Spring Boot 4.0.x。
- Spring AI 2.0.0。
- Spring Security、MyBatis、MyBatis-Plus、Flyway。
- MySQL 8，地址 `localhost:3308`，数据库名 `rag_demo`。
- Redis Stack，地址 `localhost:6380`，开发环境无密码。
- Apache Tika 3.x。
- Maven Wrapper。

Spring AI 2.0 通过官方 OpenAI 模型抽象连接阿里云百炼 OpenAI 兼容接口，不引入当前仅兼容 Spring AI 1.1.x 的 Spring AI Alibaba Starter。

### 3.2 前端

- Vue 3、TypeScript、Vite。
- Pinia、Vue Router、Element Plus。
- Axios 负责普通 REST 请求；浏览器原生流式能力或可取消的 Fetch 负责 SSE 问答。
- Apache ECharts 负责管理员首页统计图表。
- Vitest 负责单元与组件测试。

## 4. 总体架构

系统采用前后端分离的模块化单体。后端保持单 Maven Module，通过 COLA light package 分层约束依赖方向：

```text
adapter -> application -> domain <- infrastructure
```

建议基础包名为 `com.zhangzhewen.ragdemo`，目录结构如下：

```text
rag-demo-backend/src/main/java/com/zhangzhewen/ragdemo
├── RagDemoApplication.java
├── adapter
│   ├── web
│   └── security
├── application
│   ├── auth
│   ├── user
│   ├── knowledge
│   ├── conversation
│   ├── dashboard
│   └── dto
├── domain
│   ├── identity
│   ├── knowledge
│   ├── conversation
│   └── gateway
└── infrastructure
    ├── persistence
    ├── redis
    ├── storage
    ├── document
    ├── ai
    └── config
```

分层职责：

- `adapter`：REST、SSE、请求绑定、Spring Security 边界，不编写业务规则或直接使用 Mapper。
- `application`：登录、上传、异步入库、问答、删除和看板等用例编排及事务边界。
- `domain`：用户、角色、知识库、文档、会话、消息、状态转换、策略和 Gateway 接口。
- `infrastructure`：MyBatis、MyBatis-Plus、Redis、文件系统、解析器和模型客户端等 Gateway 实现。

使用 ArchUnit 保护依赖方向，禁止 `domain` 依赖其他三层，禁止 `application` 和 `adapter` 直接依赖 `infrastructure`。

## 5. 领域模型与数据设计

### 5.1 MySQL 表

所有业务表使用 `BIGINT` 主键，并包含 `created_at`、`updated_at`。需要逻辑删除的表增加 `deleted` 字段，由 MyBatis-Plus 管理。

#### `sys_user`

- `id`、`username`、`password`、`nickname`、`avatar_url`。
- `status`：`ENABLED` 或 `DISABLED`。
- `last_login_at`、`created_at`、`updated_at`、`deleted`。
- `username` 建立唯一索引。

#### `sys_role`

- `id`、`role_code`、`role_name`、`created_at`、`updated_at`。
- 固定角色为 `ADMIN` 和 `USER`，`role_code` 建立唯一索引。

#### `sys_user_role`

- `id`、`user_id`、`role_id`、`created_at`。
- 对 `user_id + role_id` 建立唯一索引。

#### `kb_knowledge_base`

- `id`、`name`、`description`、`cover_url`。
- `status`：`ENABLED` 或 `DISABLED`。
- `created_by`、`created_at`、`updated_at`、`deleted`。
- 不设置类别字段；一个知识库直接包含多个文档。

#### `kb_document`

- `id`、`knowledge_base_id`、`original_name`、`stored_name`、`storage_path`。
- `extension`、`mime_type`、`file_size`、`content_hash`。
- `status`：`PENDING`、`PROCESSING`、`READY`、`FAILED`、`DELETING`。
- `chunk_count`、`retry_count`、`failure_stage`、`failure_reason`。
- `created_by`、`created_at`、`updated_at`、`deleted`。
- 对 `knowledge_base_id`、`status` 和 `content_hash` 建立查询索引。

#### `ai_conversation`

- `id`、`user_id`、`knowledge_base_id`、`title`、`status`。
- `created_at`、`updated_at`、`deleted`。
- 会话创建后不可切换知识库。

#### `ai_message`

- `id`、`conversation_id`、`role`、`content`。
- `prompt_tokens`、`completion_tokens`、`elapsed_ms`。
- `status`：`COMPLETED`、`FAILED` 或 `CANCELLED`。
- `created_at`。

#### `ai_message_reference`

- `id`、`message_id`、`knowledge_base_id`、`document_id`。
- `source_name`、`chunk_index`、`similarity_score`、`excerpt`。
- `page_number`、`section_title`、`created_at`。
- 页码或 Markdown 标题不可获得时存 `NULL`，禁止伪造定位信息。

### 5.2 Flyway

- `V1__init_schema.sql` 创建表、索引、唯一约束和必要外键。
- `V2__init_test_data.sql` 初始化固定角色、测试用户、两个知识库、对话和看板可复现数据。
- 测试账号为 `admin` 和 `user`，两者初始密码均为 `123456`。
- 原始文档和 Redis 向量不在 SQL 中伪造，由真实上传和解析流程生成。

## 6. 存储边界

### 6.1 MySQL

MySQL 保存用户、角色、知识库、文档任务、会话、消息、引用和统计所需的可审计业务事实，不保存文件 BLOB 和向量数组。

### 6.2 Redis Stack

Redis Stack 同时承担以下职责：

- Spring AI `RedisVectorStore` 的文档切片与向量索引。
- Refresh Token、JWT 退出失效标记、登录失败计数和轻量缓存。

向量索引建议使用名称 `rag-demo-index` 和前缀 `rag:chunk:`，使用 HNSW、COSINE 距离和固定 `DIM = 2560`。每个切片至少保存以下元数据：

- `knowledgeBaseId`：TAG，用于会话级精确过滤。
- `documentId`：TAG，用于删除、重试和重建。
- `chunkIndex`：NUMERIC。
- `pageNumber`：NUMERIC，可空。
- `sectionTitle`、`sourceName`：TEXT。

Redis 认证相关 Key 使用 `rag:auth:` 前缀，登录失败计数使用 `rag:login:fail:` 前缀，避免与向量数据混用命名空间。

### 6.3 文件系统

所有上传文件统一保存到：

```text
/Users/zhangzhewen/data/ragdemo/uploads/{knowledgeBaseId}/{uuid}.{ext}
```

数据库保存原始文件名、实际存储名和绝对路径。服务启动时验证根目录可读写；上传时解析规范化路径并确认其仍在根目录内，防止路径穿越。

## 7. 文档入库设计

### 7.1 上传校验

- 单文件上限 20 MB。
- 支持 `.txt`、`.md`、`.pdf`、`.doc`、`.docx`。
- 同时校验扩展名、MIME 和文件魔数，不能只信任浏览器传入的类型。
- 使用 UUID 作为存储文件名，原始名称只作为展示元数据。

### 7.2 解析器分工

- `.md` 使用 `org.springframework.ai:spring-ai-markdown-document-reader` 中的 `MarkdownDocumentReader`，保留可用于引用的标题信息。
- `.txt`、`.pdf`、`.doc`、`.docx` 使用 Apache Tika 3.x。
- Tika 能解析 OLE2 `.doc` 和 OOXML `.docx`。PDF 仅在解析器能可靠提供页信息时记录页码，否则引用不显示页码。

### 7.3 异步状态流转

上传接口完成安全校验和落盘后，插入 `PENDING` 文档并立即返回。Spring `@Async` 使用独立线程池处理后续流程，教学默认值为核心线程 2、最大线程 4、队列 100。

```text
PENDING -> PROCESSING -> READY
                     -> FAILED
READY/FAILED -> DELETING -> 逻辑删除
```

任务通过文档状态和条件更新实现幂等，同一文档不能被并发重复处理。失败时保存阶段、简要原因和重试次数，并按 `documentId` 删除本次已写入的向量，原文件保留供管理员重试。

### 7.4 切片与向量化

- 基础切片使用 Spring AI `TokenTextSplitter`，目标大小 800 tokens。
- 通过轻量重叠转换保留相邻切片约 100 tokens 的上下文。
- `qwen3.7-text-embedding` 固定输出 2560 维 dense 向量。
- 单次向量化批次最多 20 条。
- 模型或维度变化时必须删除并重建整个 Redis 向量索引，禁止在同一索引混用不同维度。

## 8. RAG 问答设计

### 8.1 会话规则

- 一个会话绑定一个知识库，创建后不可切换。
- 普通用户只能操作自己的会话；管理员可使用用户工作台，但不直接查看其他用户的私有会话内容。
- 每次问答加载最近 10 轮消息作为短期上下文，完整历史保存在 MySQL。

### 8.2 检索

- 先校验会话归属和知识库启用状态。
- 通过 `knowledgeBaseId` 精确过滤 Redis Vector Store。
- 默认 `Top-K = 6`，相似度阈值为 `0.60`，两者均通过配置文件暴露。
- 应用层显式调用检索 Gateway 并保留命中的 Spring AI `Document`，不把检索结果完全隐藏在 Advisor 内，以便生成和保存引用。

### 8.3 提示词与证据约束

- 系统提示词明确要求只基于检索证据回答。
- 检索内容放入带边界的证据区，并要求模型将其视为资料而不是指令，降低文档提示注入风险。
- 无结果或最高相似度低于阈值时，不调用模型自由补充知识，直接回答“当前知识库中未找到可靠依据”。
- 有冲突证据时保留来源差异，不自行选择未经证明的结论。

### 8.4 SSE 协议

聊天接口使用 SSE 返回以下事件：

- `delta`：增量答案文本。
- `references`：最终引用列表。
- `done`：Token 用量、耗时和消息 ID。
- `error`：稳定错误码、用户可读提示和 `traceId`。

开始向客户端发送 `delta` 后不自动重试整次模型请求，避免重复输出。流完成后保存回答和引用；客户端取消时保存 `CANCELLED` 状态或不完整消息状态，不将其计为成功回答。

## 9. 模型接入

使用 Spring AI 2.0 的 OpenAI 兼容模型实现连接阿里云百炼：

- Chat Model：`qwen3.7-plus`。
- Embedding Model：`qwen3.7-text-embedding`。
- Embedding 维度：2560。
- API Key：仅从 `DASHSCOPE_API_KEY` 环境变量读取。
- Base URL：通过环境变量配置阿里云百炼对应地域的 `/compatible-mode/v1` 地址。

百炼超时、限流或首次连接失败时最多重试 3 次并指数退避；SSE 已输出内容后不重试。模型调用的超时、重试次数和检索参数均应配置化。

## 10. 认证与安全

### 10.1 角色权限

- `ADMIN`：用户、知识库、文档和看板管理，同时可使用普通用户问答页面。
- `USER`：浏览已启用知识库、创建问答会话和查看本人历史。
- `/admin/**` 仅允许 `ADMIN`，业务方法增加方法级鉴权作为第二道边界。

### 10.2 Token

- Access Token 默认 30 分钟，通过 `Authorization: Bearer` 发送。
- Refresh Token 默认 7 天，服务端状态存 Redis，浏览器通过 `HttpOnly` Cookie 携带。
- Access Token 保存在 Pinia 内存中；页面刷新后通过 Refresh Token 恢复登录态。
- 退出登录删除 Refresh Token 并使当前 Access Token 的 `jti` 在剩余有效期内失效。
- 连续登录失败 5 次后锁定 15 分钟，计数保存在 Redis。

### 10.3 密码硬约束与风险

按明确需求，所有环境包括生产均使用无盐 MD5。`123456` 的固定小写十六进制值为：

```text
e10adc3949ba59abbe56e057f20f883e
```

后端提供自定义 Spring Security `PasswordEncoder` 完成兼容。该要求存在已知生产安全风险：MD5 是快速、无工作因子的旧散列算法，不能有效抵抗预计算和离线撞库。代码与文档必须如实标注风险，不得将其描述为生产安全实践。

### 10.4 机密与日志

- 数据库密码、Redis 密码、JWT 密钥和百炼 API Key 只从环境变量读取。
- 仓库不提交真实密钥或包含凭据的 `.env`。
- 日志记录 `traceId`、用户 ID、文档 ID、模型耗时和任务阶段，不记录密码、Token、API Key 或完整私有文档内容。

## 11. API 设计

普通 REST 响应统一使用：

```json
{
  "code": "0",
  "message": "success",
  "data": {},
  "traceId": "..."
}
```

主要接口分组：

- `/api/auth/login`、`/refresh`、`/logout`、`/me`。
- `/api/knowledge-bases`：普通用户查询已启用知识库。
- `/api/conversations`：创建、列表、详情、重命名和删除本人会话。
- `/api/conversations/{id}/messages/stream`：SSE 问答。
- `/api/admin/dashboard`：指标卡和图表聚合数据。
- `/api/admin/users`：用户新增、编辑、启停、分配角色和重置密码。
- `/api/admin/knowledge-bases`：知识库增删改、启停和封面管理。
- `/api/admin/knowledge-bases/{id}/documents`：上传和列表。
- `/api/admin/documents/{id}`：详情、状态、重试和删除。

业务错误由全局异常处理器映射到稳定错误码和 HTTP 状态。SSE 使用独立 `error` 事件，不在已建立的流中返回普通 JSON 包装。

## 12. 前端设计

### 12.1 视觉方向

采用“智能紫渐变”：紫蓝渐变背景、半透明卡片、圆润组件和适量光影。正文与功能性文字统一使用黑色，保证对比度；避免大面积深色背景和低对比度灰字。

桌面端 1280px 及以上优先，兼容平板，不专门设计移动端管理后台。

### 12.2 路由与页面

公开页面：

- `/login`：项目名称、账号密码登录和演示账号提示。

普通用户工作台：

- `/home`：欢迎信息、可用知识库、最近会话和快捷入口。
- `/knowledge-bases`：已启用知识库卡片和创建会话入口。
- `/chat/:conversationId`：会话侧栏、流式回答、停止生成、引用角标和来源抽屉。
- `/conversations`：本人历史会话查询、重命名和删除。
- `/profile`：个人基本资料；MVP 不提供密码修改。

管理员后台：

- `/admin/dashboard`：数据看板。
- `/admin/knowledge-bases`：知识库新增、编辑、启停、封面和文档入口。
- `/admin/documents`：上传、状态进度、失败原因、重试和删除。
- `/admin/users`：新增、角色、启停和重置密码。

管理员同时拥有普通用户工作台路由权限。

### 12.3 管理首页

- 知识库数、文档数、用户数、累计问答数四个指标卡。
- 近 7 日问答趋势折线图。
- 文件类型分布环形图。
- 热门知识库 Top 5 柱状图。
- 所有数据来自真实 SQL 聚合，不生成随机统计数。

### 12.4 交互状态

- 页面提供骨架屏、空状态和明确错误提示。
- 文档列表展示上传、处理中、成功、失败和删除中状态。
- 失败任务显示可读原因和重试按钮。
- 聊天页面支持停止生成、复制答案、展开引用和继续追问。

## 13. 删除与一致性

删除文档时依次执行：

1. 将 MySQL 状态条件更新为 `DELETING`，阻止重复删除或重新向量化。
2. 按 `documentId` 删除 Redis 切片。
3. 删除物理文件。
4. 将 MySQL 文档记录逻辑删除。

任何步骤失败都保留 `DELETING` 或 `FAILED` 状态与失败阶段，允许管理员重试。重新向量化先生成新一批向量，成功后替换旧向量，避免处理失败导致知识库完全不可用。

删除知识库前必须确认其不存在处理中任务；删除操作级联处理其文档向量和物理文件，但数据库业务记录采用逻辑删除保留审计线索。

## 14. 注释与文档规范

- 后端所有类和方法必须编写中文 JavaDoc，说明职责、参数、返回值和关键异常。
- 复杂状态流转、补偿逻辑、向量过滤和安全边界增加中文行内注释。
- 前端组件、Store、组合式函数和导出的 API 方法使用中文注释说明职责。
- 注释解释“为什么”和约束，不机械复述代码。
- 项目 README、启动说明和配置说明以中文为主，技术标识、命令和路径保留英文。

## 15. 测试策略

### 15.1 后端

- Domain 单测：用户状态、角色权限、知识库启停和文档状态流转。
- Application 测试：使用 Fake 或 Mock Gateway 验证登录、上传、重试、删除、问答和看板编排。
- Infrastructure 集成测试：MyBatis 映射、Flyway、Redis 2560 维索引、文件解析和向量删除。
- Adapter 测试：MockMvc 参数校验、JWT 权限、统一响应和管理员接口隔离。
- Architecture 测试：ArchUnit 保护 COLA 依赖方向。
- 百炼在线测试使用独立 `live` 标签且默认禁用，常规测试使用模拟模型响应。

基础设施集成测试使用隔离的测试数据和独立 Key 前缀，禁止清理或污染现有开发服务数据。若使用 Testcontainers，仅用于测试生命周期，不改变项目运行时直接连接现有服务的设计。

### 15.2 前端

- Vitest 覆盖 Pinia 登录态、Token 刷新、路由守卫、SSE 事件解析和引用转换。
- 核心页面执行组件测试。
- 登录、上传、问答和权限隔离各保留一条端到端冒烟路径。
- TypeScript 类型检查和 Vite 生产构建必须通过。

## 16. 验收标准

- 项目可使用 Java 25 启动，并连接 `localhost:3308/rag_demo` 与 `localhost:6380`。
- Flyway 可在空库完整建表并写入可复现测试数据。
- `admin` 和 `user` 可使用密码 `123456` 登录，普通用户访问管理员接口返回 `403`。
- 管理员能完成用户、知识库和多个文档的管理。
- 五个受支持扩展名均能上传，任务状态能进入 `READY` 或显示明确失败原因并重试。
- Redis 索引固定为 2560 维，并能按会话绑定的 `knowledgeBaseId` 检索全部就绪文档。
- 问答能够流式展示、停止生成、保存消息并显示可靠来源；无证据时固定拒答。
- 管理首页四个指标和三张图表来自真实数据库聚合。
- 后端测试、前端测试、类型检查和生产构建全部通过。

## 17. 官方参考

- [Spring AI 2.0 Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI RedisVectorStore API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/vectorstore/redis/RedisVectorStore.html)
- [Spring AI MarkdownDocumentReader API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/reader/markdown/MarkdownDocumentReader.html)
- [阿里云百炼文本向量同步 API](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api/)
- [Apache Tika 支持格式](https://tika.apache.org/3.1.0/formats.html)
- [Spring Security 密码存储](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html)
