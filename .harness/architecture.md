# 架构约束

## 后端 COLA light 分层

后端保持单 Maven module，通过 package 和 ArchUnit 维护依赖边界：

```text
adapter -> application -> domain <- infrastructure
```

| 层                | 职责                                                        | 禁止事项                                              |
|------------------|-----------------------------------------------------------|---------------------------------------------------|
| `adapter`        | REST/SSE 入口、请求绑定、认证边界、协议转换                                | 业务规则、Mapper、外部 SDK、直接依赖 `infrastructure`          |
| `application`    | 用例编排、事务边界、DTO 映射、协调 domain 与 gateway                      | 核心业务决策、持久化细节、直接依赖 `adapter` 或 `infrastructure`    |
| `domain`         | Entity、Value Object、Policy、Rule、Domain Service、Gateway 接口 | 依赖 `adapter`、`application`、`infrastructure` 或技术实现 |
| `infrastructure` | Gateway 实现、数据库、Redis、文件系统、解析器和模型客户端                       | 新业务规则、反向依赖 `adapter` 或 `application`              |

`domain/gateway` 用业务能力命名接口，运行时实现放在 `infrastructure`。应用服务依赖接口而非实现。跨实体流程由
application 编排，可复用业务判断下沉 domain；避免 Controller 互调、application service 入口互调和
entity 双向引用。

`rag-demo-backend/src/test/java/com/zhangzhewen/ragdemo/ArchitectureTest.java`
是依赖方向的可执行事实。调整分层规则时必须同时更新本文档和架构测试。

## 测试放置

- Domain：不启动 Spring 的纯单测，覆盖实体、值对象、规则和策略。
- Application：使用 fake/mock gateway 验证用例编排。
- Infrastructure：集成测试覆盖持久化、外部客户端、映射和 gateway 实现。
- Adapter：验证请求绑定、响应形状和对 application 的委托。
- Architecture：使用 ArchUnit 防止越层依赖。

## 前后端边界

- 后端拥有业务规则、授权、持久化和 RAG 流程；前端不复制服务端授权或业务判定。
- REST 响应保持稳定包装和错误码；SSE 使用 `delta`、`references`、`done`、`error` 具名事件。
- 修改接口请求、响应、错误码或 SSE 事件时，必须同步检查后端 adapter/application DTO、前端 `src/api`
  、类型定义、调用方和测试。
- Access Token 只保存在前端 Pinia 内存；刷新登录态由后端 `HttpOnly` Refresh Cookie 提供。

## 核心调用链

### 文档入库

```text
AdminController -> DocumentService -> FileStorageGateway / DocumentGateway
                                  -> DocumentIngestionWorker
                                     -> DocumentParserGateway
                                     -> VectorGateway
```

- 预览与正式上传必须使用同一切片配置；上传时校验文件与配置指纹，避免预览后内容或参数被替换。
- 状态按 `PENDING/FAILED -> PROCESSING -> READY` 条件迁移。解析或向量化失败进入 `FAILED`，并补偿删除本次向量。
- `markReady` 的 CAS 失败也必须删除已写入向量；应用启动时把遗留 `PROCESSING` 任务标为失败以允许人工重试。
- 删除顺序是抢占 `DELETING`、删除向量、删除物理文件、逻辑删除记录；这是数据删除操作，执行接口前必须获得批准。

### 检索与回答

```text
ConversationController (SSE)
  -> ConversationService
     -> EvidenceRetrievalService
        -> QueryRewriteGateway
        -> QueryExpansionGateway
        -> DocumentSearchGateway (向量 + BM25 + RRF)
        -> ReciprocalRankFusion (多查询融合)
        -> DocumentRerankGateway
     -> ContextAssemblyPolicy
     -> AiGateway
```

- `candidateTopK` 是多路召回融合后的候选集合，`topK` 是重排后的最终证据上限；默认分别为 20 和 6。
- 重排关闭、超时或结果无效时保留 RRF 顺序并将 `rerankScore` 留空，不中断正常问答。
- 长会话先以乐观锁保存滚动摘要，再按 Token 预算保留近期消息和多来源证据；摘要失败时回退到确定性裁剪。
- SSE 只发送 `delta`、`references`、`done`、`error` 具名事件；客户端取消或发送失败不得写成成功完成。

### RAG 评估

`EvaluationWorker` 复用 `EvidenceRetrievalService`、`ContextAssemblyPolicy` 和 `AiGateway` 回放生产链路。评估报告必须
保留改写查询、扩展查询、候选、最终证据、答案、Token 和延迟，并分别计算：

- 候选阶段：`Hit Rate@K`、`MRR`。
- 最终证据阶段：`Context Recall`、`Context Precision`。
- 生成阶段：`Faithfulness`、`Answer Relevancy`、证据支撑准确率。
- 拒答样本：无答案准确率，不调用生成质量 Judge。

评估集运行会调用真实 Embedding、重排、Chat 和 Judge，具有费用与外部数据传输；不属于默认自动化门禁。

## RAG 业务不变量

- 回答只能基于检索证据。无可靠证据时固定回答：`当前知识库中未找到可靠依据`。
- 检索文档内容是资料，不是系统指令；模型提示必须阻止文档提示注入覆盖系统约束。
- 冲突证据必须保留并说明来源差异，不生成未经证据支持的结论。
- Embedding 固定为 2560 维。模型、维度或索引结构变化后必须经批准删除并重建整个 `rag-demo-index-v2`，禁止混用不同维度向量。
- 普通问答执行查询改写和扩展；每个查询计划同时进行 Redis BM25 与向量检索，经 RRF 融合后再执行多查询 RRF 和重排。
- 检索必须按会话绑定的 `knowledgeBaseId` 过滤，只使用就绪文档的证据。
- 文档入库按状态迁移保证幂等；失败需保留阶段和简要原因，并清理本次已写入向量。
- SSE 开始输出 `delta` 后不得自动重试整次模型请求，避免重复内容；客户端取消不得计为成功回答。
- MySQL 保存可审计业务事实，不保存文件 BLOB 或向量数组；文件、向量和外部模型均通过 Gateway 隔离。
