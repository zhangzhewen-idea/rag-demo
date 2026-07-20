# RAG Demo 后端

## 技术栈

Java 25、Spring Boot 4.0.7、Spring AI 2.0.0、Spring Security、MyBatis / MyBatis-Plus、Flyway、MySQL 8、Redis Stack、Apache Tika 3。

代码使用 COLA light 单模块分层：

```text
adapter -> application -> domain <- infrastructure
```

`ArchitectureTest` 会阻止 `domain`、`application`、`adapter` 直接依赖不允许的实现层。

## 必要环境变量

| 变量 | 说明 | 开发默认值 |
|---|---|---|
| `DB_URL` | MySQL JDBC 地址 | `jdbc:mysql://localhost:3308/rag_demo...` |
| `DB_USERNAME` | MySQL 用户 | `root` |
| `DB_PASSWORD` | MySQL 密码 | 空 |
| `REDIS_HOST` / `REDIS_PORT` | Redis Stack | `localhost` / `6380` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `JWT_SECRET` | 至少 32 字节的 HMAC 密钥 | 仅有不安全开发占位值 |
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key | 无有效默认值 |
| `DASHSCOPE_BASE_URL` | 百炼 OpenAI 兼容服务根地址 | `https://dashscope.aliyuncs.com/compatible-mode` |
| `RAG_STORAGE_ROOT` | 文件上传根目录 | `/Users/zhangzhewen/data/ragdemo/uploads` |

机密必须通过环境变量注入，不要提交 `.env`、Token、证书或真实密钥。

## 启动与验证

先确认 `JAVA_HOME` 指向 JDK 25。若机器安装了多个 JDK，可按本机路径设置，例如：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

```bash
./mvnw -version
./mvnw test
./mvnw spring-boot:run
```

当前工程使用 `maven.compiler.release=25`。更高版本 JDK 可用于兼容编译验证，但正式开发和验收应使用设计指定的 JDK 25。

首次启动时 Flyway 创建 7 张业务表，并初始化两个角色、两个测试用户和两条知识库数据。Redis Vector Store 使用索引 `rag-demo-index`、Key 前缀 `rag:chunk:`、HNSW/COSINE 和 2560 维向量。

上传文件保存到 `{RAG_STORAGE_ROOT}/{knowledgeBaseId}/{uuid}.{ext}`。支持 `txt`、`md`、`pdf`、`doc`、`docx`，单文件不超过 20 MB。

## 模型与检索

- Chat：`qwen3.7-plus`。
- Embedding：`qwen3.7-text-embedding`，固定 2560 维。
- 默认 Top-K：6，相似度阈值：0.60。
- 无可靠证据固定回答：`当前知识库中未找到可靠依据`。

模型或维度改变后必须删除并重建整个 `rag-demo-index`，不能混用不同维度。

## 密码风险

测试密码 `123456` 的无盐 MD5 是 `e10adc3949ba59abbe56e057f20f883e`。这是需求兼容实现，不是生产安全实践；生产环境应迁移到带工作因子的现代密码哈希。
