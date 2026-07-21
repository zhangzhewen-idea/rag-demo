# Claude Code 项目入口

Claude Code 开始任何任务前，先读取 [`.harness/README.md`](.harness/README.md)，并按其中的任务类型和加载顺序读取必要文档。

平台映射：

- 使用当前会话的任务列表维护执行步骤，不向仓库写入任务状态。
- 使用文件读取、搜索和 Bash 能力检查实现，并运行 Harness 指定的验证命令。
- 使用 Git 查看和审查改动，只按 Harness 规则处理当前任务文件。
- 将 `.claudeignore` 作为项目忽略清单；内置读取与搜索工具的限制由 `.claude/settings.json` 中的 `permissions.deny` 执行。两者的路径范围必须同步维护，不得通过 Bash 绕过限制。

`.harness/` 是项目规则的唯一核心来源。本文件只负责 Claude Code 的加载入口和能力映射；若本文件与 Harness 核心文档表述不一致，以 Harness 核心文档为准。
