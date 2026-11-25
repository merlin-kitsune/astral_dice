# Astral Dice 1.20.1 Fabric 实机清单（2026-11-02）

> 范围：`fabric-1.20.1` 单线 · 批次 LT0–LT7 · 条目 54 条 · 机判用例 6 条 · 通道 CH-R / CH-A / CH-M / CH-S

> 事实源：代码 + 实机取证。本文只做「排期 + 判据登记」，不是产品规格。

> 上游依据：`porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md` §4（U1–U8）、`porting/fabric-1.20.1/WIRING-TODO.md`、`scripts/test/TESTING-SPEC.md` §7B。

## §0 范围与红线

1. **只覆盖 `fabric-1.20.1`**。neoforge-26.1.2 的 P-1..P-5 已移交另一会话（`porting/neo-26.1.2/P1-DEFECT-REPAIR-CONTEXT.md`），其残留项 U9 本文只登记不执行。
2. **启动可在本会话内完成（2026-11-02 更新，原「必须交回用户终端」已过时）**。现行事实：沙箱已是 Medium 完整性级别（S-1-16-8192），`run/{server,client}/.fabric` 可直接删除；此前「`java.io.IOException: the jar file ... can't be written`」的真因是 **JDK 17 的 zipfs 在目标 jar 尚不存在时把 readOnly 推成 true**（同 loader jar、同路径，JDK17 `isReadOnly=true` / JDK21 `false`），已由 `fabric-1.20.1/build.gradle` 把 `runClient/runServer/runDatagen` 的 `javaLauncher` 钉到 21 解决（compileJava 仍出 Java 17 字节码）。服务端 `gradlew :fabric-1.20.1:runServer`、客户端 `gradlew :fabric-1.20.1:runClient -PtestSodium=false -PquickplayMultiplayer=127.0.0.1:25565 -PdevUsername=AstralTester` 均可直接跑；客户端重启前须先杀残留客户端 JVM 再删 `run/client/.fabric`。
3. **不改产品源码（已按用户指令部分解禁，2026-11-02）**。静态预检发现「实现根本不存在」的条目一律写 `BLOCKED(原因)` 并进 §5「候选产品缺陷」，由用户裁决后另开批次修；解禁部分：用户 m03068「按照你的计划开始执行实机」之后，实机当场暴露的 fabric 侧缺陷准予就地修复并登记在 §7（已修：`CardTraderTrades` 标签校验时机、村民职业显示名 lang 键）。
4. **不动既有台账结论**：`WIRING-TODO.md` / `ALIGNMENT-AUDIT-2026-11-01.md` 里已过时的历史结论在本表「静态预检」列就地标注「已过时 + 现行证据」，不回写那两份文档。
5. **不 commit / push / 部署 / 不打 tag**；不碰工作树里他人未提交的 649 项改动。
6. 测试生物一律**非亡灵**（pillager / cow）；禁僵尸（原版亡灵在日光下自燃，会把「实体存活」判据污染）。

## §1 四条取证通道

| 代号 | 通道 | 机制 | 能判 | 不能判 |
|---|---|---|---|---|
| `CH-R` | 机判（RCON） | `mt.sh --phase cases --case <json>` → `inject_command` → 服务端 `latest.log` 正则 | 机器行出现 / 命令可执行 / 产物符号在位 | 数值关系（日志断言无比较运算）、客户端观感 |
| `CH-A` | AutoJS | 会话内 `mcp__autojs__eval_js`（fabric 侧 AutoJS 是 `-PautojsTestMod=1` 条件依赖） | 任意运行时状态（槽位尺寸、注册表计数、客户端世界加载） | 不能写进 case JSON —— bash 运行器对 `eval_js/await_state/mcp_status` 恒判 `BLOCKED` |
| `CH-M` | 人工视觉 | 截图 + 人眼；同坐标 / 同 yaw·pitch / 同距离 / 同时间码 | 观感、布局、颜色 | 可复跑判据；不得单独作为验收依据 |
| `CH-S` | 静态预检 | grep / `javap` / 开 jar 核符号 | 提前把「实现不存在」的条目判 BLOCKED | 运行期行为 |

补充：`CH-R` 的日志是**整文件匹配、无时间窗**（`scripts/test/win/lib/fabric.sh` 的 `log_text()`），因此每批前必须轮转 `latest.log`。`live.sh` 已在「实例未在跑」时自动改名 `latest.log.live-<ts>`；实例已在跑则打 `MT_LIVE_ABORT` 并 exit 12（除非 `--allow-stale-log`）。

## §2 环境矩阵

| 轮 | env | 配置 | 覆盖 |
|---|---|---|---|
| R1 | `C1` | 仅服务端 + RCON（`enable-rcon=true` / `rcon.port=25575` / 密码 `astralft`，由 `--phase env --side server` 写入） | LT0 |
| R2 | `C2` | 服务端 + dev 客户端（同版本进服，`run/client` 与 `run/server` 双目录） | LT2 / LT3 / LT4 / LT5 / LT6 |
| R3 | `C3` | `-PtestTrinkets=false`（只 Accessories） | LT1 |
| R4 | `C4` | `-PtestAccessories=false`（只 Trinkets） | LT1 |
| R5 | `C5` | C2 + 旧客户端 jar `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`（放 `run/fabric-1.20.1/mods/` 或对应整合包） | LT7 反向侧 |

**run 根语义**：fabric 是 `fabric-1.20.1/run/<side>`（client / server 两个），**不是**三线的 `run/<版本>`。

**启动前一次性清障（用户终端，必做）**：

```bat
cd /d D:\MCProject\astral_dice_multiloader
rmdir /s /q fabric-1.20.1\run\server\.fabric
rmdir /s /q fabric-1.20.1\run\client\.fabric
```

**起法（用户终端，Git Bash 或 cmd）**：

```bash
# 服务端（RCON 驱动所需）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase launch
# 客户端（C2 需要）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase launch -PdevUsername=Dev
# 收尾（每轮必跑，禁实例驻留）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase stop --force
```

**排期编排（本会话可直接跑，dry-run 先看）**：

```bash
bash scripts/test/win/live.sh --batch LT0 --dry-run          # 只打印命令，不启动进程
bash scripts/test/win/live.sh --batch LT2 --allow-auto       # 非 dry-run 需 --allow-auto
bash scripts/test/win/live.sh --batch all --allow-auto --stop-after
```

## §3 批次与条目

条目四要素顺序固定：**前置 / 驱动 / 判据 / 通道 / 环境**，末尾附**静态预检**（本轮 CH-S 结论）。状态值域：`待验证 / PASS / FAIL(说明) / BLOCKED(原因) / SKIP(原因) / N/A`。

### LT0 冷启动 · 注册面 · 无异常扫描（C1 / CH-R+CH-A / 约 10 分钟）

#### LT0-01 加载器冷启动无异常

- **前置**：`:fabric-1.20.1:remapJar` 产物为最新（当前 `astral_dice-1.5.0-rc.7+fabric_1.20.1.jar`）；`run/server/.fabric` 已清；无他人占用 `run/` 目录。
- **驱动**：用户终端 `mt.sh --side server --phase launch`。
- **判据**：日志出现就绪行 `Done (`；无 `Mixin apply failed` / `InvalidInjectionException` / `NoClassDefFoundError`；无 `crash-reports/crash-*.txt` 新增。
- **通道**：CH-R（log + crash 断言）· **环境**：C1
- **静态预检**：已闭环 —— mixins 32 条 / client 14 条齐备，三座桥（`MobFinalizeSpawnBridgeMixin` / `ExplosionStartBridgeMixin` / `EnderPearlTeleportBridgeMixin`）均在清单内；`mezzconfig` 已于 build.gradle 补 `modRuntimeOnly`（JEI 15.62 的 required 前置，缺则 Loader 解析期直接拒启）。

#### LT0-02 注册面计数对齐主线

