# 目标选择器 ActionBar 文案 — Fabric 双线实机验收（26.4-SNAPSHOT.36）

日期：2026-11-14
版本：`26.4-SNAPSHOT.36`（fabric-1.20.1 / fabric-1.21.1）
改动：`target/TargetPromptCategories` + `client/TargetSelectionClient` 类别文案（与 P0 三线 .35 同口径），lang 键 44 → 23。

## 1. 环境与启动

| 线 | 世界 | 启动命令 |
|---|---|---|
| fabric-1.20.1 | `testworld` | `bash scripts/test/win/launch.sh --version fabric-1.20.1 --quickplay testworld --timeout 900 --log-dir build/tmp-dsh/live-logs --kill-on-timeout --enable-autojs` |
| fabric-1.21.1 | `testworld` | 同上，`--version fabric-1.21.1` |

日志：`build/tmp-dsh/live-logs/fabric-1.20.1-20261114-130726.log`、`build/tmp-dsh/live-logs/fabric-1.21.1-20261114-131041.log`。
AutoJS 在 Fabric 是条件依赖，**必须带 `--enable-autojs`**（等价 `-PautojsTestMod=1`），否则 25585 不监听。

## 2. 判据

真实持卡路径，不用服务端 `TargetSelectionManager.start` 旁路：

1. 服务端 `give` / `item replace entity @s hotbar.0 with <card>`；
2. 客户端切到该槽（`inv.selected` / `hotbar.0`）；
3. 客户端 `mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND)` 触发真实右键；
4. 读 ActionBar 文案。

服务端旁路 `start()` 时客户端 `holdToSelect=false`、`active=false`、剩余秒数溢出成 2147483647，是假态，**不能作为 UI 判据**。

## 3. 读数

### fabric-1.20.1（反射 `steadyPrompt()`，hold=true / active=true）

| 动作（卡片） | ActionBar |
|---|---|
| `berserk`（`astral_dice:effect_card_berserk`） | 选择一个友方目标，或右键对自己使用。移出手持即退出选择 |
| `directional_blast`（`astral_dice:effect_card_directional_blast`） | 选择一个中立目标。移出手持即退出选择 |
| `fu_card`（`astral_dice:fu_card`） | 选择一个玩家目标，或右键对自己使用。移出手持即退出选择 |

### fabric-1.21.1（截图取证，真实持卡右键）

| 截图 | 动作 | ActionBar | sha256 前 16 |
|---|---|---|---|
| `fabric-1.21.1/run/client/screenshots/2026-11-14_13.11.42.png` | `berserk` | 选择一个友方目标，或右键对自己使用。移出手持即退出选择 | ea563776c25355c5 |
| `fabric-1.21.1/run/client/screenshots/2026-11-14_13.12.06.png` | `directional_blast` | 选择一个中立目标。移出手持即退出选择 | 0305a44ff6ccb4eb |
| `fabric-1.21.1/run/client/screenshots/2026-11-14_13.12.09.png` | `fu_card` | 选择一个玩家目标，或右键对自己使用。移出手持即退出选择 | 79e34c0fd984fa16 |

截图目录注意：Fabric 客户端的 `mc.gameDirectory` 是 `<line>/run/client`，不是 `<line>/run`。

## 4. 取证手法与坑

- fabric-1.20.1：`Screenshot.grab(gameDirectory, getMainRenderTarget(), consumer)` 没有写出文件，改走反射读 `TargetSelectionClient.steadyPrompt()`（该类与本会话同 JVM，反射可用）。
- fabric-1.21.1：同一反射被拒（`IllegalAccessException: Attempt to lookup caller-sensitive method using restricted lookup object`），改用截图 + 肉眼判读；公开访问器 `isActive()` / `isHoldToSelect()` / `remainingSeconds()` 不受影响。
- 1.21.1 的 `Inventory` 没有 `setSelectedSlot`（1.21.1 用 `selected` 字段），本报告改用 `item replace ... hotbar.0` 规避。
- Rhino 会把 getter 变属性：`p.name.string`、`mc.level.dimension().location()`；`mc.player.getInventory()` 与 `mc.player.getMainHandItem()` 正常。

## 5. 未覆盖

- 拒绝态（红字「只能选择X目标」）在 Fabric 两线未单独取图；同一 `steadyPrompt()` 分支已在 P0 三线 .35 实机取证（`docs/reports/2026-11-14-target-selector-actionbar-35.md`）。
- 「任意」「女仆」两词条仍无可达动作：`LIVING` 动作不存在；女仆注册留给 TLMDice 附属（P1 欠账 D13）。
- CREATURE → 「中立」不精确的取舍与 P0 相同，未在 Fabric 线单独裁决。

## 6. 收停

两线均按 `scripts/test/win/stop.sh --version <line>` 收停并 `taskkill` 残留 PID；收停后 25565 / 25575 / 25585 均无监听。
