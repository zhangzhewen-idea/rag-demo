# 项目命令

所有命令默认从 `rag-demo` 仓库根目录执行。命令是当前项目认可的原始操作；是否必需由 `quality-gates.md`
按改动范围决定。

## 通用检查

```bash
git status --short
git diff --check
git diff
git diff --cached
```

仓库包含 `.codegraph/` 时，理解或定位 Java 调用链先使用：

```bash
codegraph explore "要查找的符号或问题"
```

## 后端

先确认 `JAVA_HOME` 指向 JDK 25：

```bash
cd rag-demo-backend
./mvnw -version
```

完整测试、打包和启动：

```bash
cd rag-demo-backend
./mvnw test
./mvnw package
./mvnw spring-boot:run
```

局部后端验证可指定单个或一组测试，但不能替代质量门禁要求的完整测试：

```bash
cd rag-demo-backend
./mvnw -Dtest=ArchitectureTest test
./mvnw -Dtest=RetrievalPolicyTest,ReciprocalRankFusionTest,ContextAssemblyPolicyTest test
./mvnw -Dtest=EvaluationMetricsTest,EvaluationPolicyTest test
```

有界启动检查应在依赖服务和环境变量准备完成、相关测试通过后执行。服务启动后从另一个终端检查：

```bash
curl --fail --max-time 5 http://localhost:8080/actuator/health
```

启动命令本身持续运行，不将其误报为卡死；检查完成后正常终止本地进程。启动可能连接 MySQL、Redis Stack
和百炼配置，执行前遵守安全边界。

## 前端

存在锁文件时，干净环境优先使用可复现安装；已有工作目录需要更新依赖时使用 `npm install`：

```bash
cd rag-demo-frontend
npm ci
npm install
```

测试、类型检查和构建：

```bash
cd rag-demo-frontend
npm test
npm run type-check
npm run build
npm run build:dev
npm run build:prod
```

本地启动与有界可达性检查：

```bash
cd rag-demo-frontend
npm run dev
```

开发服务器启动后从另一个终端检查：

```bash
curl --fail --max-time 5 --head http://localhost:5173
```

当前 `package.json` 没有 `lint` 脚本，不得虚构 `npm run lint` 门禁。`npm run build` 已包含
`vue-tsc -b`，但质量门禁仍单独运行 `npm run type-check`，使类型失败更清晰。

## 环境与 Docker 发布

后端默认使用 `dev` Profile；生产运行必须显式使用 `prod` Profile，并通过环境变量注入外部依赖和凭据。
前端 `npm run build` 等价于 `npm run build:prod`。

首次准备本机 Docker 发布配置：

```bash
cp deploy.env.example .env.docker
```

`.env.docker` 包含真实凭据，禁止提交、读取或输出。填写完成后，在仓库根目录执行一键发布：

```bash
./deploy.sh
```

宿主机默认端口冲突时，可在命令行覆盖端口；命令行值优先于 `.env.docker`：

```bash
FRONTEND_PORT=3001 BACKEND_PORT=8081 ./deploy.sh
```

发布资产的无副作用验证命令：

```bash
bash -n deploy.sh
RAG_ENV_FILE=deploy.env.example \
RAG_DEMO_JAR_FILE=target/rag-demo-backend-0.0.1-SNAPSHOT.jar \
docker compose --env-file deploy.env.example \
  --file compose.yaml --file compose.backend-build.yaml config
```

真实 `./deploy.sh` 会启动容器、连接外部 MySQL/Redis 并执行 Flyway，必须先获得用户明确批准；普通质量门禁只验证
脚本语法、Compose 展开和镜像构建，不执行 `compose up`。

## RAG 评估与在线模型调用

普通 `./mvnw test` 使用 fake/mock 或本地测试服务，不应触发真实百炼请求。不得默认启动管理员评估运行、手工调用
Chat/Embedding/Rerank/Judge，或删除重建 Redis 索引。获得用户明确批准后，单独执行并记录目标评估集、外部服务、
费用/数据影响和结果，不把在线调用混入普通单元测试。