- **前置**：LT0-01 PASS。
- **驱动**：CH-A 会话内 `eval_js`（side=server）遍历 `BuiltInRegistries.ITEM / BLOCK / MOB_EFFECT` 统计 `astral_dice:` 前缀数量。
- **判据**：ITEM 153（157 含 4 方块物品）/ BLOCK 4 / MOB_EFFECT 67；与 neoforge-1.21.1 同类计数逐一相等。
- **通道**：CH-A · **环境**：C1
- **静态预检**：已闭环 —— 新 jar 上 `verify_fabric_assets.py` 八项全 PASS（物品 157 / 标签 10-29-10 / 音效 23 / 粒子 2 / 语言 1077×3 / 手册 184 / 创意 156-157 / 饰品 icon 7），`verify_effect_icons.py` 四线 PASS（fabric registered=67 covered=67）。

#### LT0-03 实体类型可召唤

- **前置**：LT0-01 PASS；作弊权限 2。
- **驱动**：`/summon astral_dice:barricade 0 100 0` 与 `/summon astral_dice:repulsion 0 100 0`，随后 `/astralparty killbarricade all` / `killrepulsion all`。
- **判据**：`LT-ENTITY-SWEEP-fabric-1.20.1.json` 全绿（`MT_CASE_RESULT: PASS`）；日志无 `Unknown entity type`。
- **通道**：CH-R · **环境**：C1
- **静态预检**：已闭环 —— `entity/ModEntityTypes.java:42-43` / `:57-58` 注册 id 为 `barricade` / `repulsion`；实体与 `BarricadeAggroHandler` / `RepulsionCreeperGuard` **无 LOGGER 行**，故「存活」只能靠 CH-A（本条目只判可召唤与清扫命令可用）。

#### LT0-04 客户端渲染器注册仅一次

- **前置**：C2 客户端启动。
- **驱动**：CH-A `eval_js`（side=client）读 `EntityRenderers` 之后 `Minecraft.getInstance().getEntityRenderDispatcher()` 对两类实体的 provider 非 null。
- **判据**：无 `IllegalStateException: Entity renderer already registered`；两类实体 provider 非 null。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— `client/EntityRenderers.java:41` / `:45` 各一次注册，注释 :23 已写「只能调用一次」。

#### LT0-05 内嵌前置生效

- **前置**：LT0-01 PASS。
- **驱动**：读 jar 内 `META-INF/jars/` 与 Loader 启动日志的模组列表。
- **判据**：`starengine-fabric-1.20.1-1.1.0.jar` 在内嵌且被加载；整合包 `mods` 下**没有**独立 starengine jar（有则会报重复 mod）。
- **通道**：CH-R + CH-S · **环境**：C1
- **静态预检**：已闭环 —— jar 内 `META-INF/jars/starengine-fabric-1.20.1-1.1.0.jar` 存在；`tools/loom_embedded_jars.py` 负责按 classpath 去重投放 dev run。

#### LT0-06 饰品二选一前置的拒绝路径

- **前置**：能改 dev 开关。
- **驱动**：`-PtestTrinkets=false -PtestAccessories=false` 两后端全关启动。
- **判据**：出现明确的**中文**拒绝文案（`ModCompatibilityCheck#verifyAccessoryProviderOrThrow`），且发生在世界加载前；不是 NPE / 不是静默无槽位。
- **通道**：CH-R · **环境**：C1
- **静态预检**：已闭环 —— `compat/curios/CuriosApi.java:75-76` 两个静态布尔门控 + 调用点类加载守卫（`TrinketBridge.java:33-40`），符合 Fabric「depends 是 AND、没有 OR」的软前置口径。

### LT1 饰品后端三态与槽位（C2/C3/C4 / CH-A+CH-M / 约 30 分钟）

> 背景：Curios 在 fabric 由 `compat/curios/*` 门面等价实现（Accessories 主源 + Trinkets 兜底），12 个功能面里 4 个 P0 本就没用。用户裁决：**validator 闸门保持现状**（不放宽到 forge 语义）。

#### LT1-01 默认双后端并存

- **前置**：`-PtestTrinkets` 与 `-PtestAccessories` 都不传（dev 默认两后端都在）；玩家已装备骰子。
- **驱动**：CH-A `eval_js` 读 `CuriosApi.getCuriosInventory(player).getStacksHandler("dice"/"chip"/"stand")` 的 `getSlots()`。
- **判据**：三槽位尺寸与 neoforge-1.21.1 同装备状态下一一相等；`is_sign_active_locked` 等派生值不入判据。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— `MergedHandler` 两侧同写、`getSlots` 取两侧 max（`compat/curios/CuriosApi.java:300/:334-353`）。

#### LT1-02 仅 Accessories

- **前置**：`-PtestTrinkets=false`（这是用户当前整合包形态）。
- **驱动**：启动 + 开饰品界面 + 装/卸一枚筹码与一张立牌。
- **判据**：槽位显示正常、图标不空白（`AccessoriesClientIconCheck` 不报警）；装/卸触发 `onEquip/onUnequip`。
- **通道**：CH-A + CH-M · **环境**：C3
- **静态预检**：已闭环 —— Accessories 从 `minecraft:blocks` 图集取 sprite，故图标必须放 `textures/gui/slot/` 且被图集收录（已迁）。

#### LT1-03 仅 Trinkets

- **前置**：`-PtestAccessories=false`。
- **驱动**：同 LT1-02。
- **判据**：同 LT1-02；且 `trinkets:tag` 镜像引用最终仍读 `curios:` 标签（单一事实源）。
- **通道**：CH-A + CH-M · **环境**：C4
- **静态预检**：已闭环 —— `data/trinkets/slots/astral_dice/*.json` 用 `trinkets:tag`，其 tag 文件为 `{"values":["#curios:stand"]}` 镜像。

#### LT1-04 换骰子即时 resize（U3）

- **前置**：骰子槽已装一枚 T1 骰子（4 格卡牌容量）；背包有 T4 骰子。
- **驱动**：卸下 T1 装上 T4，同一 tick 内开卡牌界面。
- **判据**：卡牌格数即时从 4 变 12（`DiceTier.cardSlots()` 口径），无需重登；槽位本身尺寸不变（cardSlots 是卡牌栏，不是饰品槽）。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环且**已按主线改判** —— `DiceTier` 为 4 分量 record，`DiceCurioItem.java:60` 读 `tier.cardSlots()`，13 条 `new DiceTier` 与 forge 逐字节相同（用户 m01840 裁决「以最新主线为准」）。

#### LT1-05 槽位收缩归还物品（U4）

- **前置**：筹码槽有 ≥1 件物；能改变槽位尺寸的载体在手。
- **驱动**：触发槽位收缩（卸下带尺寸修饰的骰子）。
- **判据**：被挤出的物品进玩家背包或掉落在地，**不凭空消失**；`setStackInSlot(i, EMPTY)` 后确实有 `player.drop` 分支。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— `item/dice/DiceCurioItem.java:211-245` 与 forge 同逻辑（`forceRemove=false` 抬到最靠后非空槽 +1；`true` 时清槽并 `drop`），纯 `IItemHandler` 语义与后端无关。

#### LT1-06 蹲右键自动装备（U5）

- **前置**：未装备骰子；手持筹码/立牌。
- **驱动**：蹲 + 右键。
- **判据**：未装备骰子时非 dice 槽**拒绝**（提示而非静默）；已装备骰子时落到第一个空槽并 `shrink(1)`；重复物品被拒。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环 —— `item/CurioSlotUtil.java:73 tryAutoEquip`（:78 闸门 / :88 拒重复 / :100-106 落位），三线同构。注：三线都没有「拾取自动装备」，不要把这句写进判据。

#### LT1-07 死亡不掉落（U6）

- **前置**：装备骰子 + 至少一枚筹码；`keepInventory` 关。
- **驱动**：`/kill` 自杀后重生。
- **判据**：`CurioDeathKeepHandler` 保留的物品在重生后仍在原位；未受保护物品正常掉落。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— `event/CurioDeathKeepHandler` 已在 `AstralDiceMod.java` 登记进 LoaderBus（无 `EventBusSubscriber` 等价物，漏登记才不会报错只静默）。

#### LT1-08 validator 闸门差异（有意保留）

- **前置**：装一个**第三方**实现饰品接口的物品（或自写测试物品）。
- **驱动**：尝试放入 chip/stand 槽。
- **判据**：fabric **拒绝**，forge 同物品**接受** —— 这是用户 2026-11-01 裁决「保持现状」的有意差异，判据是「差异确实存在且仅存在于非自家物品」。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— `compat/curios/AccessoriesCompat.java:137` 注册谓词 `astral_dice:curio_slot`，`validateSlot`(:164-171) 读 `curios:<slot>` 标签。自家物品全在标签内 ⇒ 无差异。

