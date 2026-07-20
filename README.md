# 张喆闻 RAG 企业知识库问答系统

这是一个前后端分离的教学型企业知识库 RAG 系统，完整实现登录鉴权、用户与知识库管理、文档异步入库、Redis Stack 向量检索、流式问答、来源引用和管理看板。

## 工程目录

- `rag-demo-backend`：Java 25、Spring Boot 4.0.7、Spring AI 2.0.0、COLA light。
- `rag-demo-frontend`：Vue 3、TypeScript、Vite、Element Plus、ECharts。
- `docs`：系统设计文档。

## 快速开始

1. 准备 MySQL 8：`localhost:3308/rag_demo`。
2. 准备 Redis Stack：`localhost:6380`，并确保 RediSearch 模块可用。
3. 设置 `DB_PASSWORD`、`JWT_SECRET`、`DASHSCOPE_API_KEY` 等环境变量。
4. 启动后端：`cd rag-demo-backend && ./mvnw spring-boot:run`。
5. 启动前端：`cd rag-demo-frontend && npm install && npm run dev`。

详细配置见 [后端说明](rag-demo-backend/README.md) 和 [前端说明](rag-demo-frontend/README.md)。

测试账号：`admin / 123456`、`user / 123456`。

> 安全警告：系统按明确需求使用无盐 MD5 保存密码，仅用于兼容和教学演示。MD5 无法有效抵抗预计算与离线撞库，生产项目必须改用 Argon2、bcrypt 或 scrypt，并强制用户重置密码。
