# AI 开发 Harness

本目录是 `rag-demo` 的 AI 开发规则唯一核心来源。它约束 AI 如何理解、修改和验证项目，不负责进程守护、生产运维，也不保存任务日志或执行状态。

## 加载顺序

每个任务都按以下顺序加载上下文：

1. 从仓库根目录的工具入口进入：Codex 读取 `AGENTS.md`，Claude Code 读取 `CLAUDE.md`。
2. 阅读本文件。
3. 必读 [`constitution.md`](constitution.md) 和 [`security-boundaries.md`](security-boundaries.md)。
4. 按影响范围阅读 [`project-context.md`](project-context.md)、[`architecture.md`](architecture.md)、
   [`commands.md`](commands.md) 和 [`quality-gates.md`](quality-gates.md)。
5. 在 `feature`、`bugfix`、`refactor`、`review` 中选择一个主工作流并读取对应文档。

任务涉及代码、配置、命令、依赖、数据结构或项目结构时，应读取项目上下文、架构、命令和质量门禁。纯文档改动
可以只读取与文档事实相关的部分，但仍需遵守宪章、安全边界和文档门禁。涉及 RAG 检索、上下文、模型调用或
评估时，必须同时读取 `architecture.md` 中对应不变量。

## 文档导航

| 文档                                                 | 职责                       |
|----------------------------------------------------|--------------------------|
| [`constitution.md`](constitution.md)               | 自治原则、通用行为、Git 行为和完成定义    |
| [`project-context.md`](project-context.md)         | 当前项目总结、技术栈、能力、目录和事实来源    |
| [`architecture.md`](architecture.md)               | COLA 分层、核心链路、接口与 RAG 不变量   |
| [`commands.md`](commands.md)                       | 项目认可的安装、启动、测试和构建命令       |
| [`quality-gates.md`](quality-gates.md)             | 改动范围到验证项的映射和准入条件         |
| [`security-boundaries.md`](security-boundaries.md) | 密钥、数据、外部副作用和破坏性操作边界      |
| [`workflows/feature.md`](workflows/feature.md)     | 新功能工作流                   |
| [`workflows/bugfix.md`](workflows/bugfix.md)       | 缺陷修复工作流                  |
| [`workflows/refactor.md`](workflows/refactor.md)   | 重构工作流                    |
| [`workflows/review.md`](workflows/review.md)       | 代码审查工作流                  |

## 维护规则

- 自治原则变化只修改 `constitution.md`。
- 安全边界变化只修改 `security-boundaries.md`。
- 技术栈、目录或外部依赖变化修改 `project-context.md`。
- 分层或 RAG 不变量变化修改 `architecture.md`，并同步对应架构测试。
- 命令或脚本变化同步修改 `commands.md` 和 `quality-gates.md`。
- 检索阶段、模型、索引、Token 预算或评估指标变化时，同步核对 `project-context.md`、`architecture.md`、
  `commands.md` 和 `quality-gates.md`。
- 新工具只新增根目录薄适配入口，不复制核心政策。
- 不在 Harness 中记录单次任务计划、提示词、执行日志或任务归档。
- Harness 不维护手工版本号，变更由 Git 历史追踪。

## 迁移到其他项目

保留本目录的职责划分和工作流结构，替换项目上下文、架构约束、命令、质量门禁和安全边界。删除 `rag-demo`
专有事实前，先确认新项目已有相应事实来源；不要保留未填写占位符。