#### LT1-09 槽位图标与槽名（客户端）

- **前置**：开饰品界面。
- **驱动**：截图三个槽位 + tooltip。
- **判据**：三槽图标非紫黑方块；槽名走 `accessories.slot.*` / `trinkets.slot.astral_dice.*` 本地化键（这 7 个键是 fabric 独有、必须保留）。
- **通道**：CH-M · **环境**：C2
- **静态预检**：已闭环 —— 三语键集 1077×3 完全一致，fabric 独有键只剩这 7 个运行时槽名键。

### LT2 出牌锁 · 骰战 · 防御折算（C2 / CH-R+CH-A / 约 25 分钟）

#### LT2-01 dump 基线

- **前置**：dev 客户端已进服（`/astralparty dump @a` 的 `@a` 要解析到玩家，否则命令报错）。
- **驱动**：`LT-DUMP-BASELINE-fabric-1.20.1.json`（`/astralparty dump @a` → wait 500）。
- **判据**：`APDUMP|HEAD|`、`LOCKRAW|game_time/\d+`、`LOCKRAW|max_allowed/\d+`、`SIGN|sign_active_cooldown_end=\d+`、`SIGN|sign_active_max_cooldown=\d+`、`PENDING|source=` 全部命中；无 crash；mixin 日志无异常。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— 命令字面量 `astralparty`(:130) / dump 五组 `HEAD`(:160) `LOCKRAW`(:162) `LOCKDERIVED`(:164) `SIGN`(:166) `EFFECTS`(:168) `PENDING`(:170)；双通道 `sendSuccess` + `LOGGER.info` 使 latest.log 可 grep。

#### LT2-02 resetcardlock 幂等归零

- **前置**：LT2-01 PASS；先打出至少一张效果牌制造非零冷却。
- **驱动**：`/astralparty resetcardlock` → dump。
- **判据**：`effect_card_play_count=0`、`effect_card_bonus_plays=0`、`effect_card_cooldown_end=0` 三条同时为 0。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— `EffectCardPeriod.forceResetRound` 无条件置零（javadoc 明写「不读旧值、幂等」）；`resetcardcooldown` 是同一子命令的别名。

#### LT2-03 效果清除只清 pending

- **前置**：LT2-01 PASS。
- **驱动**：`LT-CLEAREFFECT-BY-DUMP-fabric-1.20.1.json`（`/effect give @a astral_dice:lingling_surge 60 0` → dump → `/astralparty cleareffect`）。
- **判据**：给效果后 `APDUMP|EFFECTS|effect=astral_dice:lingling_surge|amplifier=0|duration=\d+` 命中；清除后应消失这一条**不能**用 `absent`（整文件匹配），改由 CH-A 读玩家效果表复核。
- **通道**：CH-R + CH-A · **环境**：C2
- **静态预检**：已闭环 —— `clearcardeffect` 只清 `EffectCardPeriod.effectPendingEffects()`，不动立牌主动效果（两者别混判）。

#### LT2-04 骰战伤害区间（U2）

- **前置**：双方都装骰子；对手为非亡灵生物（pillager）。
- **驱动**：CH-A 读攻击前后 `getHealth()` 差；重复 10 次取分布。
- **判据**：单次伤害落在主线同装备状态的区间内（不出现 ×2 倍系统偏差）；`astral_dice:extra_damage` 通道生效。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— `DamageCardFxScheduler` 之外，`combat/DiceCombatModifiers.java:436` 命中前快照 + `:452-454` 走额外伤害通道。

#### LT2-05 防御折算 1:1（U2 核心，旧台账已过时）

- **前置**：装有防御来源的立牌/筹码。
- **驱动**：CH-A 读 `DiceCombatModifiers.armorValueForDefensePoints` 与玩家实际 `getArmorValue()`。
- **判据**：`PLAYER_ARMOR_PER_DEFENSE = 1.0 / PLAYER_ARMOR_COEF`、`MOB_ARMOR_PER_DEFENSE = 1.0 / MOB_ARMOR_COEF`，实际护甲 = 防御点 × 该系数（**不是**写死 ×2.0）。
- **通道**：CH-A · **环境**：C2
- **静态预检**：**已过时结论纠正** —— 旧台账说「fabric 仍 ×2.0、类里没这两个常量」已不成立：常量在 `combat/DiceCombatModifiers.java:197/:199`，消费点 `MotoHelmetChipItem:20-21` / `ObsidianDiceItem:29` / `DorothySignItem:115` / `JillSignItem:179` / `EventEffects:149` 全部改指常量。

#### LT2-06 伤害数字只发造成者

- **前置**：两人同时打同一目标。
- **驱动**：CH-A 分别记录两客户端收到的伤害数字事件。
- **判据**：数字只对**造成者**可见，旁观者无该行。
- **通道**：CH-A · **环境**：C2
- **静态预检**：无法静态判定（接收端分支），列为待实机。

#### LT2-07 目标选择取消（token 下单）

- **前置**：手持需要选目标的效果牌。
- **驱动**：开始选择 → 服务端发 `TargetSelectCloseMessage`（token）→ 客户端关闭。
- **判据**：客户端界面确实关闭且不误伤后续选卡；消息 id 30 不与既有 0–29 冲突。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环 —— `network/ModNetwork.java:123-128` 注册 + `:464-503` 单 VarInt 载荷；id 追加末尾（置中间会让 22 条顺移、旧端错包）。

### LT3 事件体系（C2 / CH-R+CH-A+CH-M / 约 35 分钟）

> 机器行家族（全仓实测频次）：`AP_EVENT_EFFECT` 13 / `AP_CARD_CHOICE` 13 / `AP_EVENT_CHOICE` 10 / `AP_EVENT_ANIM` 7 / `AP_EVENT_DRAWER` 6 / `AP_EVENT_TIMER` 4 / `AP_EVENT_GATE` 3 / `AP_EVENT_CARD` 2 + `AP_EVENT_CARD_CLIENT` 2 / `AP_EVENT_DRAW` 2 / 其余各 1。

#### LT3-01 抽卡与卡面显示

- **前置**：事件体系开启（`/astralparty` 无开关，靠自然触发或测试用例）。
- **驱动**：触发一次抽卡 → 截图卡面。
- **判据**：`AP_EVENT_DRAW` / `AP_EVENT_CARD`（服务端）与 `AP_EVENT_CARD_CLIENT` show/hide 各出现；卡面动画 38 tick（`CARD_ANIM=28 + SETTLE=10`）。
- **通道**：CH-R + CH-M · **环境**：C2
- **静态预检**：**纠正旧文档** —— 文档里的「39 tick 延迟」不存在，真实为 38（`DELAY_TICKS=38`）。

#### LT3-02 选卡界面与选择同步

- **前置**：LT3-01 触发过一次抽卡。
- **驱动**：在界面上选一张 → 确认。
- **判据**：`AP_CARD_CHOICE` / `AP_EVENT_CHOICE` 成对出现；选择结果落库；消息 id 20/21（Confirm/Close）与声明一致。
- **通道**：CH-R + CH-M · **环境**：C2
- **静态预检**：已闭环 —— 两个消息类内过时注释（原写 16/17）已改为 20/21；register 共 31 条（id 0–30）。

#### LT3-03 事件计时器与硬间隔闸门

- **前置**：能连续触发两次同类事件。
- **驱动**：快速连续触发。
- **判据**：第二次被 `AP_EVENT_GATE`（唯一闸门在 `AstralEventDispatcher:113` 的 ITEM_USE）拦下；**不得**在 `EventChoiceManager` 恢复预判（否则双占位 + 界面已选却静默拒绝）。
- **通道**：CH-R · **环境**：C2
- **静态预检**：**旧口径已过时** —— `EventChoiceManager:150-154` 与 :228 注释写明 2026-10-29 起不再查 `GlobalEventGate`；`GlobalEventGate.canStart` 的 javadoc 是注释残留。

#### LT3-04 效果结算与保护

