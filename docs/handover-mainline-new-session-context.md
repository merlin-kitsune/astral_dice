# 新会话提示词 — 主线模式（astral_dice_multiloader / multi-main / 1.5.0-pre.1）

> 直接整份复制给新会话即可。

---

## 环境

- 主模组主线：`F:\MCProject\astral_dice_multiloader`（分支 `multi-main`，HEAD `e79bd75e`，四线版本 **1.5.0-pre.1**）
- 前置库：`F:\MCProject\starengine`（main，`03d6a6c`，**1.1.0-pre.1**）；worktree `F:\MCProject\starengine_next`
- GitHub 库仓库：`merlin-kitsune/StarEngine`
- 开发线（历史）：`F:\mcproject\astral_dice_multiloader-next`（`multi-dev-next`，2.0.0-SNAPSHOT.132，仅作参考）

## 先读（按顺序，必须实读）

1. `F:\MCProject\astral_dice_multiloader\docs\handover-1.5.0-pre.1-main.md`（本批正本：已落地/欠账/坑/路径）
2. `F:\MCProject\astral_dice_multiloader\AGENTS.md`（红线区 + 代理协作契约）
3. `F:\mcproject\astral_dice_multiloader-next\docs\agent\AGENTS-full.md` 末尾「待办交接各批」第 39/40 条（历史背景）
4. `F:\MCProject\astral_dice_multiloader\scripts\test\TESTING-SPEC.md`（测试台口径；注意 dev 禁放 jar 的约束）

## 基本盘

- P0 = `neoforge-1.21.1` + `forge-1.20.1`；P1（26.1.2 / fabric）= **欠账，当前不可构建**（仍指旧库坐标）。
- 库 modid = `starengine`，包 `com.merlinkitsune.starengine`，maven `starengine-<平台>:1.1.0-pre.1`。
- 测试台只走 `pwsh -NoProfile -File scripts/test/mt.ps1 ...`（PowerShell 7）；实机结束必须 `--phase stop --force`。
- forge 侧用例一律 `channel=kubejs`（AutoJS 在 forge dev 不可用）。

## 立即待办（按优先级）

1. **主线实机冒烟**：`mt.ps1 --version 1.20.1 --phase env/build/launch/cases --case scripts/test/cases/LINGLING-1.20.1.json`，再 1.21.1；每轮 `--phase stop --force`。
2. **补回被废弃的主线修复**（原 5 个提交）：ja_jp 三张伤害效果牌「回落码」改行底色、tooltip 染色审计 4 线覆盖矩阵、守门覆盖度守卫、AGENTS `.ps1/.psm1` 计数段订正 —— 逐条重做并实机/守门验证。
3. **P1 两线收尾**：把 26.1.2 / fabric 的库坐标从 `starengine_lib` 改为 `starengine`（含 `fabric.mod.json` depends、`include`、`starengine_lib_version_range`），再编译。
4. **推送**：主模组 `multi-main` + 库 `main`（用户明确放行后执行，经代理 `127.0.0.1:7897`）。

## 硬约束（别踩）

- **dev 运行不得把 `astral_dice` / `starengine` 的 reobf jar 放进 `run/<线>/mods`**（会在世界加载时 `NoSuchMethodError: Commands.m_82127_`）。
- **WSL `~/.m2` ≠ Windows `C:\Users\xmace\.m2`**：库发布后要同步到 Windows 侧。
- `rsync` 到 WSL 编译副本必须带 `--delete`。
- 原项目目录的 git 用 `cmd.exe /c "git -C F:\..."`；互操作失败时改用 `GIT_DIR=<worktree 元数据> GIT_WORK_TREE=<路径>`。
- **钱包旧 NBT 键 `starengine_lib` 是迁移兼容用的，不要删。**

## 工作方式（AGENTS 契约）

- 主代理只做：需求澄清 → 拆解 → 派发 → 验收 → 汇总（每回合 ≤6 次请求，禁止轮询）。
- 委派一律走 `mcp__code_review__delegate_task`（明文、可回传）或 `mcp__code_review__code_review`（一次性独立复核）；**禁止 `fork_turns="none"`**，**禁止委托会话再派子代理**。
- 每批交付必须给：文件:行号 + 命令原始输出片段 + 编译/守门结论；未实机项如实登记。
