# 星之骰戏 (Astral Dice) 1.5.1 玩家更新日志

已发布：1.5.1（**正式版**）—— 一次只修一个崩溃的修复性更新：玩法、内容与数值都没有变化，存档与联机照旧。

- **崩服修复**：名刀牌耐久耗尽、触发费用结算时，如果玩家**没有佩戴「美咲」护法立牌**，服务端会因费用计算缺少玩家信息而崩溃，存档在 `Ticking player` 处中断；重启后只要再触发一次就再崩一次。本版起不再崩，费用照常结算。
- **兼容性**：与 1.5.0 存档兼容，不需要迁移；联机互通号仍是 **1.5**，1.5.1 与 1.5.0 玩家可以互相联机。

> **本版同时发布四个构建**：`astral_dice-1.5.1+neoforge_1.21.1.jar`（Minecraft 1.21.1 / NeoForge）、`astral_dice-1.5.1+forge_1.20.1.jar`（Minecraft 1.20.1 / Forge）、
> `astral_dice-1.5.1+neoforge_26.1.2.jar`（Minecraft 26.1.2 / NeoForge）与 `astral_dice-1.5.1+fabric_1.20.1.jar`（Minecraft 1.20.1 / Fabric）。四条线同属一个 Release，tag 为 `1.5.1`。

## BUG 修复

- **名刀牌耐久耗尽时崩溃（`Ticking player`）**：名刀牌的耐久归零会触发一次费用结算。若玩家当时**没有佩戴「美咲」护法立牌**，旧代码在计算费用时没有把玩家带进饰品查询，服务端直接抛错并以 `Ticking player` 中断存档；重启后再触发一次就会再崩一次。
  本版把真实玩家一路传到费用结算与饰品查询，并给查询补上「没有玩家就不查」的保护；佩戴「美咲」时 4 → 3 点的减费与蓄力返还都走同一套费用口径，结果不变。
- **其它玩法不变**：牌的费用、耐久、减费条件与蓄力返还没有任何数值调整。

## 安装与前置（必读）

- **`StarEngine` 仍需单独安装**（四条线都一样，版本 **1.1.0**）：发货 jar 不内嵌库，请把对应平台的 `starengine-<加载器>-1.1.0.jar` 放进 `mods`。
- 1.20.1 的 Mixin 运行时：**Mixin Booster ≥0.1.3** 或 **Sinytra Connector**，二选一即可。
- **不需要改配置、不需要迁移存档**：本版只改费用结算里的一处取值，配置项与存档格式都没动，旧存档直接进。

## 反馈

- 遇到问题请到仓库提 issue：[merlin-kitsune/astral_dice](https://github.com/merlin-kitsune/astral_dice/issues)；
  构建与历史版本在 [Releases](https://github.com/merlin-kitsune/astral_dice/releases) 页面。
- 崩溃类反馈请附上：**版本号**（如 `1.5.1`）、**加载器 + Minecraft 版本**、`crash-reports` 里的那一份报告，以及崩之前正在做什么。

---

英文版见同目录 `PLAYER_CHANGELOG.md`（GitHub Release 正文会把中英两版拼接展示）。
