# 交接文档 — 主线 astral_dice_multiloader（multi-main）

> 生成：2026-10-20（Asia/Hong_Kong）｜工作树 `F:\MCProject\astral_dice_multiloader`
> 分支 `multi-main`｜HEAD **`e79bd75e`**（`release(main): merge dev line into multi-main; version 1.5.0-pre.1; starengine 1.1.0-pre.1`）
> 四线版本 **`1.5.0-pre.1`**（`+fabric_1.20.1` / `+forge_1.20.1` / `+neoforge_1.21.1` / `+neoforge_26.1.2`）
> 前置库引脚 **`starengine_version=1.1.0-pre.1`**（四线）
> 工作树状态：干净（`git status --short -uall` = 0）

---

## 0. 一页速览

| 项 | 值 |
|---|---|
| 优先级模型 | **P0 = neoforge-1.21.1 + forge-1.20.1**；P1 = neoforge-26.1.2 + fabric-1.20.1（**欠账，暂不可构建**） |
| 本批性质 | **开发线（2.0.0-SNAPSHOT.132）整体并入主线**，主线 5 个独有提交按用户裁决**废弃**（改由实机重新验证） |
| 合并方式 | `git reset --hard multi-dev-next`（非三方 merge），再改版本号 |
| 编译 | `:neoforge-1.21.1:compileJava` = 0；`:forge-1.20.1:compileJava` = 0 |
| 前置库 | `F:\MCProject\starengine`（main，`03d6a6c`，`1.1.0-pre.1`）；worktree `F:\MCProject\starengine_next`（next，`553195a`） |
| GitHub | 库已改名 **`merlin-kitsune/StarEngine`**（原 `starengine_lib`） |

## 1. 拓扑（四层事实源）

| 路径 | 角色 |
|---|---|
| `F:\MCProject\astral_dice_multiloader` | **主模组主线**（本仓，`multi-main`，1.5.0-pre.1） |
| `F:\mcproject\astral_dice_multiloader-next` | 开发线（`multi-dev-next`，2.0.0-SNAPSHOT.132，历史快照） |
| `F:\MCProject\starengine` | 前置库主线（`main`，1.1.0-pre.1） |
| `F:\MCProject\starengine_next` | 前置库 worktree（`next`，与 main 同 tip 的祖先） |

## 2. 本批已落地（并入主线的内容）

1. **库改名**：`StarEngine_Lib` → `StarEngine`；modid `starengine_lib` → **`starengine`**；包 `com.merlinkitsune.starenginelib` → **`com.merlinkitsune.starengine`**；maven `starengine-<平台>`。
2. **钱包 NBT 迁移**：新键 `starengine`，旧键 `starengine_lib` **保留回退读**（`LEGACY_ROOT_KEY`，四平台 EconomyStorage）——删掉会导致全体玩家星币清零，**不要删**。
3. **立牌机制收敛**：`SignHookDispatcher` 注册式钩子分发（`DiceCombatEvents` 硬编码仅剩 Zhao/Teru 2 处）；`BaseSignItem` 拆分（`SignActiveLockMachine` / `SignActionBar`）；`isEquipped` 22→1（`SignEquipCheck`）；属性/效果施加收口（`SignAttributeUtil` / `EffectTimerGuard`）。
4. **附件合并**：10 个 sign 状态键 → 1 个 `sign_state`（`component/SignState`），旧 10 键仍注册 + 登录幂等迁移。
5. **5 类下沉至库**：`PartyRelations` / `EliteTargets` / `HoldToSelect` / `SelfTargetable` / `EffectTimerGuard`（`TimerSource` seam，主模组构造器注入）。
6. **4 个新立牌**：jill / dorothy / alana / deepseek（含 tooltip 接线、手册、配方；deepseek 无配方）。
7. **冷却口径**：120s 仅 parunan / moses / dorothy / sherry；其余 180s；mamushi = 锁定 60s 且 `skipCooldownAfterLock=true`（无额外冷却）。
8. **VersionGate**：`major.minor` 互通；**含 SNAPSHOT 的构建与任何其它构建互不互通**。
9. **Jade 护甲→防御**：neoforge 打 `EntityHealthAndArmorProvider`；forge 打 `EntityArmorProvider` + `ArmorElementIconMixin`，并在 `forge-1.20.1/build.gradle` 的 client run 显式加 `-Dmixin.config=astral_dice.jade.mixins.json`（dev 不读 mods.toml）。

