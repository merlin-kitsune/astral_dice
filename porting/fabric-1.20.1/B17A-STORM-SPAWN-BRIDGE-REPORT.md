# B17A — D-09「雷暴将至」夜间刷怪 ×2 · Fabric Spawn 桥交付报告

> 审计编号 D-09。基准线 `forge-1.20.1`（Forge 47.4.10），目标线 `fabric-1.20.1`（Loom，MC 1.20.1）。

## 0. 结论摘要

- **状态**：已落盘；`scripts/__javac_check.py` 的 REAL errors 仍为空（§6）。
- **注入点**：`Mob#finalizeSpawn` 的 **HEAD**，`cancellable = true`，`require = 1`。**不采用原 TODO 注释写的 `TAIL`**：Forge 侧该事件由 transformer 在**调用点**注入，语义是「先派发、后 finalizeSpawn」，取消要能**跳过**方法体 —— TAIL 定义上做不到。
- **不递归翻倍**：Forge 靠「复制体直接调 `Mob#finalizeSpawn`、不走 `ForgeEventFactory.onFinalizeSpawn`」；本线注入点就在该方法体内，改用 **ThreadLocal 重入闸**（覆盖整个派发期）复现同一语义。

## 1. 改动清单（4 文件）

| # | 路径 | 行 | 动作 |
|---|---|---|---|
| 1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/event/entity/living/MobSpawnEvent.java` | 1–122 | 新建：`MobSpawnEvent` + 嵌套 `@Cancelable FinalizeSpawn`，字段/访问器与 Forge 逐一对齐 |
| 2 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/bridge/MobFinalizeSpawnBridgeMixin.java` | 1–109 | 新建：`@Mixin(Mob.class)` + `@Inject(at=@At("HEAD"))` + ThreadLocal 重入闸 |
| 3 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventEffects.java` | 350–378 | 删原 349–387 的 `/* */` 注释块（含 Forge 实现），还原为真实现 `onFinalizeSpawn`；helper `placeStormCopy`/`hasStormWatcherNear` 保留于 :380–403 |
| 4 | `fabric-1.20.1/src/main/resources/astral_dice.mixins.json` | 15 | mixins 数组追加 `"bridge.MobFinalizeSpawnBridgeMixin",` |

未改：`fabric.mod.json`（:33–35 已含 `astral_dice.mixins.json`）、`AstralDiceMod.java`（:166 已 `LoaderBus.INSTANCE.register(EventEffects.class)`，新 `@SubscribeEvent` 方法自动扫描）、`build.gradle` / `gradle.properties` / `shop/**` / `player/**`。

## 2. 注入点 javap 原文证据

取证 jar（与 `scripts/__javac_check.py:8` 同一份，Loom 编译类路径）：`C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`（备用路径 `~/.gradle/caches/fabric-loom/1.20.1/minecraft-merged.jar` 实测仅 22 字节空壳，不可用）。

`javap -p -c`，`Mob_named.javap.txt:2651`：

```
public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
    net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance,
    net.minecraft.world.entity.MobSpawnType, net.minecraft.world.entity.SpawnGroupData,
    net.minecraft.nbt.CompoundTag);
  Code:  （71 字节）
  ... getRandom() / FOLLOW_RANGE AttributeModifier("Random spawn bonus", triangle(0.0, 0.11485000000000001)) / nextFloat() < 0.05f -> setLeftHanded ...
  71: areturn          // 唯一返回点
```

- `MobSpawnType`（1.20.1）17 值：`NATURAL, CHUNK_GENERATION, SPAWNER, STRUCTURE, BREEDING, MOB_SUMMONED, JOCKEY, EVENT, CONVERSION, REINFORCEMENT, TRIGGERED, BUCKET, SPAWN_EGG, COMMAND, DISPENSER, PATROL`；监听器只用 `NATURAL`。
- 组大小由 `Biome` 的 `SpawnerData(minCount/maxCount)` + `Mob#getMaxSpawnClusterSize()` + `Mob#isMaxGroupSizeReached(int)` 决定，「一组 4 只」不是常量。`NaturalSpawner_named.javap.txt`（`spawnCategoryForPosition`）原文偏移：

```
443: invokevirtual Mob.finalizeSpawn(...NATURAL, aload 17, aconst_null)
446: astore 17            // SpawnGroupData 回填
448: iinc 11, 1            // 该区块已刷数量
451: iinc 19, 1            // 组内计数
457: invokevirtual ServerLevel.addFreshEntityWithPassengers(Entity)   // ★ finalizeSpawn 之后才入世
462: AfterSpawnCallback.run
470: iload 11; 474: invokevirtual Mob.getMaxSpawnClusterSize:()I; 477: if_icmplt 481; 480: return
481: aload 28; 483: iload 19; 485: invokevirtual Mob.isMaxGroupSizeReached:(I)Z; 488: ifeq 494
```

## 3. `require` 命中次数

- `require = 1`（`MobFinalizeSpawnBridgeMixin.java:80`），与 `astral_dice.mixins.json` 的 `"injectors": { "defaultRequire": 1 }` 一致；依据：`Mob` 类只有**一个**同名同参 `finalizeSpawn` 方法体（javap 全类枚举仅此一条），HEAD 注入每次进入方法体必命中，命中次数恒为 1。`require` 是「**最少**命中次数」，写大了构建期不报错、只在 `Mob` 类加载时抛 `InjectionError` ⇒ 取实测值，不加余量。

## 4. 与 Forge 语义的映射与差异边界

| 维度 | Forge 47.4.10 | 本线 | 一致性 |
|---|---|---|---|
| 派发时机 | transformer 在调用点注入，**先事件后 finalizeSpawn** | `@At("HEAD")` | 等价 |
| 取消 | 取消 ⇒ 方法不执行、返回值恒 `null`，**实体仍入世** | `cir.setReturnValue(null)` 后 return | 等价 |
| 阻止入世 | `setSpawnCancelled(true)` ⇒ 平台阻止生成 | `self.discard()` + 返回 `null`（vanilla `ServerLevel.addEntity` 对 `isRemoved()` 实体早退并打 `Tried to add entity {} but it was marked as removed already`） | 等价 |
| 复制体不复发 | 复制体直接调 `Mob#finalizeSpawn` | ThreadLocal 重入闸，`post` 期间置位，复制体的 HEAD 直接 return | 等价 |
| 事件字段 | entity/level/x,y,z/spawnType/spawnData/spawnTag/difficulty/spawner/spawnCancelled | 同左，`getEntity()` 协变返回 `Mob`，`getSpawner()` 传 `null`（自然刷怪无 spawner） | 等价 |
| 数量手段 | 额外生成 1 只复制体 | 同左 | 等价 |

**差异边界（不影响当前消费方）**：
- **D1 字段不回写**：Forge 允许 `setDifficulty/setSpawnData/setSpawnTag` 并传入 `finalizeSpawn`；HEAD 注入未回写入参。唯一消费方只读事件，不受影响；后续需要可加 `@ModifyVariable(argsOnly = true)`。
- **D2 派发面更宽**：本桥在**所有** `finalizeSpawn` 调用（含 SPAWNER/STRUCTURE/COMMAND）都派发，靠监听器按 `getSpawnType()` 过滤；Forge 只在它选定的调用点派发。
- **D3 子类覆写且不调 `super` 时绕过**：注入的是 `Mob` 自身方法体。已扫 Forge merged jar：`Zombie`/`AbstractSkeleton`/`Spider`/`Drowned` 经 `Monster.finalizeSpawn` 中转、`Slime`/`Strider` 直接 `invokespecial Mob.finalizeSpawn`，可达；完全覆写不回调的子类两侧同样绕过。
- **D4 仅服务端**：`finalizeSpawn` 只存在于逻辑服务端路径，无需 `isClientSide()` 守卫，与 Forge「本事件只在逻辑服务端派发」一致。

## 5. 机器行位置（B7 实机断言锚点）

```
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventEffects.java:377
377:        LOGGER.info("AP_EVENT_EFFECT: storm_night_spawn_x2:type={}", mob.getType());
```

可达性链：`AstralDiceMod.java:166` 注册 → `@SubscribeEvent onFinalizeSpawn`（`EventEffects.java:358-359`）→ NATURAL / 未取消 / `Enemy` / `ServerLevel` / `isNight()` / 64 格内 `STORM_APPROACHING`（:360-367）→ `mob.getType().create(level)`（:368）→ `placeStormCopy`（:373）→ **:377**。`grep -rn "storm_night_spawn_x2" fabric-1.20.1/src/main/java` 唯一命中 :377。

## 6. 自检原始输出

`python scripts/__javac_check.py`：

```
EXIT 1 ERRORS 171 jars 685 srcs 522
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984

EXIT 0
```

REAL errors / REAL by file 两段皆空；`grep -c "MobSpawnEvent" fabric-1.20.1/build/javac-check/errors.txt` = **0**（脚本枚举 `src/main/java` 全部 522 个 .java，含 mixin）⇒ 新增/改写文件零错误。

`python tools/verify_fabric_assets.py`（exit 0）：

```
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138  [2] 标签 10/29/10  [3] 音效 23  [4] 粒子 2
[5] 语言: zh_cn 980 / en_us 980 / ja_jp 980   [6] 手册 157/138   [7] 创意标签 156/157（基线已知: repulsion）  [8] 饰品槽位 7
=== FAIL ===
  ✗ 方块物品资源不全(blockstate/model/texture=['card_shop','chip_shop','event_drawer'])
  ✗ 缺模型(紫黑格) 20 项 / ✗ 缺贴图 17 项（abyss_reagent_chip、alana_sign、ame_sign、card_shop、chip_shop、deepseek_sign、
     dorothy_sign、effect_card_barricade、effect_card_repulsion、event_drawer、event_ticket、extra_battery_chip、flashlight_burst_chip、
     fortune_stick_chip、infinite_grid_chip、jill_sign、kangel_sign、lingling_sign、repulsion、sensor_chip）
```

FAIL 三条为**既存状态**：本任务未触碰任何 `assets/`/`models/`/`textures/`（唯一资源改动是 mixin 清单，该脚本不扫描）⇒ **未新增 FAIL**。

`python tools/check_lang_sync.py --line fabric-1.20.1`（exit 0）：`[OK]   fabric-1.20.1: en_us=980 ...`

字节复核（CRLF + UTF-8 无 BOM）：

```
EventEffects.java                   bytes= 39516 lines= 622 crlf= 622 bareLF=0 loneCR=0 BOM=False
MobFinalizeSpawnBridgeMixin.java    bytes=  6643 lines= 109 crlf= 109 bareLF=0 loneCR=0 BOM=False
MobSpawnEvent.java                  bytes=  4845 lines= 122 crlf= 122 bareLF=0 loneCR=0 BOM=False
astral_dice.mixins.json             bytes=  2048 lines=  60 crlf=  60 bareLF=0 loneCR=0 BOM=False
```

## 7. 未做 / 无法判定

### 7.1 只能实机确认的清单

1. refmap 是否在 `remapJar` 后仍命中 intermediary `finalizeSpawn`（可看 refmap json，本次未构建）。
2. `require = 1` 的**实际命中**：`InjectionError` 只在 `Mob` 类首次加载时抛。
3. 机器行 :377 的真实触发：需「夜晚 + `STORM_APPROACHING` + 64 格内有玩家 + 自然刷怪」四条件同时成立。
4. 复制体实际数量（7 个候选偏移全被 `noCollision`/`checkSpawnObstruction` 拒绝时静默放弃、不打印日志，实测需统计）与 `self.discard()` 阻止入世无副作用残留（静态依据是 `ServerLevel.addEntity` 对 `isRemoved()` 早退）。

### 7.2 「原生组数量 ×2」的可选方案（本桥未采用，供裁决）

本桥等价对象是 **Forge 自己的实现**（额外造 1 只复制体），不是「组数量翻倍」。若产品侧要的是后者：

| 方案 | 落点 | 与 Forge 的差异 / 风险 |
|---|---|---|
| A 维持现状 | — | **与 Forge 完全等价**；复制体不继承原件 `SpawnGroupData` 上下文，落点被拒时静默失败 |
| B 放大组上限 | `@ModifyReturnValue` 到 `Mob#isMaxGroupSizeReached(int)`、`Mob#getMaxSpawnClusterSize()` | Forge 无此行为；只抬上限，实际数量仍受 `SpawnerData.maxCount` 抽样 ⇒ **不一定翻倍**；影响全部生物与刷怪笼/结构 |
| C 循环内放大 | 注入 `NaturalSpawner.spawnCategoryForPosition` 组循环（`iinc 19` / `isMaxGroupSizeReached` 分支） | 真正同组多刷，Forge 侧无对应；破坏 vanilla 抽样分布与 `SpawnGroupData` 共享语义 |

**建议**：保持 A。若要做 B/C，须同步改 Forge 蓝本，否则两线行为分叉。

### 7.3 其他

- 未构建 / 未启动游戏 / 未跑 datagen / 未提交（任务硬约束）；`platform/event/Cancelable.java` 首行自我 import 历史遗留未修（与本任务无关，避免扩大改动面）。

