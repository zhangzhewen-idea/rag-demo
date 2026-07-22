# RAG Demo 前端

Vue 3 + TypeScript 管理与问答工作台。Access Token 仅保存在 Pinia 内存，刷新页面通过后端 `HttpOnly`
Refresh Cookie 恢复登录态。

## 命令

```bash
npm install
npm run dev
npm run dev:prod
npm test
npm run type-check
npm run build:dev
npm run build:prod
```

`npm run dev` 使用 `dev` 模式，默认运行在 `http://localhost:5173`，并将 `/api` 代理到
`http://localhost:8080`。`npm run dev:prod` 使用 `prod` 模式并监听 `3000` 端口，便于本地检查生产模式；
正式生产产物由 `npm run build:prod` 生成。

Docker 发布时，Nginx 托管生产静态资源，将 `/api` 反向代理到后端容器，并为聊天 SSE 关闭代理缓冲。
仓库根目录的 `./deploy.sh` 会统一构建并发布前后端。

页面覆盖登录、工作台、知识库、流式问答、历史会话、个人资料，以及管理员看板、知识库、文档和用户管理。聊天流使用可取消
`Fetch` 解析 `delta`、`references`、`done`、`error` 四类 SSE 事件。
