# 目标选择器 ActionBar 文案重写 · 26.4-SNAPSHOT.35 验收

- 日期：2026-11-14
- 提交：`68d85ceb`（文案 + 类别注册，53 files +468/−413）
- 范围：P0 三线（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）；fabric 两线未改（P1 欠账 D13）
- 结论：三线实机均显示新文案，无技能名 / 效果牌名残留

## 一、改动摘要

- 常驻提示改「选择一个<类别>目标」，类别闭集六选一：敌对 / 友方 / 中立 / 任意 / 玩家 / 女仆。
- 新增 `target/TargetPromptCategories`：默认按会话 `TargetType` 推导，附属模组可按 actionId 覆写；
  `maid` 预留给 TLMDice 注册（当前无动作使用，故不会出现）。
- 清理死条目：九份 lang 删 `skill.*` 20 键、`valid_target.*` 5 键、`active` / `rejected` / `self_unsupported`；
  客户端删 `skillName()` / `validTargetName()`；11 个物品类的旧「技能名」注释同步清掉。

## 二、实机读数

| 线 | 触发（server `TargetSelectionManager.start`） | 观察到的 ActionBar | 截图 / sha256 前 16 位 |
|---|---|---|---|
| 1.21.1 | `z3000_pull`（ENEMY） | 选择一个敌对目标。潜行+右键退出选择（剩余 7 秒） | `run/neoforge-1.21.1/screenshots/2026-11-14_12.51.18.png` / `7eaefe889ae6f0da` |
| 1.21.1 | `berserk`（NON_HOSTILE） | 选择一个友方目标，或右键对自己使用。移出手持即退出选择 | `...12.51.35.png` / `1e40e790bc9a942b` |
| 1.21.1 | `huo_card`（CREATURE_OR_RIVAL） | 选择一个中立目标。移出手持即退出选择 | `...12.51.49.png` / `318fd664b87beb7e` |
| 1.21.1 | `zhao_blessing`（PLAYER） | 选择一个玩家目标，或右键对自己使用。潜行+右键退出选择（剩余 30 秒） | `...12.51.50.png` / `bc0faf9161d0de45` |
| 1.21.1 | `z3000_pull` + 瞄准牛（不可选） | 红字：只能选择敌对目标。潜行+右键退出选择（剩余 28 秒） | `...12.52.41.png` / `6d852b4fd3d35c6e` |
| 1.20.1 | `z3000_pull` | 选择一个敌对目标。潜行+右键退出选择（剩余 29 秒） | `run/forge-1.20.1/screenshots/2026-11-14_12.53.47.png` / `ef97b066c4cfd886` |
| 1.20.1 | `berserk` | 选择一个友方目标，或右键对自己使用。移出手持即退出选择 | `...12.54.15.png` / `de871be68c0f219c` |
| 26.1.2 | `z3000_pull` | 选择一个敌对目标。潜行+右键退出选择（剩余 29 秒） | `run/neoforge-26.1.2/screenshots/2026-11-14_12.54.59.png` / `b5d29b0067c81fd4` |
| 26.1.2 | `zhao_blessing` | 选择一个玩家目标，或右键对自己使用。潜行+右键退出选择（剩余 29 秒） | `...12.55.07.png` / `4cc59b173cbaa087` |

启动/收停：`scripts/test/win/launch.sh --version <line> --quickplay <world> --timeout 420 --log-dir build/tmp-dsh/live-logs --kill-on-timeout`；
日志 `build/tmp-dsh/live-logs/{1.21.1-20261114-125007,1.20.1-20261114-125257,26.1.2-20261114-125431}.log`；三线均已 taskkill 收停，端口 FREE。

## 三、未覆盖项

- 「任意」：本仓当前没有任何 `TargetType.LIVING` 动作，无法从游戏内触发；映射与文案已就位，等新动作接入即可。
- 「女仆」：注册 API 已提供，TLMDice 侧尚未注册动作；P1 欠账 D13。
- fabric 两线：本轮不改（旧文案仍在），同挂 D13。

## 四、已知取舍

- `CREATURE` / `CREATURE_OR_RIVAL` 映射为「中立」并不精确（CREATURE 含敌对生物：`directional_blast` 等），词表内无更贴近的词；
  代码注释已写明，如需更精确可再裁决拆分类别。
- 「中立」与「任意」一样是纯词表表达，不改变实际可选目标集合（仍由库 `TargetType#matches` 决定）。

## 五、取证方法（可复用）

- 客户端游戏内截图（desktop 捕获对 GL 窗口为全白，不可用）：
  `var mc=Java.loadClass("net.minecraft.client.Minecraft").getInstance(); Java.loadClass("net.minecraft.client.Screenshot").grab(mc.gameDirectory, mc.getMainRenderTarget(), function(c){ console.info(c.getString()); });`
  文件落 `run/<line>/screenshots/<yyyy-MM-dd_HH.mm.ss>.png`；console.info 有时不落 `logs/kubejs/client.log`，按 mtime 列目录更可靠。
- KubeJS Rhino 隐藏 `getYRot` / `setYRot`（属性读写也不可靠），改朝向优先用 server `teleportTo(x,y,z,yaw,pitch)`；
  瞄准类用例可把目标放在准星高度（如浮空牛 `NoGravity:1b`）避免调相机。
- 反射读 `ActionBarManager.message` 被 JVM 拒绝（`IllegalAccessException: restricted lookup`），文案证据只走截图。