- **前置**：事件结算一次。
- **驱动**：读 dump 的 `PENDING` 组。
- **判据**：`AP_EVENT_EFFECT` 出现；`PENDING|source=<id>|effect=|is_active=|remaining=` 四键完整。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— `appendEffects` / PENDING 组格式固定；`EventEffects` 已在 LoaderBus 登记。

#### LT3-05 动画窗锁（U7）

- **前置**：事件卡动画播放中。
- **驱动**：动画期内使用效果牌（手持左键 / 右键起选）。
- **判据**：`AP_EVENT_ANIM: block:<player>:effect_card_selector` 出现；无第二次选择界面、无 token 消耗；`tickHeldSelector` 静默返回不刷屏。
- **通道**：CH-R + CH-M · **环境**：C2
- **静态预检**：已闭环（本会话补齐）—— `item/card/BaseEffectCardItem.java` 三处拦截（`tickHeldSelector` / `tryStartSelector` / `useViaSelector`），字节码内可见 `AP_EVENT_ANIM` 与 `EventAnimationLock`。

#### LT3-06 抽奖盒冷却（U8 关联）

- **前置**：能使用抽奖盒一次。
- **驱动**：使用一次 → 立刻再用。
- **判据**：`AP_EVENT_DRAWER` 出现；冷却键 `EVENT_DRAWER_COOLDOWN_UNTIL` 与 `EVENT_DRAWER_BLANK_INSERT_AT` 均落档并 `.sync()`；`BLANK_INSERT_COOLDOWN_TICKS=2400`（只清购买冷却、不刷新内容）。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— `component/ModAttachments.java:126` / `:141` 两键存在，`:1413` 已把 cooldown 加入 `SYNCED_KEYS`。

#### LT3-07 HUD 事件卡覆盖层

- **前置**：事件卡显示中。
- **驱动**：截图（准心上方区域）。
- **判据**：覆盖层出现在 `CROSSHAIR` 之上、被 F1 正常隐藏；无 z-fighting。
- **通道**：CH-M · **环境**：C2
- **静态预检**：已闭环 —— `client/ModClientEvents.java:44` 的 `registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "event_card", …)`。

### LT4 商店 · 村民 · 交易（C2 / CH-M+CH-A / 约 30 分钟）

#### LT4-01 卡牌商店开窗与扣款

- **前置**：玩家持有足够卡牌货币；商店方块在场（或命令开启）。
- **驱动**：右键卡牌商店 → 购买一张 → 拖拽并栈。
- **判据**：界面开、购买扣款正确、并栈后数量与价格同步；服务端权威（客户端伪造不生效）。
- **通道**：CH-M + CH-A · **环境**：C2
- **静态预检**：已闭环 —— 37 类商店子系统全部搬迁完成（`shop/11 + screen/4 + block/3 + client/2 + network/4 + trade/2 + world/2 + item/chip/6 + recipe/condition/1`）；`planPlacement/canAcceptDrop/resolvePlacedItem` 三处共用同一转换逻辑（禁各写一份）。

#### LT4-02 筹码商店与刷新

- **前置**：同 LT4-01。
- **驱动**：开筹码商店 → 刷新货架 → 购买。
- **判据**：`ChipShopBuyPayload` / `ChipShopDenyPayload` / `ChipShopRefreshPayload` 三条消息在日志或 CH-A 探针中可见；拒绝路径给明确原因不静默。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环 —— 三条 payload 已注册（`ModNetwork.java:90` 附近），序列化按 1.20.1 手写 `FriendlyByteBuf` 口径。

#### LT4-03 卡牌交易员村民

- **前置**：村庄内有卡牌商店工作站方块。
- **驱动**：`/summon villager` → `/data merge` 或直接放置工作站让其转职。
- **判据**：转职为 `card_trader`；交易表填充（非空、含卡牌）；工作方块认对（不是另一个职业的工作站）。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环 —— `world/ModVillagerProfessions` / `ModPoiTypes` 在 `AstralDiceMod.java:68-71` 的 `bootstrap()` 完成注册；两条职业专用标签保留共享标签 `minecraft:acquirable_job_site`（否则村民抢别人工作站）。

#### LT4-04 筹码商人村民

- **前置**：同 LT4-03（筹码商店方块）。
- **驱动**：同 LT4-03。
- **判据**：转职为 `chip_trader`；交易表来自 `ChipTraderTrades`；两职业**不共用**同一工作方块。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环 —— `new VillagerProfession("chip_trader", holder -> holder.is(CHIP_SHOP_KEY), holder -> holder.is(CHIP_TRADER_JOB_SITES), …)`。

#### LT4-05 交易表注入链路

- **前置**：LT4-03/04 通过。
- **驱动**：打开村民交易界面。
- **判据**：交易条目含主线定义的兑换条目；`EmeraldDiceTrade` 折扣口径 = `Math.max(1, stack.getCount())`（不是 `floor(count*0.8)`）；兑换条目过滤 `isEmeraldToStarCoin` 生效（防止把星币兑换洗成绿宝石）。
- **通道**：CH-A · **环境**：C2
- **静态预检（2026-11-02 05:20 订正）**：**已闭环** —— 此前记「`isEmeraldToStarCoin` / `rawCostA` / `MerchantOfferRawAccess` / `filteredFor` 在 fabric grep 为 0」是过时结论：四者均已存在且接线完整（`trade/EmeraldDiceTrade.java:117/132/150/168`、`trade/MerchantOfferRawAccess.java`，调用点 `mixin/trade/MerchantContainerMixin.java:51`、`mixin/trade/MerchantMenuMixin.java:41`、`mixin/trade/ServerPlayerMixin.java:39`）；折扣为 1:1 等值替换（不是 0.8）。**仍需实机**：佩戴绿宝石骰子后兑换行是否被剔除、费用是否显示为星币。

#### LT4-06 JEI 两个商店类别

- **前置**：客户端装 JEI 15.62.0.216。
- **驱动**：打开 JEI 查卡牌商店 / 筹码商店类别。
- **判据**：两个类别都能打开、配方条目不崩（Loom 1.17.21 下 JEI 已进编译期，8 个 `compat/jei/**` 类已在产物内）。
- **通道**：CH-M · **环境**：C2
- **静态预检**：已闭环 —— jar 内 `compat/jei` 9 个 class；`fabric.mod.json` 的 `jei_mod_plugin` entrypoint 在位。

#### LT4-07 Jade 商店冷却显示

- **前置**：客户端装 Jade。
- **驱动**：准心看向商店方块。
- **判据**：冷却剩余时间显示；无 Jade 时**静默跳过不崩**（mixin 配置 `required:false`）。
- **通道**：CH-M · **环境**：C2
- **静态预检**：已闭环 —— `compat/jade/` 3 类 + `fabric.mod.json` 的 `jade` entrypoint；`mixin/jade/**` 编进主 mixin 配置且 `required:false`。

### LT5 立牌（C2 / CH-A+CH-M / 约 40 分钟）

#### LT5-01 七位新立牌注册与获取

- **前置**：创造模式。
- **驱动**：在创造栏搜索 7 位新立牌。
- **判据**：`alana_sign / ame_sign / deepseek_sign / dorothy_sign / jill_sign / kangel_sign / lingling_sign` 七件均在创造栏与 `curios:stand` 标签内。
- **通道**：CH-A + CH-M · **环境**：C2
- **静态预检**：已闭环 —— 物品数已达 157（与两条主线相等）；`verify_fabric_assets.py` 创意栏 156/157（`repulsion` 已知不阻塞）；标签 `curios:stand` 已补 7 条（旧快照缺 13 行的问题已随 B 批收口）。

#### LT5-02 立牌主动技与冷却

- **前置**：装备任一立牌，且骰子已装备（总闸门 `CurioSlotUtil.hasDiceEquipped`）。
- **驱动**：按立牌键（`ACTIVATE_SIGN_KEY`）→ 再按一次看冷却。
- **判据**：主动技生效；冷却进入 `SIGN|sign_active_cooldown_end`；`sign_active_max_cooldown` 与之匹配；重复按被拒。
- **通道**：CH-A + CH-R · **环境**：C2
- **静态预检**：已闭环 —— `item/CurioSlotUtil.java:172` 总闸门；`SignAttributeUtil` / `SignActiveLockMachine` 已搬迁。

