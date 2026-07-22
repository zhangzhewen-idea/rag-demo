# 项目上下文

## 项目目标

`rag-demo` 是前后端分离的教学型企业知识库 RAG 系统。普通用户可登录、浏览启用的知识库、创建会话、进行带引用的
流式问答并管理个人资料；管理员可管理用户、知识库和文档，预览切片、重试入库任务，并维护版本化评估集、运行生产
RAG 链路和人工复核结果。

## 技术栈

### 后端

- 目录：`rag-demo-backend/`
- Java 25，单 Maven module，使用 Maven Wrapper。
- Spring Boot 4.0.7、Spring AI 2.0.0、Spring Security。
- MyBatis / MyBatis-Plus、Flyway、MySQL 8。
- Redis Stack 同时承载中文 BM25/向量索引和认证状态。
- Apache Tika 解析文档；上传文件写入受控本地目录。
- ArchUnit 保护 COLA light package 依赖方向。

### 前端

- 目录：`rag-demo-frontend/`
- Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus。
- Vitest 和 Vue Test Utils 负责单元与组件测试。
- Axios 处理普通 REST 请求，可取消的 Fetch 处理 SSE 流式问答。
- 页面覆盖登录、首页、知识库、会话问答、历史会话、个人资料，以及管理员看板、用户、知识库、文档和 RAG 评估。

## 当前核心能力

- 身份认证：JWT Access Token、`HttpOnly` Refresh Cookie、Redis 刷新令牌轮换/黑名单/登录失败状态，管理员接口按角色隔离。
- 文档入库：上传前校验扩展名、MIME、大小和切片预览指纹；后台解析、分批 Embedding、CAS 状态迁移和失败补偿。
- 证据检索：追问上下文处理、查询改写、查询扩展、BM25 与向量混合召回、两级 RRF、可回退重排。
- 上下文管理：滚动摘要、近期对话窗口、Token 预算和多来源证据优先，证据不足时固定拒答。
- 质量评估：版本化评估集异步回放生产链路，分别统计候选召回、最终上下文、生成质量、拒答、延迟和 Token。

## 主要目录

```text
rag-demo/
├── rag-demo-backend/
│   └── src/
│       ├── main/java/com/zhangzhewen/ragdemo/
│       │   ├── adapter/
│       │   ├── application/
│       │   ├── domain/
│       │   └── infrastructure/
│       ├── main/resources/
│       └── test/
├── rag-demo-frontend/
│   └── src/{api,layouts,router,stores,views}/
├── docs/
└── .harness/
```

## 外部依赖

- MySQL 8：保存用户、知识库、文档任务、会话、消息、引用和统计事实。
- Redis Stack：保存 `rag-demo-index-v2` 的 2560 维向量与中文 BM25 索引，以及刷新令牌、黑名单和登录失败计数。
- 本地文件系统：保存上传原文档、头像和知识库封面。
- 阿里云百炼：OpenAI 兼容 Chat 使用 `deepseek-v4-flash`，Embedding 使用 `qwen3.7-text-embedding`，文档重排使用
  `qwen3-rerank`；评估 Judge 默认复用 `deepseek-v4-flash`。

外部服务地址和凭据由环境变量或本地运行环境提供。会调用真实模型、产生费用或影响共享数据的验证不属于默认测试。

## 事实来源与优先级

按具体问题使用最接近执行面的来源：

1. 编译、测试和运行行为：当前源码、`pom.xml`、`package.json`、锁文件与实际命令输出。
2. 运行时配置：`rag-demo-backend/src/main/resources/application.yml` 和环境变量覆盖规则。
3. 架构边界：`ArchitectureTest` 是可执行事实，`architecture.md` 必须与其一致。
4. 数据结构：Flyway migration 与持久化 Gateway/Mapper；已执行 migration 不因文档冲突被改写。
5. 已批准业务目标：`docs/2026-07-20-rag-enterprise-knowledge-qa-design.md`。
6. 项目使用说明：根目录和前端 `README.md`。

发现来源冲突时不得静默选择：以可执行实现描述当前状态，同时指出设计或文档差异。当前
`application.yml` 以 `DASHSCOPE_API_KEY` 为主并兼容回退到 `OPENAPI_KEY`。根 README 与实际配置仍存在默认
数据库/Redis 地址、百炼 Base URL 和最终 `top-k` 的表述差异；未经单独任务不得顺手改配置或运行环境。
