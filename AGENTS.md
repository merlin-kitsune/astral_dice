# Agent Instructions

**本仓**：`F:\MCProject\astral_dice_multiloader`（原生 Linux 宿主为同一块 NTFS 盘的原生挂载：`/mnt/f/MCProject/astral_dice_multiloader`；无 hgfs）—— Minecraft 模组「Astral Dice」的多加载器工作树（各线 `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`，同一工作树）。

**本文件只是公共入口**：先按下面的分派认领自己的规则文件，再读共同红线，最后按需查索引。

**事实源原则**：分支 / HEAD / `mod_version` / 是否有未提交改动，一律以**实读命令**为准；文档里的数字只是当次快照，随时可能落后。旧文档（`docs/agent/AGENTS-full.md` 正文、历史批次条目、`docs/handover-*.md` 里的旧版本）默认只作**历史 / 待核实参考**，不得据此下现状结论。

## 一、宿主分派

| 宿主 | 规则文件 | 口径 |
|---|---|---|
| **Kimi Code** | `docs/agent/AGENTS-kimi.md` | **只有 Kimi Code** 读取并遵守该文件；它是 Kimi 的规则正本 |
| **DSH** | Windows 原生：`docs/agent/AGENTS-dsh.md`；**Linux 原生：`docs/agent/AGENTS-dsh-linux.md`** | DSH **必须先读**自己的规则正本（Linux 宿主读 `AGENTS-dsh-linux.md`，Windows 宿主读 `AGENTS-dsh.md`）；原 `docs/agent/AGENTS-full.md`、`docs/agent/AGENT-SPLIT.md`、`docs/agent/delegation-runbook.md` 的调用关系**继续有效**，其中的红线区与区间索引照旧使用 |
| **Codex** | `docs/agent/AGENT-SPLIT.md` | 维持原**只读验证**分工：可读、可 `grep`、可写 `docs/**`；不写源码、不跑构建、不跑实机 |
| **其他宿主** | 先向用户确认 | **不得**把 `AGENTS-kimi.md` 或 `AGENTS-dsh.md` 当成自己的规则 |

- `docs/agent/AGENTS-dsh.md` = **DSH 的规则正本**（2026-10-24 起从旧根规则文件迁入；同日按用户裁决清掉非 DSH 内容 —— WSL 口径、路径转译、跨宿主的派活工具说明一律删除，只留实际工作流裁决）。目录内另有历史留存：原根规则文件备份 `AGENTS.md.bak-a-plan-183829` 经 `docs/agent/AGENTS-full.md` 逐字留存。当前 `AGENTS-dsh.md` SHA-256 `318db22207544e0ee9e76840bf06383ad7203d742bc76495bc9585a71bd64e0e`（纯 LF，331 行 / 59,052 B；2026-11-11 复核）。
- `docs/agent/AGENTS-dsh-linux.md` = **DSH 在 Linux 原生宿主的规则正本**（2026-10-31 起；逐条 Linux 化，Windows 口径保留在 `AGENTS-dsh.md` 作历史正本）。Linux 与 Windows 冲突时各读自己的正本；共同红线与索引仍以本文件为准。
- Kimi 当前接手文档：`docs/handover-2026-10-24-kimi-takeover.md`；Kimi 当前工程参考：`docs/agent/PROJECT-REFERENCE.md`。

## 二、共同安全红线（所有宿主）