#### LT5-03 冷却完成命令

- **前置**：立牌处于冷却中。
- **驱动**：`LT-SIGN-COOLDOWN-FINISH-fabric-1.20.1.json`（`/astralparty finishsigncooldown` → dump）。
- **判据**：命令成功、`SIGN` 两键存在；「冷却已到期」的数值关系属派生判定 → 转 CH-A。
- **通道**：CH-R + CH-A · **环境**：C2
- **静态预检**：已闭环 —— `FINISH_COOLDOWN_LITERAL = "finishsigncooldown"`(:134)，目标可选 `EntityArgument.players()`，省略 = 执行者自己。

#### LT5-04 半透明渲染

- **前置**：装备任意立牌，背后有实体/地形。
- **驱动**：截图（正对、背光）。
- **判据**：立牌模型半透明可见、无深度写入错乱；`SignTranslucencyMixin` 生效（无 `Mixin apply failed`）。
- **通道**：CH-M + CH-R · **环境**：C2
- **静态预检**：已闭环 —— `client.SignTranslucencyMixin` 在 mixin 配置的 client 段内。

#### LT5-05 立牌属性与效果叠加

- **前置**：同时装备两枚带属性的立牌。
- **驱动**：读 `getAttributeModifiers` / 实际护甲值。
- **判据**：两个属性修饰器并存且键不同（不互相覆盖）；卸下后干净移除。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— 修饰器 key 在 1.20.1 为 `UUID`（Accessories/Trinkets 均为 `Map<UUID, AttributeModifier>` + `update()`）。

#### LT5-06 免疫 HARMFUL 效果

- **前置**：装备带「免疫负面效果」描述的立牌。
- **驱动**：`/effect give <player> minecraft:poison 20 0`。
- **判据**：效果被拦下（玩家身上无该效果），且有拒绝提示。
- **通道**：CH-A · **环境**：C2
- **静态预检**：**BLOCKED(实现缺失)** —— `MobEffectEvent.Applicable` 在 fabric 全仓**无派发点**（`platform/event/entity/living/MobEffectEvent.java:95` 只有注释提到 `LivingEntity#canBeAffected`；`event/NewSignEvents.java:39-43` javadoc 自述本线不派发）。修法（未做）：mixin `LivingEntity#canBeAffected` 返回 false 时派发等价事件。⇒ 实机只会看到「效果照常生效」，属候选产品缺陷。

#### LT5-07 立牌钩子派发（分发器已删）

- **前置**：分别装备 Alana（赐福）与 Fen（溅射费用）立牌。
- **驱动**：触发对应战斗事件（打出 5 张牌 / 溅射命中）。
- **判据**：钩子**恰好触发一次**（不得直调 + 分发双触发）。
- **通道**：CH-A + CH-R · **环境**：C2
- **静态预检**：已闭环 —— `SignHookDispatcher` 全仓 0 命中（整类已删），改为直调：`combat/DiceCombatEvents.java:596 AlanaSignItem.onBlessingTriggered(player)` / `:593 FenSignItem` / `:601 HealingManager`。

#### LT5-08 立牌触发时隐藏玩家渲染（NancyLu）

