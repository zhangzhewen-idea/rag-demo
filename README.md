# RAG Demo

前后端分离的企业知识库 RAG 教学项目。后端位于 `rag-demo-backend/`，前端位于
`rag-demo-frontend/`。

## 开发与生产环境

后端默认启用 `dev` Profile，无需额外参数：

```bash
cd rag-demo-backend
./mvnw spring-boot:run
```

生产环境使用 `prod` Profile，数据库、Redis、JWT 和百炼凭据均必须通过环境变量注入：

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

前端提供独立的开发和生产模式命令：

```bash
cd rag-demo-frontend
npm run dev
npm run dev:prod
npm run build:dev
npm run build:prod
```

开发服务默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。
生产构建仍使用相对 `/api`，由部署环境的 Nginx 反向代理后端。

## Docker 一键发布

Docker Compose 只运行前端和后端，MySQL 与 Redis Stack 由外部环境提供。首次发布前准备本地配置：

```bash
cp deploy.env.example .env.docker
```

编辑 `.env.docker`，填写真实的 `DB_*`、`REDIS_*`、`JWT_SECRET`、`DASHSCOPE_API_KEY` 和
`RAG_DOCKER_DATA_ROOT`。该文件已被 Git 和项目读取规则忽略，不得提交。

完成一次性配置后，在仓库根目录执行：

```bash
./deploy.sh
```

默认 Compose 项目名为 `rag-demo`，也可显式指定：

```bash
./deploy.sh my-rag-demo
```

发布成功后，前端入口为 `http://localhost:3000`，后端健康检查为
`http://localhost:8080/actuator/health`。常用运维命令：

```bash
docker compose --project-name rag-demo --file compose.yaml logs --follow
docker compose --project-name rag-demo --file compose.yaml down
```

发布脚本会在宿主机通过 Maven Wrapper 打包后端，再构建前后端镜像、启动容器并执行有界健康检查。
脚本不会创建 MySQL、Redis Stack，也不会重建 Redis 索引。

## 技术栈

Java 25、Spring Boot 4.0.7、Spring AI 2.0.0、Spring Security、MyBatis / MyBatis-Plus、Flyway、MySQL
8、Redis Stack、Apache Tika 3。

代码使用 COLA light 单模块分层：

```text
adapter -> application -> domain <- infrastructure
```

`ArchitectureTest` 会阻止 `domain`、`application`、`adapter` 直接依赖不允许的实现层。

## 必要环境变量

| 变量                          | 说明                | 开发默认值                                            |
|-----------------------------|-------------------|--------------------------------------------------|
| `DB_URL`                    | MySQL JDBC 地址     | `jdbc:mysql://192.168.50.6:3306/rag_demo...`     |
| `DB_USERNAME`               | MySQL 用户          | `root`                                           |
| `DB_PASSWORD`               | MySQL 密码          | 空                                                |
| `REDIS_HOST` / `REDIS_PORT` | Redis Stack       | `192.168.50.6` / `6380`                          |
| `REDIS_PASSWORD`            | Redis 密码          | 空                                                |
| `JWT_SECRET`                | 至少 32 字节的 HMAC 密钥 | 仅有不安全开发占位值                                       |
| `DASHSCOPE_API_KEY`         | 阿里云百炼 API Key     | 无有效默认值                                           |
| `DASHSCOPE_BASE_URL`        | 百炼 OpenAI 兼容服务根地址 | `https://dashscope.aliyuncs.com/compatible-mode` |
| `DASHSCOPE_RERANK_BASE_URL` | 百炼重排接口地址         | `https://dashscope.aliyuncs.com/compatible-api/v1/reranks` |
| `RAG_RERANK_ENABLED`        | 是否启用候选结果重排       | `true`                                           |
| `RAG_RERANK_MODEL`          | 重排模型               | `qwen3-rerank`                                   |
| `RAG_RERANK_TIMEOUT`        | 重排请求超时             | `5s`                                             |
| `RAG_EVALUATION_JUDGE_MODEL` | 生成质量评估模型          | `deepseek-v4-flash`                              |
| `RAG_EVALUATION_PIPELINE_VERSION` | 检索与 Prompt 组合版本 | `v1`                                             |
| `AI_INTERACTION_LOG_LEVEL`  | ChatClient 交互日志级别 | `DEBUG`                                          |
| `RAG_STORAGE_ROOT`          | 文件上传根目录           | `/Users/zhangzhewen/data/ragdemo/uploads`        |

