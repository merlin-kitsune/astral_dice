# Z3000 立牌 .34 五线回归 — P0 三线实机验收（2026-11-14 12:24）

> 版本：`26.4-SNAPSHOT.34`（五线 bump 后的第二次实机批次；.33 仅构建）。本轮改动：拖拽速度 0.175→0.5 格/tick、渲染起点 0.65→0.45 身高、管道 X 居中修正 0.5、被拖拽标记 `astral_dice.z3000_dragged.<uuid>`（击杀不限时机给 1 层反击）。
> 本轮未 commit / 未 push / 未打 tag；实机进程均已收停（25565/25575/25585 NO_LISTEN）。
> 装备：Curios `stand` 槽 0 = `astral_dice:z3000_sign`；玩家 Dev；夹具 `minecraft:husk`（NoAI/Silent/PersistenceRequired/Glowing，最大生命 1000 或 10）。

## 0. 环境与日志

| 线 | 版本（loading 行） | 日志 |
|---|---|---|
| neoforge-1.21.1 | `astral_dice-26.4-SNAPSHOT.34+neoforge_1.21.1.jar`（1.21.1-20261114-120953.log:120-121） | `build/tmp-dsh/live-logs/1.21.1-20261114-120953.log` |
| forge-1.20.1 | `astral_dice-26.4-SNAPSHOT.34+forge_1.20.1.jar`（1.20.1-20261114-121617.log:154-155） | `build/tmp-dsh/live-logs/1.20.1-20261114-121617.log` |
| neoforge-26.1.2 | `26.4-SNAPSHOT.34+neoforge_26.1.2`（AutoJS `modVersion` 实读；日志同下） | `build/tmp-dsh/live-logs/26.1.2-20261114-122120.log` |

三线各自独立启动、测试、收停，同一时刻只有一条线在跑。收停命令 `bash scripts/test/win/stop.sh --version <line>` 只打印 PID，实际由 `taskkill /PID ... /F /T` 收尾（脚本明文：Agent 侧终止）。

## 1. 主动拖拽（9 格 / 17 格 / 16.00 格）

| 用例 | 1.21.1 | 1.20.1 | 26.1.2 |
|---|---|---|---|
| 9 格施放 | `AP_Z3000_ACTIVE ... dist=9.00 cooldown=3200`（12:10:59） | `... dist=9.00 cooldown=3600`（12:17:40） | `... dist=9.00 cooldown=3600`（12:22:12） |
| 9 格撞击伤害 | `AP_GRAPPLE_DAMAGE ... damage=5.00 dead=false` | 同（12:19:30） | 同（12:22:13） |
| 9 格终点 | 终态 `hp=995.0 dist=1.50 counter=0` | `hp=995.0 dist=1.50 counter=0` | `hp=995 dist=1.46 counter=0`（26.1.2 停距略小） |
| 17 格拒绝 | `confirm FAIL: target_too_far ... dist=17.0 max=16.0`（12:14:16），会话保留、冷却未消耗 | 同（12:19:36），cd=0 | 同（12:22:19），cd=0 |
| 16.00 格接受 | `AP_Z3000_ACTIVE ... dist=16.00 cooldown=3200`（12:14:19），END age=50 | `... dist=16.00`（12:19:36），END age=50，终态 hp=15.0 dist=1.50 | `... dist=16.00`（12:22:25），END age=53，终态 hp=15 dist=1.50 |
| 拖拽耗时 | 9 格 END age=30；16 格 age=50 | 9 格 age=27；16 格 age=50 | 9 格 age=33；16 格 age=53 |

`AP_GRAPPLE_END ... reason=arrived` 三线均命中；`PULL_STEP=0.5` 与旧 .32 的 0.175 相比，16 格耗时从约 90 tick 降到 50 tick 级。

**1.20.1 环境坑（非缺陷）**：首测 9 格 hp 掉到 994（多 1 点）。`getLastDamageSource()` 读到 `DamageSource (inWall)`，方块扫描在 testworld `(4,-59,0)`、`(5,-59,0)` 发现旧测试残留 `minecraft:stone`；被拖目标穿过触发窒息 1 点。换到干净带 `z=100`（x=2..18,y=-60..-58 全 air）重跑即精确 995。**副作用事实**：拖拽路径会穿过实体方块（placeTarget 只按 heightmap 抬地面，不处理半空障碍），本轮未改代码。

## 2. 被动：拖拽后击杀（不限时机）

三线都等到目标被拖到面前约 5–6 秒后再击杀（超过撞击时刻）：

