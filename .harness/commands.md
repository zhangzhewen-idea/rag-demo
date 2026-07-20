# 项目命令

所有命令默认从 `rag-demo` 仓库根目录执行。命令是当前项目认可的原始操作；是否必需由 `quality-gates.md` 按改动范围决定。

## 通用检查

```bash
git status --short
git diff --check
git diff
git diff --cached
```

## 后端

先确认 `JAVA_HOME` 指向 JDK 25：

```bash
cd rag-demo-backend
./mvnw -version
```

测试、构建和启动：

```bash
cd rag-demo-backend
./mvnw test
./mvnw package
./mvnw spring-boot:run
```

有界启动检查应在依赖服务和环境变量准备完成、相关测试通过后执行。服务启动后从另一个终端检查：

```bash
curl --fail --max-time 5 http://localhost:8080/actuator/health
```

启动命令本身持续运行，不将其误报为卡死；检查完成后正常终止本地进程。启动可能连接 MySQL、Redis Stack 和百炼配置，执行前遵守安全边界。

## 前端

首次安装依赖：

```bash
cd rag-demo-frontend
npm install
```

测试、类型检查和构建：

```bash
cd rag-demo-frontend
npm test
npm run type-check
npm run build
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

当前 `package.json` 没有 `lint` 脚本，不得虚构 `npm run lint` 门禁。`npm run build` 已包含 `vue-tsc -b`，但质量门禁仍单独运行 `npm run type-check`，使类型失败更清晰。

## 在线模型调用

默认不得执行会触发真实百炼请求的测试或手工调用。获得用户明确批准后，单独运行并记录命令、影响范围和结果，不把在线调用混入普通单元测试。