- **前置**：装备南希露立牌，使用主动技。
- **驱动**：截图看自身模型是否被隐藏。
- **判据**：主动技期间自身渲染被取消（`NancyLuClientEvents` 取消整次 `PlayerRenderer.render` 会**连带取消 CuriosLayer`**），结束后恢复。
- **通道**：CH-M · **环境**：C2
- **静态预检**：无法静态判定（渲染侧链），列为待实机；已知风险见 `client/NancyLuClientEvents.java:17-23`。

### LT6 轨道炮 / 激光 FX 四线对照（C2 + 两 P0 线各一轮 / CH-M / 约 30 分钟）

#### LT6-01 光束由真实渲染器绘制

- **前置**：装激光/轨道炮筹码；C2 双端。
- **驱动**：开火，同角度截图；同时在 neoforge-1.21.1 与 forge-1.20.1 各截一张同参数图。
- **判据**：fabric 出现**细直光柱**（不是旧版 84 枚尘粒子柱）；三线观感一致；`BeamFxPayload` 有下发（日志/探针）。
- **通道**：CH-M（+ CH-A 佐证） · **环境**：C2 + P0×2
- **静态预检**：已闭环（本会话改）—— `combat/DamageCardFxScheduler.java` 的 `drawBeam` 已替换为 `sendBeamFx`，构造 `new BeamFxPayload(pos.x, headY+BEAM_HEIGHT, pos.z, pos.x, headY, pos.z, color, width, BEAM_TTL_TICKS)` 并 `ModNetwork.sendToPlayersTrackingEntity`；四线 `sendBeamFx` 各 2 处、`drawBeam` 各 0 处。

#### LT6-02 颜色与宽度常量

- **前置**：LT6-01 有截图。
- **驱动**：像素取样。
- **判据**：激光色 `0xFF2626`、信标色 `0xFFCF4A`；宽度 `LASER_WIDTH=0.05F` / `BEACON_WIDTH=0.45F`；信标纵向平铺 `BEACON_V_TILE=8.0`。
- **通道**：CH-M · **环境**：C2
- **静态预检**：已闭环 —— `client/BeamFxRenderer` 四线常量逐字相同（含 `Stage.AFTER_ENTITIES`、`ADDITIVE_TRANSPARENCY+LEQUAL+COLOR_WRITE+NO_CULL+LIGHTMAP+OVERLAY`）。

#### LT6-03 连续开火去重与 TTL

- **前置**：连续开火 ≥3 次。
- **驱动**：截图 / 探针读光束数量。
- **判据**：光束按 `BEAM_TTL_TICKS=3` 自动过期，不无限堆叠；`BEAM_TICKS=20`、`BEAM_HEIGHT=96.0D` 与主线一致。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— 常量表与 neo 主基准逐字相同（`BEAM_TICKS=20` / `BEAM_HEIGHT=96.0D` / `BEAM_TTL_TICKS=3`）。

#### LT6-04 轨道炮落雷与 AOE

- **前置**：充能满（`CHARGE_REQUIRED=6`）。
- **驱动**：开火 → 等 `STRIKE_DELAY_TICKS=20`。
- **判据**：半径 `AOE_RADIUS=3` 内实体受伤；伤害 = `max(STRIKE_DAMAGE_MIN, base + 比例×攻)`；落雷伤害走 `RailgunBolts` 补丁（1.20.1 `LightningBolt` 无 `setDamage`）。
- **通道**：CH-A · **环境**：C2
- **静态预检**：已闭环 —— 常量四线逐字相同；`mixin/EntityThunderHitMixin.java:61` 用 `RailgunBolts.damageOf(bolt)` 取补丁值。

#### LT6-05 26.1.2 死常量不参与判据

- **前置**：无。
- **驱动**：静态比对。
- **判据**：`BEAM_PARTICLES=84` 仅在 26.1.2 存在且**无调用**；不得据此判 fabric「观感不一致」。
- **通道**：CH-S · **环境**：N/A
- **静态预检**：已确认 —— `neoforge-26.1.2/.../DamageCardFxScheduler.java:114` 是未使用死常量。

### LT7 网络与版本闸门（C2/C5 / CH-R / 约 15 分钟）

#### LT7-01 同版本握手 OK

- **前置**：C2 客户端与服务端均为 `1.5.0-rc.7+fabric_1.20.1`。
- **驱动**：`LT-VERSION-GATE-LIVE-fabric-1.20.1.json`。
- **判据**：`AP_NET_VERSION_JOIN: player=`、`AP_NET_VERSION_OK: player=` 出现；`absent` `AP_NET_VERSION_SELFCHECK_FAIL`。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— 握手 21 处机器行分三族（OK / REJECT / ABSENT），`HANDSHAKE_DEADLINE_TICKS=200`。

#### LT7-02 旧客户端被拒（C5）

- **前置**：客户端 `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`。
- **驱动**：进服。
- **判据**：出现 `AP_NET_VERSION_REJECT` + `_REJECT_DETAIL`（**唯一**断开场景）并有可读文案；不是 NPE、不是静默退出。
- **通道**：CH-R + CH-M · **环境**：C5
- **静态预检**：已闭环 —— `disconnect` 全类只在 :225 与 :286 两处，均紧贴 `REJECT` 判定。

#### LT7-03 缺失组件给不出握手的处理

- **前置**：对端未安装握手组件（或超时不回）。
- **驱动**：观察日志。
- **判据**：`AP_NET_VERSION_ABSENT` + `action=give_up`；**不**踢人；**不**出现旧口径 `REJECT remote=ABSENT`。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— `VersionHandshake.java` 268→361 行：`rejectAbsent`→`giveUpAbsent`(:239-247)，超时只 `it.remove()` + WARN（:190-194 / :255-263）。

#### LT7-04 消息注册 id 连续无空洞

- **前置**：无。
- **驱动**：CH-S 静态读 `network/ModNetwork.java`。
- **判据**：注册总数 31（id 0–30），与 forge 的相对顺序（16/17/18/19/20/27 段）一致；追加式分配，不插队。
- **通道**：CH-S · **环境**：N/A
- **静态预检**：已闭环 —— :90 CardShopDeny / :93,:95 CardChoice Confirm·Close / :102 EventCard / :104-112 EventChoice 五条 / :119,:121 VersionProbe·Reply / :127 TargetSelectClose。

#### LT7-05 客户端网络包不 NPE

- **前置**：C2 客户端进服 5 分钟。
- **驱动**：扫客户端日志。
- **判据**：无 `NullPointerException` / `ClassCastException` 于 `network.*Message.handle`；`FMLEnvironment.dist == Dist.CLIENT` 分支正确（服务端不得调客户端渲染）。
- **通道**：CH-R · **环境**：C2
- **静态预检**：已闭环 —— `BeamFxPayload.handle` 有 dist 守卫，不守卫则在服务端调 `Minecraft.getInstance()` 直接崩。

## §4 判据词典（可锚 / 禁锚）

**可锚（原始值）**

| 组 | 行格式 | 键 |
|---|---|---|
| `HEAD` | `APDUMP\|HEAD\|…` | — |
| `LOCKRAW` | `APDUMP\|LOCKRAW\|<键>=<值>` | `game_time`、`effect_card_cooldown_end`、`effect_card_play_count`、`effect_card_bonus_plays`、`living_page_cycle_bonus`、`max_allowed`、`remaining_block_ticks` |
| `SIGN` | `APDUMP\|SIGN\|<键>=<值>` | `sign_active_lock_sign`、`sign_active_lock_end`、`sign_active_reduction_pool`、`sign_active_lock_grace_end`、`sign_active_lock_played`、`sign_active_lock_last_seen`、`sign_active_cooldown_end`、`sign_active_max_cooldown`、`sign_ready_type`、`sign_ready_expire` |
| `EFFECTS` | `APDUMP\|EFFECTS\|effect=<全限定 id>\|amplifier=<0 基>\|duration=<tick>` | — |
| `PENDING` | `APDUMP\|PENDING\|source=<id>\|effect=<id\|none>\|is_active=<bool>\|remaining=<tick>` | — |
| 版本闸门 | `AP_NET_VERSION_(JOIN|OK|REJECT|REJECT_DETAIL|ABSENT|ABSENT_DETAIL|SELFCHECK_FAIL)` | — |

**禁锚（派生口径，行尾带 `|assert=forbidden`）**：`APDUMP|LOCKDERIVED|is_burst_full`、`…|is_cooldown_active`、`…|is_effect_pending`、`…|is_blocked`、`APDUMP|SIGN|is_sign_active_locked`。它们是判定入口而不是原始值，拿它当落点等于把「实现自己的 bug」当成预期。

**通道边界**：`log` 断言只能「命中/不命中」，没有数值比较；要比较数值必须走 CH-A（`eval_js` 里自己算）或命令回显。`absent` **不能**表达「某状态已被清除」——历史行还在文件里。

## §5 失败归因与记录

1. **一眼判方向**：客户端崩 → 看 `fabric-1.20.1/run/client/logs/latest.log` 与 `crash-reports`；服务端崩 → `run/server/` 同名；**别在错误那一侧空找**。
2. **Mixin 类失败**：先看 `Mixin apply failed` 的目标类，再对 `astral_dice.mixins.json` 的 32/14 条清单；`require` 语义是「**最少**命中次数」，写大了编译不报错、只在目标类加载时抛 `InjectionError`。
3. **静默失效**：无报错但没效果 ⇒ 优先怀疑「LoaderBus 没登记」（fabric 无 `EventBusSubscriber` 等价物，漏登记只静默）与「事件根本无派发点」（如 LT5-06）。
4. **记录格式**：每条失败写「条目 ID / 环境 / 时间码 / 日志末 20 行 / 与主线期望值的差」；报告落 `scripts/test/reports/<date>-fabric-1.5.0-live/`。
5. **失败不中止整轮**：机判条目由 `live.sh` 在首个非 0 时打 `MT_LIVE_ABORT` 并停该批（避免脏状态下误判后续）——这是刻意的，不是缺陷。

## §6 执行顺序与预算

| 序 | 轮次 | env | 批次 | 净耗时（估） | 前置 |
|---|---|---|---|---|---|
| 1 | R1 | C1 | LT0 | 10 min | 清障 + 用户起服务端 |
| 2 | R2 | C2 | LT2 → LT3 → LT4 → LT5 | 130 min | R1 PASS；服务端 + 客户端 |
| 3 | R3 | C3 | LT1（仅 Accessories） | 20 min | 换 `-PtestTrinkets=false` 重启 |
| 4 | R4 | C4 | LT1（仅 Trinkets） | 20 min | 换 `-PtestAccessories=false` 重启 |
| 5 | R5 | C5 | LT7 反向 | 15 min | 换旧客户端 jar |
| 6 | R6–R8 | — | LT6 三线对照 | 30 min | 各线一轮截图 |

合计约 **3.7 小时**净执行，可分次下单。每轮收尾**必须**：

```bash
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase stop --force
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase stop --force
```

需要干净世界时加 `--purge-saves`（只对白名单世界生效）。

## 附：本轮静态预检的 2 个未闭环缺口（候选产品缺陷，需用户裁决）

1. **`MobEffectEvent.Applicable` 无派发点** ⇒ 立牌「免疫 HARMFUL」整条特性静默无效（LT5-06 会 FAIL）。修法：mixin `LivingEntity#canBeAffected` 返回 false 时派发等价事件 + `NewSignEvents` 订阅。属**代码批次**，本轮不改。
2. **`component/SignAttachments.java` 仍与 `ModAttachments` 并存** ⇒ 玩家数据键双事实源（非实机可判）。修法：收尾批次并回 `ModAttachments` 并删该类。属**代码批次**，本轮不改。

## §7 执行记录

### R1（2026-11-02 01:29–01:56）— LT0 冷启动 / 注册面 / 机判 6 条

| 条目 | 通道 | 环境 | 结果 | 证据（时间码 / 机器行） |
|---|---|---|---|---|
| LT0-01 加载器无异常 | CH-R | C1 | PASS | `Loading 73 mods` → `Done (1.493s)! For help` → `RCON running on 0.0.0.0:25575`，全程无 crash-report |
| LT0-02 实体注册面 | CH-R | C1 | PASS | `Summoned new Barricade` / `Summoned new Reinforced Repulsion`；`killbarricade all` → `Cleared 0 barricade(s)`（0 因实体在 0 100 0、区块未加载，属预期） |
| LT0-03 玩家数据 dump | CH-R | C2 | PASS | `APDUMP` HEAD/LOCKRAW 两族机器行有值（`AstralTester=43e4e50b-...`、`game_time=...`） |
| LT0-04 出牌锁重置 | CH-R | C2 | PASS | `Reset the play lock for 1 player(s)` |
| LT0-05 效果清空 | CH-R | C2 | PASS | `Applied effect Energy Surge to AstralTester` → `Cleared 1 Astral Dice effect(s) across 1 player(s)` |
| LT0-06 立牌冷却完成 | CH-R | C2 | PASS | `Finished the sign active-skill cooldown for 1 player(s)` |
| LT0-07 版本闸门 | CH-R | C2 | PASS | `LT-VERSION-GATE-LIVE` 全绿 |

