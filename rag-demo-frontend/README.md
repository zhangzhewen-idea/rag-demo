# RAG Demo 前端

Vue 3 + TypeScript 管理与问答工作台。Access Token 仅保存在 Pinia 内存，刷新页面通过后端 `HttpOnly`
Refresh Cookie 恢复登录态。

## 命令

```bash
npm install
npm run dev
npm test
npm run type-check
npm run build
```

开发服务器默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。

页面覆盖登录、工作台、知识库、流式问答、历史会话、个人资料，以及管理员看板、知识库、文档和用户管理。聊天流使用可取消
`Fetch` 解析 `delta`、`references`、`done`、`error` 四类 SSE 事件。
