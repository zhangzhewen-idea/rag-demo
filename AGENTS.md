# Codex 项目入口

执行任何任务时，先读取根目录 `.claudeignore` 并确认目标路径未被忽略，再读取并遵循根目录 `CLAUDE.md`。

## 适配边界

- 本文件只维护 Codex 专属适配，不复制 `CLAUDE.md` 中的共用规则。
- 发生冲突时，只允许 Codex 的平台系统、安全约束和能力差异覆盖共用规则。
- Codex 使用当前环境支持的原生方式加载 skill，并按 skill 内的 Codex 工具映射执行。

## 忽略规则

本项目复用项目根目录 `.claudeignore` 作为 Codex 的忽略规则来源。

- Codex 在执行文件读取、搜索、分析或编辑前，必须先查看 `.claudeignore`。
- `.claudeignore` 中匹配的路径，不得主动读取、搜索、分析、修改或输出其内容。
- 如果用户明确要求处理被 `.claudeignore` 匹配的文件，先说明该路径被忽略规则覆盖，并等待用户确认。
- `.claudeignore` 是项目约定，不代表 Codex 原生支持；Codex 必须通过本文件人工遵守。