**前置教训**：7 条里 5 条依赖真玩家（`/astralparty dump @a` 无玩家时回 `No player was found`），因此实测必须先起 dev 客户端并进服；`live.sh` 会在 RCON 就绪后等 `/list` 玩家最多 120 s。

**本轮修的缺陷（已复验）**：`trade/CardTraderTrades` 的 `CardTradeFilter.validateExclusiveTag()` 原挂在 `register()`（mod init，早于 datapack 装载）⇒ `astral_dice:is_exclusive` 恒解析出 0 张，服务端 01:29:22 与客户端 01:40:55 各打一次假 ERROR「专属牌排除判据当前是空转」。改挂 `ServerLifecycleEvents.SERVER_STARTED` 后服务端回 `专属牌标签 astral_dice:is_exclusive 解析出 6 张物品。`，客户端不再打该行。

**未归零的客户端 ERROR（待裁决）**：`[01:54:24] [Render thread/ERROR] (SyncEntireContainer) Unable to Sync Container Data for a given Entity as its Capability is null on the Client! [EntityId: 3]` —— 进服瞬间单次，疑似 Accessories 容器同步；LT1 的「仅 Trinkets」一轮可作对照（若该 ERROR 不出现即可定位到 Accessories 侧）。

### 剩余批次

| 批次 | 覆盖 | 状态 |
|---|---|---|
| LT1 | 饰品后端三态（双开 / 仅 Accessories / 仅 Trinkets）与槽位尺寸 | **PASS（2026-11-02 02:17–02:29 三态；05:20–05:35 补数值取证）** 三态各跑 5 条机判用例全绿；槽位数值已用 AutoJS 实测（T4 骰子 4 格 / 卸下防御式保留 1 格 / 强制复位归 0 并退回背包）；其间发现并修掉 **筹码栏缩容崩服**（见 §7「LT1 追加」） |
| LT3 | 事件体系 UI / 计时 / 动画窗 | 待执行 |
| LT4 | 商店方块 / 村民 / 交易 | **部分 PASS（2026-11-02 04:50–05:20）** LT4-03/04 实机通过（职业 + POI + 工作方块认对）；其间发现并修掉「村民职业显示名未翻译」缺陷；LT4-01/02/06/07 需客户端 GUI，LT4-05 仅部分取证 |
| LT6 | 轨道炮光柱三线视觉对照 | 待执行（CH-M，需同坐标截图） |

### LT1（2026-11-02 02:17–02:29）— 饰品后端三态

每态都先 `rm -rf run/{server,client}/.fabric`、两侧 `latest.log` 截空，再用**同一组 `-P` 开关**同时起服务端与客户端（服务端与客户端后端集合必须一致，见下「跨态陷阱」）。

| 态 | 开关 | `AP_FAB_ACCESSORY_PROVIDER` | 5 条机判用例 | 进服 ERROR |
|---|---|---|---|---|
| 双开（默认） | 无 | `OK (trinkets=true accessories=true)` | 6/6 OK（见 §7 R1） | `SyncEntireContainer` ×1 |
| 仅 Accessories | `-PtestTrinkets=false` | `OK (trinkets=false accessories=true)` | 5/5 OK | `SyncEntireContainer` ×1 |
| 仅 Trinkets | `-PtestAccessories=false` | `OK (trinkets=true accessories=false)` | 5/5 OK | **0 条 ERROR** |
| 两态不匹配（对照） | 服务端双开 + 客户端仅 Accessories | 两侧各自 `OK` | — | `SyncEntireContainer` ×1 + `IndexOutOfBoundsException: Index 46 out of bounds for length 46` @ `AbstractContainerMenu.getSlot` ← `initializeContents` ← `handleContainerContent` |

用例集（三态同集）：`TRINKET-BACKEND` / `CURIOS-SLOT-SIZING` / `PLATFORM-BRIDGE` / `TICK-DISPATCH` / `SIGN-ATTR`，全部 `MT_CASES: OK`。

**结论 1（Accessories 侧 ERROR，已定性）**：`[Render thread/ERROR] (SyncEntireContainer) Unable to Sync Container Data for a given Entity as its Capability is null on the Client! [EntityId: 3]` **只在装了 Accessories 时出现**（仅 Trinkets 时 0 条），进服瞬间一次，不阻断游戏与用例。归属 `io.wispforest.accessories` 的容器同步 mixin，不是本模组代码；由于 Accessories 是 `modCompileOnly` + 用户整合包实际后端，记为「上游行为，待 Accessories 联动评估」，本轮不改。

**结论 2（跨态陷阱，属部署要求而非缺陷）**：服务端与客户端后端集合不一致时，玩家背包容器槽数差 1（46 vs 47）⇒ 客户端 `ClientboundContainerSetContentPacket` 解包 `IndexOutOfBoundsException`。两套后端**都会**往玩家背包菜单里注入槽位，因此「服务端装 Trinkets、客户端只装 Accessories」不是受支持组合；同态下 0 异常。

**未闭环**：槽位尺寸的**数值**判据（`handler.getSlots()` 随骰子增减）本轮只锚到「实现与数据在位 + 无异常」，因为 fabric 侧 AutoJS 需 `-PautojsTestMod=1` 才可用，本轮未开。要拿到数值证据需下一轮开 AutoJS 跑 `CH-A` 读 `CuriosApi.getStacksHandler(player,"chip").getSlots()`。
### LT4（2026-11-02 04:50–05:20）— 商店方块 · 村民职业 · 交易表

覆盖 **LT4-03 / LT4-04 完成**（CH-R + CH-A 逐层取证）；LT4-05 部分取证；LT4-01 / LT4-02 / LT4-06 / LT4-07 仍待（都要客户端 GUI 或视觉，属 CH-M）。

场地（可复现，此前失败就是缺这一步）：

```
/kill @e[type=villager]
/fill 92 68 96 112 68 104 minecraft:stone
/setblock 100 69 100 astral_dice:card_shop
/setblock 106 69 100 astral_dice:chip_shop
/setblock 96 69 100 minecraft:composter
/summon villager 100.5 69 101.5
/summon villager 106.5 69 101.5
/summon villager 96.5 69 101.5
```

**结论 1（职业与 POI 实机通过）**：约 25s 后两个村民分别转职为 `astral_dice:card_trader` / `astral_dice:chip_trader`——`/data get entity … VillagerData` 回 `{type: "minecraft:plains", profession: "astral_dice:card_trader", level: 1}`（另一个同形，`chip_trader`）。配套 CH-A 取证：`BuiltInRegistries.VILLAGER_PROFESSION.containsKey(astral_dice:card_trader / chip_trader)` = true / true；`POINT_OF_INTEREST_TYPE.containsKey(astral_dice:card_shop / chip_shop)` = true / true；`PoiTypes.forState(方块默认态)` 对两方块都 `mapped=true`（`matchingStates` 只含 4 个朝向）；`PoiManager.getInSquare(… card_shop|chip_shop, (103,70,100), 16, ANY)` = `count=2`；免费票额 `getFreeTickets` 均为 1。

**结论 2（此前「村民不转职」是场地问题，不是缺陷）**：方块放在 y=70 而 y=69 全是 `Block{minecraft:air}` ⇒ 村民悬空坠落、无法寻路到工作站。同场放香草 `minecraft:composter` 的对照村民在铺地前**同样**不转职，铺地后立刻转职 ⇒ 排除职业/POI 实现问题。

**结论 3（与香草工作站共享发现标签，属既有设计）**：第三个村民放在 composter 旁却转成了 `card_trader`（被更近 / 先搜到的 `card_shop` 抢走）。这与 `world/ModPoiTypes` 注释一致：两个方块都必须进 `minecraft:acquirable_job_site` 才能被无业村民发现，专用标签只决定转职后认哪个站 ⇒ 跨站竞争是可预期行为，非缺陷。

#### 本轮修掉的缺陷：村民职业显示名在 Fabric 上未翻译

- **现象**：实机 `/data get entity …` 回显实体名为 **`entity.minecraft.villager.card_trader`**（无 `astral_dice.`），因此按主线口径只放 `entity.minecraft.villager.astral_dice.card_trader` 时，1.20.1 Fabric 客户端会显示原始键名。
- **根因（决定性证据）**：`Villager#getTypeName` 在 Forge 是被补丁改过的（`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` 内 `net/minecraft/world/entity/npc/Villager.java`）：