1. **默认不启动实机**：不自行开 dev 客户端、连拍、探针或用例；实机必须用户明确下达。
2. **默认不部署**：不复制 jar 到整合包、不上传 CurseForge、不改发布配置。
3. **默认不 commit / push / 打 tag**：仅在用户明确要求时执行；任何远程 git 操作一律走本地代理 `http://127.0.0.1:7897`（**Linux 真机例外**：clash-verge-rev 以 TUN 全局接管路由，直连即可，见 `docs/agent/AGENTS-dsh-linux.md`）。
4. **保护素材与他人改动**：`models/`、`images/`、`sound/` 属随时更新的素材目录；`git status` 里出现不属于自己的改动（含未跟踪文件）⇒ 立即停手并报告，不 `add` / `commit` / `stash` / `checkout`、不删文件。
5. **互斥锁**：写文件或启动 Gradle 前检查 `F:/MCProject/.agent-locks/`（Linux：`/mnt/f/MCProject/.agent-locks/`）；Kimi 用 `kimi.lock`、DSH 用 `dsh.lock`；尊重对方的活跃锁，结束只清理自己的锁。
6. **不代改他人职责**：模组源码 / 资源 / 版本号 / 依赖 / 发布配置的改动，按所属宿主的规则文件走。
7. **没有详细测试就没有发布版本**：未完成本地详细测试与实机冒烟取证的版本一律不得作为发布版本；生产版本的 push / tag / Release / CurseForge 上传必须是整条流程的最后一步（2026-11-02 用户裁决）。
8. **早提交、勤提交**：每完成一个可独立验证的改动单元（功能 / 修复 / 文档 / 规则 / 版本号），**立即本地提交**，不得把多轮改动堆在工作树里等批量提交；一个提交只做一个可描述、可回滚的变更，提交信息必须如实写明改动面与验证读数；默认仍**不 push**（见第 3 条）。（2026-11-07 用户裁决）
9. **单写者 + 实机互斥**：同一时刻**一个文件只能有一个写者**（主 Agent / 子代理 / teammate 不得并发写同一文件；并行时按互斥写作用域切分，重叠即停手重切）；测试台端口（`25585` AutoJS / `25565` 游戏 / `25575` fabric RCON）**同一时刻只允许一条线**，一旦发现**其他实机测试正在进行**，**必须搁置本轮实机测试并立即告知用户**，不等待、不排期、不重试、不抢端口。（2026-11-07 用户裁决）

## 三、按需文档索引

| 需要什么 | 去哪里 |
|---|---|
| 工程拓扑、各线基线、目录职责、常用命令 | `docs/agent/PROJECT-REFERENCE.md` |
| 当前接手 / 交班 | Kimi：`docs/handover-2026-10-24-kimi-takeover.md`；DSH：`docs/handover-*.md` 中最新一份 + `docs/handover-new-session-context.md` |
| Linux 真机适配与迁移（2026-10-31） | `docs/handover-2026-10-31-native-linux-migration.md`（原生 Linux 实测、盘符重映射、游戏测试资产接管 + 剩余 checklist）；规则见 `docs/agent/AGENTS-dsh-linux.md` |
| NTFS 双系统工作树 / Windows 互操作 | `docs/handover-2026-10-31-native-linux-migration.md`（`/mnt/f` 同盘、大小写与换行口径、锁与并发）；旧副本同步脚本 `scripts/dev/rsync-original.sh` 已停用 |
| 规则全文（逐批历史、领域规格） | `docs/agent/AGENTS-full.md`；标题→行号总表 `docs/agent/AGENTS-TOC.md` |
| 宿主分工矩阵、互斥、冲突处理 | `docs/agent/AGENT-SPLIT.md` |
| 立牌 / 物品的英文与日文命名（wiki 角色人名，禁机翻） | `docs/agent/AGENTS-full.md`〈立牌 / 物品英日命名规则〉（L4053 起；行号以 docs/agent/AGENTS-TOC.md 为准）；权威表 `tools/sign_display_names.json`；门禁 `python tools/verify_sign_display_names.py` |
| DSH 委派 runbook | `docs/agent/delegation-runbook.md` |
| 规则库自身说明 | `docs/agent/README.md` |
| Linux 测试台（bash） | `scripts/test/linux/README.md`（`env.sh` / `launch.sh` / `stop.sh`）；Linux 规则正本的〈测试台现状（Linux）〉一节 |
| 桌面控制 / 游戏内注入通道 | Windows 当前 DSH：`mcp__wincu__windows_computer_use_*`（详见 `docs/agent/AGENTS-dsh.md` 第 10 条）；Linux 宿主见 `docs/agent/AGENTS-dsh-linux.md` 的〈桌面控制与实机通道（Wayland）〉与〈AutoJS 运行时通道〉（`computer-use-linux` / `screen-ocr` / `autojs` 仅 Linux 注册） |

> 本文件只放「分派 + 共同红线 + 索引」。宿主专用的工作方式（额度口径、委派细节、角色设定）、逐批历史与完整领域规格**都不放在这里**，一律留在各自的规则文件里，按需读取。