- 1.21.1：`AP_Z3000_DRAG_KILL ... counter=1` + `AP_Z3000_RECYCLE ... remaining=3042->2039`（12:14:27）
- 1.20.1：目标 tags = `[astral_dice.z3000_dragged.380df991-..., r17b]`；`AP_Z3000_DRAG_KILL ... counter=1` + `AP_Z3000_RECYCLE ... remaining=3448->2311`（12:19:44）
- 26.1.2：tags 同形；`AP_Z3000_DRAG_KILL ... counter=1` + `AP_Z3000_RECYCLE ... remaining=3284->2201`（12:22:41）
- 未拖拽目标被击杀：26.1.2 counter 保持 0（`damage ... by Dev` 直接击杀 plain husk）。

## 3. 反击上限与冷却门

- 共享 `COUNTER_MAX=4`：三线把 counter 预置到 4 后拖拽+击杀，`AP_Z3000_DRAG_KILL ... counter=4`，终态仍 4（1.21.1 12:14:55 / 1.20.1 / 26.1.2）。
- 冷却 -33%（公式 `(remaining*67+99)/100`）：1.21.1 `3042->2039`、`2362->1583`；1.20.1 `3448->2311`；26.1.2 `3284->2201`。
- 「每 1200 tick 最多一次」：1.21.1 两次 RECYCLE 间隔 67 s（≈1340 tick，12:14:27→12:15:34）；1.20.1 与 26.1.2 在 60 s 内连杀 26+ 只，日志里 RERECYCLE 各仅 1 条。
- 冷却门控（`BaseSignItem.performSkillForCurio`）：`setSignActiveCooldownEnd(sp,9e18)` 后 `isSelecting=false`；复位 0 后 `isSelecting=true`；`cancelSessionForTests` 后 false。三线一致。

## 4. 攻击叠层

- 26.1.2：26 只 husk 逐个击杀，stacks 序列 `0,1,1,2,2,...,11,11,12,12,12`（每 2 杀 +1，封顶 12）；`ATTACK_DAMAGE=112`（基础 100 + 12）。
- 1.20.1：同序列升到 12；基础攻击 1 时 `ATTACK_DAMAGE=13`。
- 1.21.1：序列升到 12 后继续杀 s15/s16 仍 12；基础 1 → 13。
- `最大生命 <20` 不计层：三线击杀 10 血 husk 后 stacks 保持 12、healthy 计数 0。

## 5. 视觉（**用户已于 2026-11-14 目视确认**）

- 渲染常量（三线 jar 内 `javap` 取证）：`START_HEIGHT_FACTOR=0.45`、`PIPE_X_CENTER_FIX=0.5`、`HEAD_BACK=0.55`。
- 1.21.1 第三人称截图（本轮已在会话内展示）：管道自玩家腰部下方伸出、连到吸尘器头。
- 用户原始三点：① 接头居中；② 起点在玩家正中心下方；③ 拖拽提速（已由 §1 age 数据验证）。① ② 用户已于 2026-11-14 12:37 目视确认（1.21.1 第三人称截图）。

## 6. 未验证 / 未做

- 视觉美观度（接头是否「正中心」）未由机器判定，结论仅来自用户目视（1.21.1；26.1.2 未取到有效帧）。
- 26.1.2 的 `attribute` 命令在本版静默无效（`generic.max_health` 未命中），夹具改用 `Entity#getAttribute(...).setBaseValue`；`AutoJS.attack` 在 26.1.2 也不生效，击杀改用 `server.createCommandSourceStack().withSuppressedOutput()` + `damage ... minecraft:player_attack by Dev`。
- 1.20.1 `sp.level.getEntity(id)` 返回 null，必须遍历 `getAllEntities()`。
- 三线 .34 未构建产物校验之外的东西：没有重跑 datagen；没有 commit。

## 7. 复现清单

```bash
bash scripts/test/win/launch.sh --version 1.21.1 --quickplay 新的世界 --timeout 420 --log-dir build/tmp-dsh/live-logs --kill-on-timeout
bash scripts/test/win/launch.sh --version 1.20.1 --quickplay testworld --timeout 420 --log-dir build/tmp-dsh/live-logs --kill-on-timeout
bash scripts/test/win/launch.sh --version 26.1.2 --quickplay flatworld --timeout 420 --log-dir build/tmp-dsh/live-logs --kill-on-timeout
```

关键日志行过滤：`grep -E "AP_Z3000_ACTIVE|AP_Z3000_REJECT|AP_Z3000_DRAG_KILL|AP_Z3000_RECYCLE|AP_GRAPPLE_(ATTACH|DAMAGE|END)|target_too_far"`。
