# Agent Instructions

**本仓**：`F:\MCProject\astral_dice_multiloader` —— Minecraft 模组「Astral Dice」的多加载器工作树（四条线 `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`，同一工作树）。

**本文件只是公共入口**：先按下面的分派认领自己的规则文件，再读共同红线，最后按需查索引。

**事实源原则**：分支 / HEAD / `mod_version` / 是否有未提交改动，一律以**实读命令**为准；文档里的数字只是当次快照，随时可能落后。旧文档（`docs/agent/AGENTS-full.md` 正文、历史批次条目、`docs/AGENT-HANDOFF.md`、`docs/handover-*.md` 里的旧版本）默认只作**历史 / 待核实参考**，不得据此下现状结论。

## 一、宿主分派

| 宿主 | 规则文件 | 口径 |
|---|---|---|
| **Kimi Code** | `docs/agent/AGENTS-kimi.md` | **只有 Kimi Code** 读取并遵守该文件；它是 Kimi 的规则正本 |
| **DSH** | `docs/agent/AGENTS-dsh.md` | DSH **必须先读**它；原 `docs/agent/AGENTS-full.md`、`docs/agent/AGENT-SPLIT.md`、`docs/agent/delegation-runbook.md` 的调用关系**继续有效**，其中的红线区与区间索引照旧使用 |
| **Codex** | `docs/agent/AGENT-SPLIT.md` | 维持原**只读验证**分工：可读、可 `grep`、可写 `docs/**`；不写源码、不跑构建、不跑实机 |
| **其他宿主** | 先向用户确认 | **不得**把 `AGENTS-kimi.md` 或 `AGENTS-dsh.md` 当成自己的规则 |

- `docs/agent/AGENTS-dsh.md` = **DSH 的规则正本**（2026-10-24 起从旧根规则文件迁入；同日按用户裁决清掉非 DSH 内容 —— WSL 口径、路径转译、跨宿主的派活工具说明一律删除，只留实际工作流裁决）。目录内另有历史留存：原根规则文件备份 `AGENTS.md.bak-a-plan-183829` 经 `docs/agent/AGENTS-full.md` 逐字留存。当前 `AGENTS-dsh.md` SHA-256 `bb640cc030551f170c0f772e1771921cd6b31a6d5196535697df5541f9e72c5d`（CRLF，307 行 / 48,926 B）。
- Kimi 当前接手文档：`docs/handover-2026-10-24-kimi-takeover.md`；Kimi 当前工程参考：`docs/agent/PROJECT-REFERENCE.md`。

## 二、共同安全红线（所有宿主）

1. **默认不启动实机**：不自行开 dev 客户端、连拍、探针或用例；实机必须用户明确下达。
2. **默认不部署**：不复制 jar 到整合包、不上传 CurseForge、不改发布配置。
3. **默认不 commit / push / 打 tag**：仅在用户明确要求时执行；任何远程 git 操作一律走本地代理 `http://127.0.0.1:7897`。
4. **保护素材与他人改动**：`models/`、`images/`、`sound/` 属随时更新的素材目录；`git status` 里出现不属于自己的改动（含未跟踪文件）⇒ 立即停手并报告，不 `add` / `commit` / `stash` / `checkout`、不删文件。
5. **互斥锁**：写文件或启动 Gradle 前检查 `F:/MCProject/.agent-locks/`；Kimi 用 `kimi.lock`、DSH 用 `dsh.lock`；尊重对方的活跃锁，结束只清理自己的锁。
6. **不代改他人职责**：模组源码 / 资源 / 版本号 / 依赖 / 发布配置的改动，按所属宿主的规则文件走。

## 三、按需文档索引

| 需要什么 | 去哪里 |
|---|---|
| 工程拓扑、四条线基线、目录职责、常用命令 | `docs/agent/PROJECT-REFERENCE.md` |
| 当前接手 / 交班 | Kimi：`docs/handover-2026-10-24-kimi-takeover.md`；DSH：`docs/handover-*.md` 中最新一份 + `docs/handover-new-session-context.md` |
| 规则全文（逐批历史、领域规格） | `docs/agent/AGENTS-full.md`；标题→行号总表 `docs/agent/AGENTS-TOC.md` |
| 宿主分工矩阵、互斥、冲突处理 | `docs/agent/AGENT-SPLIT.md` |
| 立牌 / 物品的英文与日文命名（wiki 角色人名，禁机翻） | `docs/agent/AGENTS-full.md`〈立牌 / 物品英日命名规则〉（L4087 起）；权威表 `tools/sign_display_names.json`；门禁 `python tools/verify_sign_display_names.py` |
| DSH 委派 runbook | `docs/agent/delegation-runbook.md` |
| 规则库自身说明 | `docs/agent/README.md` |

> 本文件只放「分派 + 共同红线 + 索引」。宿主专用的工作方式（额度口径、委派细节、角色设定）、逐批历史与完整领域规格**都不放在这里**，一律留在各自的规则文件里，按需读取。
