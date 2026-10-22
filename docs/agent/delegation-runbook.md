# 委派操作手册（DSH 子智能体 / dsh_code_review）

> 适用：**DSH 会话**（Windows 原生）。2026-10-22 用户裁决起，派活**只用 DSH 自带子智能体**；Codex 侧 `~/.codex/mcp/delegation/server.mjs` 的 `delegate_task` 属另一个宿主，**DSH 不得再引用**（其日志语义见本文末尾〈历史〉）。

## 0. 工具选择（唯一通道）

| 场景 | 工具 | 关键约束 |
|---|---|---|
| 一次性、可独立验收的活 | `subagent` | **全新 context，看不到本会话** ⇒ brief 完全自包含；派发返回 `subagentId`，结果由子代理 `send_message` 回报，运行时另有完成通知 |
| 接续本会话上下文（续写 / 二次核查；**复核不算**，见下一行） | `subagent_fork` | 继承本会话**已完成回合**（不含当前进行中这一轮） |
| 多人并行 / 长期角色 | `spawn_teammate`（Team Lead 专属）+ `team_task_*` | `write_scopes` 必须互不重叠；`send_message` 改需求；`wait_agent` 长超时一次到位；`interrupt_agent` 止血 |
| 复核（二次验证） | `dsh_code_review({ input })` | **不是** subagent 派活；不解析 CLI 选项 |

**主 Agent 分工不变**：识别需求 → 分配工作 → 验收成果；Subagent 只负责干活与回报。

## 1. 四条收敛规则

1. **一次委派只做一件事，只给一个编译目标。** 一件事 = 一个可独立验收的改动面；一个编译目标 = 一条 `gradlew.bat …` 命令。
2. **一个工作树同一时间只跑一个 gradle。** DSH 没有服务端的 `DELEGATION_BUSY` 兜底 ⇒ 这条靠人守：同树并发编译会互相踩锁。
3. **一次只发一条，等回报再发下一条。** 不并发、不「先发后补」；子代理完成由**运行时通知**，**禁止**轮询（`list_agents` / `job_list` / 反复 `wait_agent`）。
4. **brief 里禁止同时要求「双线编译 + 部署整合包 + 变异用例 + commit」。** 这四件事各自独立成轮；混在一轮必然超时且无法归因。

## 2. 每轮标准流程

```
体检 → 派活 → 等回报 → 编译/守门 → 验收 → 提交
```

1. **体检**：`git -C F:\MCProject\astral_dice_multiloader status --short -uall` 必须空输出；有脏改动先处理，否则事后无法归因。
2. **派活**：一条 `subagent`（或 `subagent_fork`），brief 用第 4 节模板；显式写 `provider: "deepseek-official"` + `model: "deepseek-flash"`（其它 model 用 `list_subagent_models` 现查）。
3. **等回报**：子代理用 `send_message` 回结论；运行时也会通知完成。长时间无回报 ⇒ 按第 3 节处置。
4. **编译/守门**：核对它报的改动清单（`文件:行号`），再本地跑编译与 `git status` 确认无越界文件。
5. **提交**：一个改动面一个 commit；信息写清「做了什么 + 为什么」。

## 3. 中断后的处置

三查齐了才判定，顺序不可颠倒：

| 顺序 | 查什么 | 判据 | 结论 |
|---|---|---|---|
| 1 | 有无子代理回报（`send_message` / 完成通知） | 有且引用了产物路径 | 进第 2 查 |
| 2 | 它声称改的产物是否真的落盘（文件 mtime ≥ 派发时间、内容含声称的改动） | 存在且内容对得上 | 进第 3 查 |
| 3 | `git status` / `git log -1` | 与预期一致（无越界文件） | 三查齐 ⇒ 已完成；缺一 ⇒ 需重做 |

- **无回报 + 产物无变化** ⇒ 空转，直接重派（重派前确认没有残留的 java / gradle 进程）。
- **有产物但无回报** ⇒ 按产物验收，**不要**重跑。
- **有回报但产物缺失/为空** ⇒ 子代理无结论，重派并缩小范围。
- **子代理说「没收到任务 / 需要澄清」** ⇒ 用 `send_message` 补一次，**最多 1 次**；仍失败**如实上报用户**，禁止反复重派。

## 4. 可复制模板

```text
你是一位<职业>（<加载器>/MC <版本>），交付前必须编译通过并贴出命令输出。

## 目标
<一句话说明这一轮唯一要做的事>

## 唯一编译目标
`<一条 gradlew.bat 命令>`   ← 只此一条，禁止追加其它编译/部署命令

## 禁做清单
- 不改 <路径>
- 不做 commit / push / 部署整合包 / 变异用例
- 不重构与本轮无关的代码

## 交付格式
1. 改动清单：`文件:行号` + 一句话说明
2. 编译输出：原样粘贴（含 BUILD SUCCESSFUL/FAILED）
3. 未完成项与风险（无则写「无」）
```

## 5. 常见失败模式

- **同树并发编译**（两个 subagent / 一个 subagent + 主 Agent 同时跑 gradle）⇒ 锁冲突、产物互相覆盖。一次只跑一个。
- **brief 过载**（一轮塞四件事）⇒ 超时且无法归因。拆轮。
- **回报为空**（没给交付格式）⇒ 子代理只回一句「已完成」。模板里必须写交付格式。
- **误用 `subagent` 做接续任务**（它有全新 context，看不到本会话）⇒ 改成 `subagent_fork`，或在 brief 里把背景重写一遍。

## 历史（Codex 侧，2026-10-21 加固版，**DSH 不再使用**）

> `~/.codex/mcp/delegation/server.mjs` 是 **Codex 宿主**的 MCP 委派服务：超时钳制 [60,1800]、同 cwd 互斥（`DELEGATION_BUSY`）、调用方取消即杀进程组、子会话 rollout 持久化、日志 `…\delegation\server.log` 的 `done code=0 timedOut=false`。
> 这些字段（`timeout_seconds` / `DELEGATION_BUSY` / `TIMEOUT requested=… effective=…` / `codex resume`）只出现在 2026-10-22 之前的历史记录里；在 DSH 会话中遇到请按本节 0–3 处理，**不要**去连那个服务。