```java
protected Component getTypeName() {
   net.minecraft.resources.ResourceLocation profName = net.minecraftforge.registries.ForgeRegistries.VILLAGER_PROFESSIONS.getKey(this.getVillagerData().getProfession());
   return Component.translatable(this.getType().getDescriptionId() + '.' + (!"minecraft".equals(profName.getNamespace()) ? profName.getNamespace() + '.' : "") + profName.getPath());
}
```

  原版（= Fabric 无该补丁）只拼 path ⇒ 键是无命名空间的 `entity.minecraft.villager.<path>`。属于「1.20.1 + 非 Forge 加载器」这类职业的通用坑，影响面仅显示名（交易、转职、JEI 均正常）。
- **修复**：`fabric-1.20.1/src/main/resources/assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` 各加两条扁平键 `entity.minecraft.villager.card_trader` / `…chip_trader`，值沿用主线（卡牌交易员/筹码商人、Card Trader/Chip Trader、カードトレーダー/チップトレーダー），**保留**原带命名空间键（无害，且保持与 P0 键集可比）⇒ 三语各 **1151** 键、键集完全一致、CRLF 1153、bareLF 0、无 BOM。
- **复验**：`:fabric-1.20.1:remapJar` → `BUILD SUCCESSFUL in 9s`，产物 `astral_dice-1.5.0+fabric_1.20.1.jar`；jar 内三语均同时含两种键形；`python tools/verify_fabric_assets.py` → `=== PASS: 8 项闭环全部通过 ===`；`python tools/check_lang_sync.py` 四线全 `[OK]`。

#### 附带修复：`tools/verify_fabric_assets.py` 的 [5] 恒假阳性

- 触发点：`event/ModTooltipHandler.java:436` 的 `"tooltip.astral_dice.dice_face." + stone.type()`（真键 `dice_face.<medium|large|epic>` 在 lang 里齐全）。旧规则只放过「以 `_` 结尾」的拼接前缀，这条以 `.` 结尾 ⇒ 被当成缺键，[5] 恒 FAIL。
- 新规则：**以 `_` 或 `.` 结尾 且紧跟字符串拼接号 `+`** ⇒ 视作前缀跳过。
- 反证（未放宽过头）：合成样本里 `"tooltip.astral_dice.really_missing";`（后面是 `;`）仍被判为缺键，`"item.astral_dice.another_missing" + y`（完整键名后紧跟 `+`）同样不会被静默放过——只有「以 `.`/`_` 结尾且紧跟 `+`」才跳过。

**未闭环**：① LT4-05 的兑换条目过滤 `isEmeraldToStarCoin` / `rawCostA` / `MerchantOfferRawAccess` / `filteredFor` 在 fabric 仍 grep 为 0，需实机看是否会冒出错误的星币→绿宝石兑换行；② LT4-01/02/06/07 需客户端 GUI / JEI / Jade 视觉确认；③ 本轮的显示名修复只做到「键与值就位 + 门禁通过」，**客户端实际渲染**要等下一轮客户端起来时用 CH-A 读 `Component.getString()` 或截图收尾。

### LT1 追加（2026-11-02 05:20–05:35）— 槽位数值取证 + 修掉「筹码栏缩容崩服」

本轮开 AutoJS（服务端 `-PautojsTestMod=1`，端口 25585；客户端同开关自动退到 25586）补上了 LT1 唯一未闭环项：**槽位尺寸的数值判据**。举证过程中撞出一个真缺陷并当场修掉。

#### 一、槽位数值（CH-A 实测，服务端 eval）

读法：`CuriosApi.getCuriosInventory(pl).get().getStacksHandler("dice"/"chip").get()`；装备骰子时**必须**再调 `DiceCurioItem.refreshChipSlotCount(Player)`（`setStackInSlot` 不触发 `onEquip`，尺寸不会自己变）。

| 场景 | chip 栏 | 依据 |
|---|---|---|
| 未佩戴骰子 | **0** | `getSlots()` |
| `astral_dice:dice`（T1） | **0** | T1 品阶 `targetChipSlots` = 0 |
| `astral_dice:nether_star_dice`（T4） | **4** | `DiceTier.cardSlots` 与 `targetChipSlots` 叠加结果 |
| T4 装入 3 个 `flashlight_chip` 后**卸下骰子**（防御式 `refreshChipSlotCount`） | **1** | 设计如此：保留「最靠后非空槽 + 1」，绝不缩掉占用中的槽 |
| 同上再走**强制** `DiceCurioItem.enforceNoDiceState(Player)`（登录/重生路径） | **0** | 槽内 3 个 `flashlight_chip` 退回背包（背包计数 0 → 3） |

`enforceNoDiceState` 的守卫：dice 槽非空时**直接 return**（先实测踩到过：骰子还在槽里调它什么都不会发生——这是预期语义，不是缺陷）。

#### 二、本轮修掉的缺陷：筹码栏运行期缩容 = 服务端必崩（P0）

**现象**：缩容后下一 tick 服务端直接 `Encountered an unexpected exception` 并停服。两次独立复现（同一会话内）：

```
Description: Ticking entity
java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0   # 4 → 0（强制复位）
java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1   # 4 → 1（防御式缩容）
  at java.util.Arrays$ArrayList.get
  at net.minecraft.core.NonNullList.get
  at dev.emi.trinkets.api.TrinketInventory.getItem(TrinketInventory.java:74)
  at net.minecraft.world.inventory.Slot.getItem(Slot.java:53)
  at net.minecraft.world.inventory.AbstractContainerMenu.broadcastChanges
  at net.minecraft.server.level.ServerPlayer.tick
```

**根因**：本线用「绝对 `AttributeModifier` + `TrinketInventory#update()`」实现筹码栏动态尺寸（与 P0 两线 Curios 写法等价）。Trinkets 允许运行期改尺寸，但**玩家背包菜单（`InventoryMenu`）在打开时按当时栏位数建好的 Slot 仍持有旧索引**；`ServerPlayer.tick` 每 tick 都会 `broadcastChanges` 扫全部 Slot ⇒ 索引越界。与目标值无关：**只要缩容就崩**。

**修法**（新建 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trinkets/TrinketInventoryBoundsMixin.java`）：对 `TrinketInventory` 的四条容器入口做越界短路——`getItem` / `removeItem` / `removeItemNoUpdate` 越界返回 `ItemStack.EMPTY`，`setItem` 越界直接取消；界内调用原样放行，不改任何 Trinkets 语义。

配套：新建 `fabric-1.20.1/src/main/resources/astral_dice.trinkets.mixins.json`（`required:false`，与既有 `astral_dice.jade.mixins.json` 同款，Trinkets 缺席时静默跳过），并在 `fabric.mod.json` 的 `mixins` 数组登记。

**复验（实机）**：改后同一场景重跑——`4 → 1` 与 `4 → 0` 都不再崩（`ArrayIndexOutOfBoundsException` 计数 0、无新 crash-report、服务端持续 tick）；强制复位仍正确退回 3 个筹码。新增机判用例 `LT-TRINKETS-BOUNDS-fabric-1.20.1`（断言 mixin 字节码在位 + 日志无越界行 + `mixin` / `crash`），`MT_VALIDATE: OK` 且实跑 `MT_CASE: LT-TRINKETS-BOUNDS-fabric-1.20.1 = OK`。

**残留（已知差异，非崩溃）**：缩容后背包界面里那几个「幽灵槽位」会显示为空栏位，直到重开界面/重登；读到的内容是空栈（被 mixin 短路），不可放入物品。P0 两线由 Curios 重建槽位，故无此现象。

**为什么之前的机判用例没抓到**：`CURIOS-SLOT-SIZING` 只锚「实现与数据在位 + 无 `IndexOutOfBoundsException` 行」，而这次崩溃的异常类是 `ArrayIndexOutOfBoundsException`，且只在**实际缩容**时才触发——静态判据天然碰不到。新增的 `LT-TRINKETS-BOUNDS` 按真实异常串做 `absent` 断言补上这一层。