## 3. 测试台（必须遵守）

- 唯一入口：`pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase <preflight|build|env|launch|cases|report|stop>`（**PowerShell 7**）。
- **dev 运行禁止把 `astral_dice` / `starengine` 的 reobf jar 放进 `run/<线>/mods`**（SRG vs named ⇒ `NoSuchFieldError: f_256929_` / `NoSuchMethodError: Commands.m_82127_`）；类由 `modFolders` / `modImplementation` 提供。
- forge dev 的 Jade mixin 必须靠 `-Dmixin.config`（见 2.9）。
- 用例通道：**forge 侧一律 `channel=kubejs`**（AutoJS 在 forge dev 不可用：jar 为 SRG 字节码；上游无 release/PR 可修）。1.21.1 侧 AutoJS 可用，但为一致性已同批改 KubeJS。
- lingling 用例：`LINGLING-1.20.1.json` / `LINGLING-1.21.1.json` 均为 `channel=kubejs`，**两线实机 PASS**（探针新增 6 个子命令：`linglingread/grant/charge/pickup/drop/reset`）。
- 收尾一律 `--phase stop --force`，核对 `MT_STOP: OK` 且无 java 窗口残留。

## 4. 遗留 / 欠账（如实登记）

| 项 | 状态 |
|---|---|
| **26.1.2 / fabric 两线** | 仍指向旧坐标 `com.merlinkitsune.starengine_lib:starengine_lib-<平台>` ⇒ **当前不可构建**；`fabric-1.20.1` 还残留 `starengine_lib_version_range=>=2.0.0-SNAPSHOT.12 <2.1` |
| AutoJS 在 forge dev | 不可用（SRG 字节码）；已全面回退 KubeJS |
| 主线 5 个废弃提交 | `16b6d356 / 7835da5e9 / 55d46dde6 / 25860e624 / 2dc8d6694`（tooltip ja_jp 回落码、守门覆盖度等）——**需在主线重新验证/重做** |
| lingling 的 D/C/G 组 | 无吸附目标与创造栏转储入口，属探针能力边界 |
| 实机验证 | 主线 1.5.0-pre.1 **尚未实机**（合并后只做了编译） |
| 推送 | 主模组 `multi-main` 与库 `main` **均未 push** |

## 5. 坑（本批实测）

1. `cmd.exe` 互操作不稳（`UtilBindVsockAnyPort: socket failed`）⇒ 原项目 git 用 `GIT_DIR=<worktree>/... git ...` 或失败后重试；**不要**在 WSL 里直接对 `/mnt/f` 跑 `git`（worktree 指针是 `F:/`）。
2. **WSL `~/.m2` 与 Windows `C:\Users\xmace\.m2` 不互通**：发布后必须复制到 Windows m2，否则 Windows 侧 Gradle 解析不到 `starengine-<平台>:1.1.0-pre.1`。
3. `rsync` 同步到 WSL 编译副本时**必须带 `--delete`**，否则已删除的类（如下沉的 `EffectTimerGuard`）会残留在副本里导致假编译错误。
4. 目录改名会打断 worktree 的 `.git` 指针；WSL 下需写 POSIX 绝对路径（`gitdir: /mnt/f/...`）。
5. `git add -A` 会把 `build*` 目录收进索引（本批发生过），已加 `.gitignore` 的 `build*/`。
6. 新增探针子命令需**冷启动**才生效（`kubejs reload` 不重绑 Brigadier）。

## 6. 关键路径

- 主模组源码：`neoforge-1.21.1/`、`forge-1.20.1/`（P0）、`neoforge-26.1.2/`、`fabric-1.20.1/`（欠账）
- 立牌：`src/main/java/com/merlinkitsune/astral_dice/item/sign/`
- 附件：`component/ModAttachments.java`、`component/SignState.java`
- 钩子分发：`item/sign/SignHookDispatcher.java`、`SignHooks.java`
- 计时：`event/EffectTimerGuard`（库内）、`component/SignTimerSourceAdapter.java`（主模组注入）
- 测试台：`scripts/test/mt.ps1`、`scripts/test/cases/`、`scripts/test/resources/kubejs/<版本>/server_scripts/astral_bugfix_probe.js`
