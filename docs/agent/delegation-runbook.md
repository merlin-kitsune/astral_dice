# 委派操作手册（delegate_task / code_review）

> 适用：`~/.codex/mcp/delegation/server.mjs`（2026-10-21 加固版）。服务端已强制：超时钳制 [60,1800]、同 cwd 互斥、调用方取消即杀进程组、子会话 rollout 持久化。

## 1. 四条收敛规则

1. **一次委派只做一件事，只给一个编译目标。** 一件事 = 一个可独立验收的改动面；一个编译目标 = 一条 `./gradlew …` 命令。
2. **`timeout_seconds` 一律 1200–1800。** 服务端把请求值钳制到 [60, 1800]（默认 1200），日志记 `requested=X effective=Y`；超时即失败，不重试、不加时。
3. **一次只发一条，等 `server.log` 出现 `done` 再发下一条。** 不并发、不「先发后补」；同一 cwd 并发会被服务端拒绝（`DELEGATION_BUSY`）。
4. **brief 里禁止同时要求「双线编译 + 部署整合包 + 变异用例 + commit」。** 这四件事各自独立成轮；混在一轮必然超时且无法归因。

## 2. 每轮标准流程

```
体检 → 派活 → 等 done → 编译/守门 → 提交
```

1. **体检**：`git -C <repo> status --short -uall` 必须空输出；有脏改动先处理，否则事后无法归因。
2. **派活**：一条 `delegate_task`，brief 用第 4 节模板；`cwd` 写绝对路径，`timeout_seconds` 写 1200 或 1800。
3. **等 `done`**：`tail -f ~/.codex/mcp/delegation/server.log`，看到 `done code=0 timedOut=false` 才算结束；`timedOut=true` 或长时间无 `done` 一律按第 3 节处置。
4. **编译/守门**：核对产物 `.last.md`（改了什么、编译输出、有无越界文件），再本地跑编译与 `git status`。
5. **提交**：一个改动面一个 commit；信息写清「做了什么 + 为什么」。

## 3. 中断后的处置

三查齐了才判定，顺序不可颠倒：

| 顺序 | 查什么 | 判据 | 结论 |
|---|---|---|---|
| 1 | `server.log` 有无该 run 的 `done` | 有 `done code=0` | 进第 2 查 |
| 2 | `out/<同名>.last.md` 是否存在 | 存在且 mtime ≥ 启动时间 | 产物已落地 |
| 3 | `git status` / `git log -1` | 与预期一致 | 三查齐 ⇒ 已完成；缺一 ⇒ 需重做 |

- **无 `done` + 无 `.last.md` + 工作区无变化** ⇒ 空转，直接重派（重派前先确认无残留进程）。
- **无 `done` 但有 `.last.md`** ⇒ 已完成但日志缺 `done`：按产物验收，**不要**重跑。
- **有 `done` 但 `.last.md` 缺失/为空** ⇒ 子会话无结论，重派并缩小范围。
- 加固后取消会立刻 `kill` 整个进程组（SIGTERM → 3s → SIGKILL），日志出现 `kill SIGTERM group` / `abort-all`；rollout 已持久化，可用 `codex resume` 回放卡点。

## 4. 可复制模板

```text
你是一位<职业>（<加载器>/MC <版本>），交付前必须编译通过并贴出命令输出。

## 目标
<一句话说明这一轮唯一要做的事>

## 唯一编译目标
`<一条 gradlew 命令>`   ← 只此一条，禁止追加其它编译/部署命令

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

- **`DELEGATION_BUSY`**：同 cwd 已有 run 在跑。等它 `done`，不要换 cwd 绕过。
- **`TIMEOUT requested=5400s effective=1800s`**：请求值被钳制，说明 brief 过载 → 拆轮。
- **`empty result`**：子会话无结论，多半是 brief 缺「交付格式」，补格式重派。