机密必须通过环境变量注入，不要提交 `.env`、Token、证书或真实密钥。

## 启动与验证

先确认 `JAVA_HOME` 指向 JDK 25。若机器安装了多个 JDK，可按本机路径设置，例如：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

```bash
cd rag-demo-backend
./mvnw -version
./mvnw test
./mvnw spring-boot:run
```

当前工程使用 `maven.compiler.release=25`。更高版本 JDK 可用于兼容编译验证，但正式开发和验收应使用设计指定的
JDK 25。

首次启动时 Flyway 创建业务表，并初始化两个角色、两个测试用户和两条知识库数据。Redis Vector Store
使用索引 `rag-demo-index-v2`、Key 前缀 `rag:chunk:`、HNSW/COSINE 和 2560 维向量。

上传文件保存到 `{RAG_STORAGE_ROOT}/{knowledgeBaseId}/{uuid}.{ext}`。支持 `txt`、`md`、`pdf`、`doc`、
`docx`，单文件不超过 20 MB。

## 模型与检索

- Chat：`deepseek-v4-flash`。
- Embedding：`qwen3.7-text-embedding`，固定 2560 维。
- 检索：Redis BM25 关键词检索与向量语义检索先通过 Reciprocal Rank Fusion 融合，再由
  `qwen3-rerank` 对候选切片二次打分。
- 默认召回 20 条候选、重排后输出 Top-10，向量相似度阈值为 0.2；可通过 `RAG_CANDIDATE_TOP_K`、
  `RAG_TOP_K`、`RAG_SIMILARITY_THRESHOLD` 调整。
- 重排超时、限流或响应无效时回退到 RRF 顺序，不中断问答。引用中的 `similarityScore` 保留初始检索分数，
  `rerankScore` 为本次候选集内的相对重排分数；未重排或回退时后者为 `null`。
- 无可靠证据固定回答：`当前知识库中未找到可靠依据`。

## 大模型交互日志

`SimpleLoggerAdvisor` 覆盖查询改写和最终流式回答，在 `DEBUG` 级别记录完整提示词、模型选项和响应。
请求日志会展开 `model`、`temperature`、`topP`、Token 上限、惩罚项和 reasoning 等非敏感参数，
不会序列化 `apiKey`、credential 或自定义 HTTP 请求头。
日志按现有配置写入 `log/main.log`。这些内容可能包含用户问题、历史对话和知识库切片，不需要调试时应将
`AI_INTERACTION_LOG_LEVEL` 设为 `INFO`。Advisor 不记录 API Key 或 HTTP Authorization 请求头。

模型或维度改变后必须删除并重建整个 `rag-demo-index-v2`，不能混用不同维度。

## RAG 评估

管理员可通过 `/api/admin/evaluations` 维护不可变的版本化评估集，并异步回放与在线问答相同的查询改写、
查询扩展、混合召回、RRF、重排、上下文组装和生成链路。非拒答样本使用 `sourceName + evidenceContains`
标注一条或多条黄金证据；`REFUSAL` 样本不配置黄金证据。支持的答案类型为 `FACTUAL`、`PROCEDURE`、
`COMPARISON`、`REFUSAL`、`SUMMARY`。

主要接口：

- `POST /api/admin/evaluations/datasets`：创建新版本评估集，单个版本最多 100 条样本。
- `GET /api/admin/evaluations/datasets`、`GET /api/admin/evaluations/datasets/{id}`：查询评估集。
- `POST /api/admin/evaluations/datasets/{id}/runs`：启动异步评估；同一评估集同一时间只允许一个运行。
- `GET /api/admin/evaluations/datasets/{id}/runs`、`GET /api/admin/evaluations/runs/{id}`：查询汇总和逐题轨迹。
- `PUT /api/admin/evaluations/results/{id}/review`：提交 `ACCURATE` 或 `INACCURATE` 人工复核结论与备注。

报告分别保存候选 `Hit Rate@K`、`MRR`，最终上下文 `Context Recall`、`Context Precision`，以及 Judge
给出的 `Faithfulness`、`Answer Relevancy`、证据支撑准确率；拒答样本单独计算无答案准确率。运行还记录
逐题与 P95 延迟、Prompt/Completion Token、完整检索轨迹和配置版本快照。默认通过规则为绝对门槛加
最近一次通过运行的基线比较，所有门槛均可通过 `RAG_EVALUATION_*` 环境变量调整。
