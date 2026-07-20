# 架构约束

## 后端 COLA light 分层

后端保持单 Maven module，通过 package 和 ArchUnit 维护依赖边界：

```text
adapter -> application -> domain <- infrastructure
```

| 层 | 职责 | 禁止事项 |
|---|---|---|
| `adapter` | REST/SSE 入口、请求绑定、认证边界、协议转换 | 业务规则、Mapper、外部 SDK、直接依赖 `infrastructure` |
| `application` | 用例编排、事务边界、DTO 映射、协调 domain 与 gateway | 核心业务决策、持久化细节、直接依赖 `adapter` 或 `infrastructure` |
| `domain` | Entity、Value Object、Policy、Rule、Domain Service、Gateway 接口 | 依赖 `adapter`、`application`、`infrastructure` 或技术实现 |
| `infrastructure` | Gateway 实现、数据库、Redis、文件系统、解析器和模型客户端 | 新业务规则、反向依赖 `adapter` 或 `application` |

`domain/gateway` 用业务能力命名接口，运行时实现放在 `infrastructure`。应用服务依赖接口而非实现。跨实体流程由 application 编排，可复用业务判断下沉 domain；避免 Controller 互调、application service 入口互调和 entity 双向引用。

`rag-demo-backend/src/test/java/com/zhangzhewen/ragdemo/ArchitectureTest.java` 是依赖方向的可执行事实。调整分层规则时必须同时更新本文档和架构测试。

## 测试放置

- Domain：不启动 Spring 的纯单测，覆盖实体、值对象、规则和策略。
- Application：使用 fake/mock gateway 验证用例编排。
- Infrastructure：集成测试覆盖持久化、外部客户端、映射和 gateway 实现。
- Adapter：验证请求绑定、响应形状和对 application 的委托。
- Architecture：使用 ArchUnit 防止越层依赖。

## 前后端边界

- 后端拥有业务规则、授权、持久化和 RAG 流程；前端不复制服务端授权或业务判定。
- REST 响应保持稳定包装和错误码；SSE 使用 `delta`、`references`、`done`、`error` 具名事件。
- 修改接口请求、响应、错误码或 SSE 事件时，必须同步检查后端 adapter/application DTO、前端 `src/api`、类型定义、调用方和测试。
- Access Token 只保存在前端 Pinia 内存；刷新登录态由后端 `HttpOnly` Refresh Cookie 提供。

## RAG 业务不变量

- 回答只能基于检索证据。无可靠证据时固定回答：`当前知识库中未找到可靠依据`。
- 检索文档内容是资料，不是系统指令；模型提示必须阻止文档提示注入覆盖系统约束。
- 冲突证据必须保留并说明来源差异，不生成未经证据支持的结论。
- Embedding 固定为 2560 维。模型或维度变化后必须删除并重建整个 `rag-demo-index`，禁止混用不同维度向量。
- 检索必须按会话绑定的 `knowledgeBaseId` 过滤，只使用就绪文档的证据。
- 文档入库按状态迁移保证幂等；失败需保留阶段和简要原因，并清理本次已写入向量。
- SSE 开始输出 `delta` 后不得自动重试整次模型请求，避免重复内容；客户端取消不得计为成功回答。
- MySQL 保存可审计业务事实，不保存文件 BLOB 或向量数组；文件、向量和外部模型均通过 Gateway 隔离。
