# S1 伤害管线与顺序

> 只读审计。基线 `multi-1.20.1-1.21.1` @ `d48a529`(工作区干净)。所有结论均以**代码**为准,不采信类注释与文档(文末列出代码与注释不一致处)。
>
> **路径简写**(后文所有 `N/…`、`F/…` 均指此两处源码根):
> - `N/` = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`
> - `F/` = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`
>
> **外部工件证据简写**(用于原版/加载器语义,均为本机 moddev 反编译源码,不是本项目仓库文件):
> - `VN:` = `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar` 内 `net/minecraft/...`
> - `VF:` = `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` 内 `net/minecraft/...`
> - `BusN:` = `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.neoforged.fancymodloader\loader\4.0.42\…\loader-4.0.42-sources.jar`
> - `BusF:` = `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.minecraftforge\javafmllanguage\1.20.1-47.4.10\…\javafmllanguage-1.20.1-47.4.10-sources.jar`、`…\net.minecraftforge\forgespi\7.0.1\…\forgespi-7.0.1-sources.jar`
> - `JarN:` = `neoforge-1.21.1/build/libs/astral_dice-1.2.1+neoforge_1.21.1.jar`(2026/9/15 14:41 构建)
> - `JarF:` = `forge-1.20.1/build/libs/astral_dice-1.2.1+forge_1.20.1.jar`(2026/9/15 14:41 构建)

---

## 1. 参与方与订阅点清单

### 1.1 事件阶段语义(先说清"阶段",否则顺序无从谈起)

| 阶段 | 1.21.1(NeoForge 21.1.235) | 1.20.1(Forge 47.4.10) |
|---|---|---|
| 最前置、可取消、早于无敌/濒死判定 | `LivingIncomingDamageEvent`(VN:1152-1153,在 `LivingEntity#hurt` 里压入 `DamageContainer` 后立即派发;**玩家**走 `Player#hurt` → 自身判定后才 `super.hurt`,VN:922-952) | `LivingAttackEvent`(`ForgeHooks.onLivingAttack` 是 `hurt` **第一条语句**:VF LivingEntity:1088-1090;**玩家**侧 VF Player:841-851 同样是 `Player#hurt` 第一行) |
| 护甲/附魔/抗性之后、扣血之前(可改最终值) | `LivingDamageEvent.Pre`(VN:1785-1793,`onLivingDamagePre` 在 ARMOR/MAGIC 折减之后、`ABSORPTION` 折减**之前**;扣血取容器 `getNewDamage()`) | `LivingHurtEvent` = 护甲/附魔/抗性**之前**的原始值(VF:1665-1668);`LivingDamageEvent` = 上述折减**且吸收(absorption)已扣**之后的「FINAL value」,可 `setAmount` 且返回值直接用于扣血(VF:1669-1683;类文档 VF `LivingDamageEvent.java:21-33`;`ForgeHooks.onLivingDamage` 返回 `event.getAmount()`) |
| 死亡 | `LivingDeathEvent`(`die()` 首行 CommonHooks.onLivingDeath;**取消即 `die()` 整体 return**,含掉落) | `LivingDeathEvent`(同位置:VF LivingEntity:1342-1343) |
| 图腾 | `LivingUseTotemEvent`:原版保命在 `hurt` 内、`isDeadOrDying()` 分支调用 `checkTotemDeathProtection`,**早于** `die()`(VN:1260-1267;图腾对 `BYPASSES_INVULNERABILITY` 直接 return false,VN:1306-1308) | 同位置(VF:1205-1213;VF:1247-1249) |
| 吸收折减位置差异 | Pre 阶段**未扣** absorption | `LivingDamageEvent` 阶段**已扣** absorption |
| `LivingDamageEvent.Post` | 存在,但**本仓库无任何订阅者**(全仓 grep `LivingDamageEvent.Post|onLivingDamagePost` 在 `neoforge-1.21.1/src/main/java` 零命中) | 不存在 |

### 1.2 订阅点清单(伤害管线相关,按事件分组)

`优先级`栏:`—` = 未声明(默认 NORMAL)。

#### A. `LivingIncomingDamageEvent`(仅 1.21.1)

| 类 | 事件 | 优先级 | 1.21.1 file:line | 1.20.1 file:line |
|---|---|---|---|---|
| `DiceCombatEvents`(闪避统一取消入口 `applyDodgeCancel`,由下方三处调用) | LivingIncomingDamageEvent | — | `N/combat/DiceCombatEvents.java:1067`(定义) | `F/combat/DiceCombatEvents.java:1061`(定义,签名换成 `LivingAttackEvent`) |
| `DiceCombatEvents.onMosesBrokenDodge` | LivingIncomingDamageEvent | — | `N/combat/DiceCombatEvents.java:1088-1089` | `F/combat/DiceCombatEvents.java:1096-1097`(`LivingAttackEvent`) |
| `ObsidianDiceHandler.onFireDamage` | LivingIncomingDamageEvent | **HIGH** | `N/event/ObsidianDiceHandler.java:29-30` | `F/event/ObsidianDiceHandler.java:28-29`(**LivingHurtEvent**,默认) |
| `FateGuidanceCardItem.onCurseMitigation`(七咒倍率捕获) | LivingIncomingDamageEvent | **LOWEST** | `N/item/card/FateGuidanceCardItem.java:85-87` | —(1.20.1 拆成两点,见 B 段) |
| `AdrenalineChipItem.onAdrenalineDodge` | LivingIncomingDamageEvent | — | `N/item/chip/AdrenalineChipItem.java:86-87` | `F/item/chip/AdrenalineChipItem.java:87-88`(`LivingAttackEvent`) |
| `NancyLuSignItem.onNancyLuEnderPearlDamage` | LivingIncomingDamageEvent | — | `N/item/sign/NancyLuSignItem.java:294-295` | `F/item/sign/NancyLuSignItem.java:296-297`(`LivingAttackEvent`) |
| `FateGuidanceCardItem.onCurseOriginalCapture`(原始伤害记录) | —(1.21.1 无对应,直接用 `getOriginalAmount()`) | — | — | `F/item/card/FateGuidanceCardItem.java:86-88`(`LivingAttackEvent`,**HIGHEST**) |

#### B. `LivingDamageEvent.Pre`(1.21.1)/ `LivingDamageEvent`+`LivingHurtEvent`(1.20.1)

| 类 | 事件(1.21.1 / 1.20.1) | 优先级 | 1.21.1 file:line | 1.20.1 file:line |
|---|---|---|---|---|
| `EnderDiceHandler`(雨中/水下 +40%) | `LivingDamageEvent.Pre` / `LivingHurtEvent` | **HIGH** | `N/event/EnderDiceHandler.java:134-135` | `F/event/EnderDiceHandler.java:135-136` |
| `DiceCombatEvents.onLivingDamagePre`(骰战主链路) | `LivingDamageEvent.Pre` / `LivingDamageEvent` | — | `N/combat/DiceCombatEvents.java:184-185` | `F/combat/DiceCombatEvents.java:180-181` |
| `DamageEffectCardHandler.onLivingDamagePre`(法伤) | `LivingDamageEvent.Pre` / `LivingHurtEvent` | — | `N/event/DamageEffectCardHandler.java:31-32` | `F/event/DamageEffectCardHandler.java:28-29` |
| `NancyLuSignItem.onNancyLuAnyAttackWhileHidden`(破隐身) | `LivingDamageEvent.Pre` / `LivingDamageEvent` | — | `N/item/sign/NancyLuSignItem.java:262-263` | `F/item/sign/NancyLuSignItem.java:264-265` |
| `DiceCombatEvents.onPandamanTauntCounter`(嘲讽反击) | `LivingDamageEvent.Pre` / `LivingDamageEvent` | — | `N/combat/DiceCombatEvents.java:1109-1110` | `F/combat/DiceCombatEvents.java:1119-1120` |
| `DiceCombatEvents.onBerserkDamageTaken` | `LivingDamageEvent.Pre` / `LivingDamageEvent` | **LOW** | `N/combat/DiceCombatEvents.java:894-895` | `F/combat/DiceCombatEvents.java:888-889` |
| `DiceCombatEvents.onWeakMarkDamage` | `LivingDamageEvent.Pre` / `LivingDamageEvent` | **LOW** | `N/combat/DiceCombatEvents.java:940-941` | `F/combat/DiceCombatEvents.java:934-935` |
| `ChipDamageHandler.onLivingDamagePre`(安全气囊 / 磨刀石) | `LivingDamageEvent.Pre` / `LivingDamageEvent` | **LOWEST** | `N/event/ChipDamageHandler.java:45-46` | `F/event/ChipDamageHandler.java:45-46` |
| `PaparaSignItem.onPaparaBiteHurtHeal`(汲取回血) | `LivingDamageEvent.Pre` / `LivingDamageEvent` | **LOWEST** | `N/item/sign/PaparaSignItem.java:56-57` | `F/item/sign/PaparaSignItem.java:56-57` |
| `ObsidianDiceHandler.onFireDamage`(火焰 -70%) | `LivingIncomingDamageEvent` / `LivingHurtEvent` | **HIGH** / — | `N/event/ObsidianDiceHandler.java:29-30` | `F/event/ObsidianDiceHandler.java:28-29` |
| `FateGuidanceCardItem.onCurseMitigation`(七咒倍率) | `LivingIncomingDamageEvent` / `LivingHurtEvent` | **LOWEST** | `N/item/card/FateGuidanceCardItem.java:85-87` | `F/item/card/FateGuidanceCardItem.java:94-96` |

#### C. `LivingDeathEvent` / 图腾 / 掉落

| 类 | 事件 | 优先级 | 1.21.1 line | 1.20.1 line |
|---|---|---|---|---|
| `ChipDamageHandler.onLivingDeath`(气囊死亡兜底) | LivingDeathEvent | **HIGHEST** | `N/event/ChipDamageHandler.java:75-76` | `F/event/ChipDamageHandler.java:75-76` |
| `EnderDiceHandler.onLivingDeath`(伪不死图腾) | LivingDeathEvent | — | `N/event/EnderDiceHandler.java:146-147` | `F/event/EnderDiceHandler.java:147-148` |
| `PlayerLifecycleHandler.onPlayerDeathClearEffects`(死亡清理) | LivingDeathEvent | — | `N/event/PlayerLifecycleHandler.java:114-115` | `F/event/PlayerLifecycleHandler.java:111-112` |
| `CursedSwordChipItem.onCursedSwordKill` | LivingDeathEvent | — | `N/item/chip/CursedSwordChipItem.java:118-119` | `F/item/chip/CursedSwordChipItem.java:116-117` |
| `ElectricSwordChipItem.onLivingDeath` | LivingDeathEvent | — | `N/item/chip/ElectricSwordChipItem.java:75-76` | `F/item/chip/ElectricSwordChipItem.java:75-76` |
| `SmartWatchChipItem.onLivingDeath` | LivingDeathEvent | — | `N/item/chip/SmartWatchChipItem.java:47-48` | `F/item/chip/SmartWatchChipItem.java:47-48` |
| `SatelliteChipItem.onSatelliteRangedMagicKill` | LivingDeathEvent | — | `N/item/chip/SatelliteChipItem.java:98-99` | `F/item/chip/SatelliteChipItem.java:99-100` |
| `BonnieSignItem.onBonnieKill` | LivingDeathEvent | — | `N/item/sign/BonnieSignItem.java:137-138` | `F/item/sign/BonnieSignItem.java:136-137` |
| `HaiqingSignItem.onWeakMarkKill` | LivingDeathEvent | — | `N/item/sign/HaiqingSignItem.java:131-132` | `F/item/sign/HaiqingSignItem.java:130-131` |
| `InvestigationEventUtil.onUndercoverInvestigationKill` | LivingDeathEvent | — | `N/item/InvestigationEventUtil.java:107-108` | `F/item/InvestigationEventUtil.java:104-105` |
| `EnderDiceHandler.onLivingUseTotem`(补传送) | LivingUseTotemEvent | — | `N/event/EnderDiceHandler.java:181-182` | `F/event/EnderDiceHandler.java:182-183` |
| `LootInjectionHandler.onLivingDrops`(死亡之后) | LivingDropsEvent | — | `N/event/LootInjectionHandler.java:110-111` | `F/event/LootInjectionHandler.java:106-107` |
| `PlayerLifecycleHandler.onPlayerCloneRestoreDeathPreserved` | PlayerEvent.Clone | **LOWEST** | `N/event/PlayerLifecycleHandler.java:184-186` | `F/event/PlayerLifecycleHandler.java:180-182` |
| `PlayerLifecycleHandler.onPlayerRespawnMedkit` | PlayerEvent.PlayerRespawnEvent | — | `N/event/PlayerLifecycleHandler.java:211-213` | `F/event/PlayerLifecycleHandler.java:208-210` |

#### D. 与伤害无关但同文件出现的订阅点(明确排除,避免漏判)

`N/event/PlayerTickEvents.java:110-120` 与 `F/event/PlayerTickEvents.java:108-118` 只订阅 PlayerTick(`LivingDamageEvent` 仅 import,`:62`/`:63`,无订阅方法);`AnvilUpgradeHandler` / `ModTooltipHandler` / `LootInjectionHandler` / `ModEffectEvents` / `DiceCombatEvents.onLivingChangeTarget` / `onDiceBlessingExpired` 均不订阅伤害事件本身。

### 1.3 同优先级执行顺序 = **注册顺序 = 类扫描顺序**(脆弱依赖,已定位机制)

两个加载器的默认派发语义都是「同优先级按监听器注册顺序」,而 `@EventBusSubscriber`/`@Mod.EventBusSubscriber` 类的注册顺序来源已被证实为**类注解扫描顺序**:

- NeoForge:`ModFileScanData.annotations` 是 `LinkedHashSet`(`BusN:net/neoforged/neoforgespi/language/ModFileScanData.java:19`);`AutomaticEventSubscriber.inject` 用 `scanData.getAnnotations().stream().filter(AUTO_SUBSCRIBER…)` 收集成 `ebsTargets` 后 `ebsTargets.forEach(ad -> … )`,类在末尾 `FMLLoader.getBindings().getGameBus().register(clazz)` 注册(`BusN:net/neoforged/fml/javafmlmod/AutomaticEventSubscriber.java:43-49,86`)。
- Forge 1.20.1:`forgespi` 的 `ModFileScanData.annotations` 同样是 `LinkedHashSet`(`BusF:forgespi-7.0.1-sources.jar:net/minecraftforge/forgespi/language/ModFileScanData.java:31`);`AutomaticEventSubscriber.inject` 同样是 `getAnnotations().stream()` → `ebsTargets.forEach(…)`,类在 lambda 内 `busTarget.bus().get().register(Class.forName(...))` 注册(`BusF:javafmllanguage-1.20.1-47.4.10-sources.jar:net/minecraftforge/fml/javafmlmod/AutomaticEventSubscriber.java:41-48,61`)。

即顺序 = 模组文件里 `.class` 条目的读取顺序。当前两个已构建产物中的**实测相对顺序**(类条目索引):

| 类 | JarN 索引 | JarF 索引 |
|---|---|---|
| `combat/DiceCombatEvents.class` | 8 | 8 |
| `event/ChipDamageHandler.class` | 70 | 80 |
| `event/DamageEffectCardHandler.class` | 72 | 82 |
| `event/EnderDiceHandler.class` | 75 | 85 |
| `event/PlayerLifecycleHandler.class` | 84 | 94 |
| `item/sign/PaparaSignItem.class` | 199 | 209 |

→ 当前顺序:DiceCombatEvents 早于 DamageEffectCardHandler;ChipDamageHandler 早于 PaparaSignItem;EnderDiceHandler 早于 PlayerLifecycleHandler。**这不是契约**,dev(`runClient` 从 `build/classes` 目录扫描)与 jar 的顺序也不保证一致(见第 5 节)。

---

## 2. 触发顺序(按场景逐条)

### 场景 A:近战命中一次(攻击者:赐福 + 骰子 + 近战武器;目标:敌对生物)

**1.21.1 实际顺序**

| # | 执行者 | file:line | 依据 |
|---|---|---|---|
| A1 | `LivingIncomingDamageEvent` 派发 | VN:1152-1153 | 压 `DamageContainer` 后立即派发 |
| A2 | `ObsidianDiceHandler`(火焰 -70%) | `N/event/ObsidianDiceHandler.java:29-36` | 优先级 **HIGH** |
| A3 | `onMosesBrokenDodge`(破绽闪避,可取消并反击) | `N/combat/DiceCombatEvents.java:1088-1105` | 默认优先级 |
| A4 | `AdrenalineChipItem.onAdrenalineDodge`(20% 闪避) | `N/item/chip/AdrenalineChipItem.java:86-102` | 默认;**与 A3 同优先级 → 靠扫描顺序**(`combat` 包在 `item` 包之前,索引 8 < 156 区间) |
| A5 | `NancyLuSignItem.onNancyLuEnderPearlDamage`(末影珍珠摔伤免疫) | `N/item/sign/NancyLuSignItem.java:294-302` | 默认 |
| A6 | `FateGuidanceCardItem.onCurseMitigation`(捕获七咒实际倍率) | `N/item/card/FateGuidanceCardItem.java:85-117` | 优先级 **LOWEST**,晚于神秘遗物+(其自身优先级不在本仓库,见第 5 节) |
| A7 | 护甲/韧性折减 → 抗性/保护附魔折减 | VN:1787-1788 | 原版 |
| A8 | `EnderDiceHandler`(雨中/水下 ×1.4) | `N/event/EnderDiceHandler.java:134-143` | 优先级 **HIGH**,本阶段最早 |
| A9 | `DiceCombatEvents.onLivingDamagePre` → `event.setNewDamage(骰战最终值)` | `N/combat/DiceCombatEvents.java:185`…`:604` | 默认;**A9-A12 同优先级,顺序即扫描顺序** |
| A10 | `DamageEffectCardHandler`(法伤作用域才生效) | `N/event/DamageEffectCardHandler.java:31-77` | 默认;当前晚于 A9 |
| A11 | `NancyLuSignItem.onNancyLuAnyAttackWhileHidden`(破隐身) | `N/item/sign/NancyLuSignItem.java:262-271` | 默认 |
| A12 | `DiceCombatEvents.onPandamanTauntCounter`(嘲讽反击注入) | `N/combat/DiceCombatEvents.java:1109-1123` | 默认 |
| A13 | `onBerserkDamageTaken`(+1×层数) | `N/combat/DiceCombatEvents.java:894-901` | 优先级 **LOW** |
| A14 | `onWeakMarkDamage`(×1.10 / ×1.30) | `N/combat/DiceCombatEvents.java:940-958` | 优先级 **LOW**;**A13/A14 同优先级靠扫描顺序**,两者互不读对方结果(彼此独立:一个加、一个乘) |
| A15 | `ChipDamageHandler`(气囊致命判定 → 0 / 磨刀石减伤上限) | `N/event/ChipDamageHandler.java:45-66` | 优先级 **LOWEST**,晚于所有改值者 |
| A16 | `PaparaSignItem`(汲取回血 = 当前值/2) | `N/item/sign/PaparaSignItem.java:56-63` | **同为 LOWEST**;当前 `ChipDamageHandler`(索引 70)< `PaparaSignItem`(199) → 气囊先判 |
| A17 | 扣血(VN:1793-1801)→ 若致死:`checkTotemDeathProtection`(原版图腾,VN:1260-1267)→ `die()` → `LivingDeathEvent` | VN | 原版 |

- **由优先级保证**:A8 早于 A9-A12;A9-A12 早于 A13/A14;A13/A14 早于 A15/A16;死亡事件:A2 的 HIGHEST(气囊兜底)早于所有默认优先级。
- **由注册(扫描)顺序保证(隐含依赖)**:A9↔A10↔A11↔A12 之间;A13↔A14 之间;A15↔A16 之间;以及死亡事件侧 `EnderDiceHandler`(75)↔`PlayerLifecycleHandler`(84)↔ 各击杀钩子(`item/chip`、`item/sign`,索引更后)。

**1.20.1 实际顺序**

| # | 执行者 | file:line | 依据 |
|---|---|---|---|
| B1 | `LivingAttackEvent` 派发 | VF LivingEntity:1088-1090;VF Player:841-842 | `hurt` 首语句 |
| B2 | `FateGuidanceCardItem.onCurseOriginalCapture`(记录 original) | `F/item/card/FateGuidanceCardItem.java:86-92` | **HIGHEST** |
| B3 | `onMosesBrokenDodge` / `AdrenalineChipItem` / `NancyLu` 珍珠免疫 | `F/combat/DiceCombatEvents.java:1096-1115`、`F/item/chip/AdrenalineChipItem.java:87-105`、`F/item/sign/NancyLuSignItem.java:296-305` | 默认;`combat` 包先于 `item` 包 |
| B4 | `LivingHurtEvent`(护甲前) | VF:1665-1668 | — |
| B5 | `EnderDiceHandler`(雨中/水下 ×1.4,**护甲前**) | `F/event/EnderDiceHandler.java:135-144` | **HIGH** |
| B6 | `ObsidianDiceHandler`(火焰 -70%,护甲前) | `F/event/ObsidianDiceHandler.java:28-36` | 默认 |
| B7 | `DamageEffectCardHandler`(法伤) | `F/event/DamageEffectCardHandler.java:28-74` | 默认;**B6/B7 同优先级靠扫描顺序** |
| B8 | `FateGuidanceCardItem.onCurseMitigation`(ratio = 当前/原始) | `F/item/card/FateGuidanceCardItem.java:94-126` | **LOWEST**:读到的 `current` 已含 B5/B6 的倍数(见 S1-C3) |
| B9 | 护甲/韧性 → 抗性/附魔 → **扣 absorption** | VF:1667-1670 | 原版 |
| B10 | `LivingDamageEvent` 派发(amount 已是 FINAL,返回即扣血) | VF:1669-1683 | — |
| B11 | `DiceCombatEvents.onLivingDamagePre` → `setAmount(骰战最终值)`,随后引爆大当家溅射 | `F/combat/DiceCombatEvents.java:181`…`:601`、`:620-667` | 默认 |
| B12 | `DamageEffectCardHandler` 不在此事件(在 B7) | — | — |
| B13 | `NancyLuSignItem.onNancyLuAnyAttackWhileHidden` | `F/item/sign/NancyLuSignItem.java:264-271` | 默认 |
| B14 | `onPandamanTauntCounter` | `F/combat/DiceCombatEvents.java:1119-1133` | 默认 |
| B15 | `onBerserkDamageTaken` | `F/combat/DiceCombatEvents.java:888-895` | **LOW** |
| B16 | `onWeakMarkDamage` | `F/combat/DiceCombatEvents.java:934-951` | **LOW** |
| B17 | `ChipDamageHandler`(气囊/磨刀石) | `F/event/ChipDamageHandler.java:45-66` | **LOWEST** |
| B18 | `PaparaSignItem`(汲取回血) | `F/item/sign/PaparaSignItem.java:56-63` | **LOWEST**,当前晚于 B17 |
| B19 | 扣血 → 图腾 → `die()`/`LivingDeathEvent` | VF:1205-1213、1342-1343 | — |

### 场景 B:大当家「战斗爽·溅射」满层引爆(同一次攻击内嵌套注入)

- 触发置位:`N/combat/DiceCombatEvents.java:347`(`FenSignItem.onBlessingTriggered`)。
- 顺序:N/`:604` **先** `setNewDamage(finalDmg)` → N/`:623` 进 `aoeProcessing=true` → N/`:628-630` `splashDmg = max(5, 0.88×finalDmg)`(`N/item/sign/FenSignItem.java:45-49`) → N/`:642-657` 对 6 格内敌对目标逐个 `victim.hurt(trueDamage(level,player), splashDmg)`;**主目标临时清零 `invulnerableTime`**(N/`:648-653`)→ 嵌套进入**完整的** `LivingDamageEvent.Pre` 链(嵌套的 `DiceCombatEvents` 在 N/`:200` 因 `aoeProcessing` 提前 return,但 `EnderDiceHandler`/`DamageEffectCardHandler`/`NancyLuSignItem`/`onBerserkDamageTaken`/`onWeakMarkDamage`/`ChipDamageHandler`/`PaparaSignItem` **全部照常执行**——它们都不检查 `aoeProcessing`)。
- 嵌套返回后,外层事件继续 A13-A16。
1.20.1 同结构:`F/combat/DiceCombatEvents.java:601`、`:620-667`、`:645-653`、`:200`。

### 场景 C:法伤牌命中(激光 / 活板砖 / 轨道炮 / 定向爆破 / 活体书页 / 电击手套)

- 入口 `DamageEffectCardHandler`(N/`:31-77`);加成聚合走注册表 `SpellDamageRegistry.modifiers()`(N/`:49-54`),加成值由 `effectCardDamageBonus`(N/`combat/SpellDamageRegistry.java:133-141`)与 `livingPageBonusPages`(N/`:148-152`)给出。
- 加成"按真伤独立结算":`target.hurt(trueDamage(target.level(), player), bonus)`(N/`:62-71`)——**发生在原事件处理中,嵌套伤害在同一个 tick 内先落地**。
- 嵌套事件的身份:`ModDamageTypes.trueDamage(level, causing)` 构造 `new DamageSource(holder, null, player)`(N/`damage/ModDamageTypes.java:48-50`)→ `getDirectEntity()==null`、`getEntity()==player`(VN:61-68 实测语义)。
  → 嵌套重入 `DamageEffectCardHandler` 时 `isSpellDamage(source, null)` 为 false(白名单 matcher 都要求 `direct instanceof AbstractArrow/ThrowableProjectile` 或类型/msgId 命中,N/`SpellDamageRegistry.java:159-174`),配合 `APPLYING_TRUE_BONUS` 闸门(N/`:29,62-69`)不递归。
  → 但嵌套重入 `DiceCombatEvents.onLivingDamagePre` 时:先执行受击钩子(N/`DiceCombatEvents.java:192-196`,在 `:200` 提前 return **之前**),随后因 `directEntity==null` 在 N/`:201` 返回,不重走骰战。
- `onHit` 副作用(定向爆破 AOE N/`SpellDamageRegistry.java:243-266`、电击手套 AOE N/`:363-386`)同样在本事件内再次 `hurt(trueDamage…)`。

### 场景 D:致命伤害与"谁赢"

- 气囊(伤害阶段):`damage >= player.getHealth()` 且 `AirbagChipItem.tryNegateFatal` 成功(6 充能 + 1:00 冷却,N/`item/chip/AirbagChipItem.java:45-58`)→ `setNewDamage(0)`(N/`event/ChipDamageHandler.java:56-59`)→ 生命不降 → **不会产生死亡事件**,原版图腾与末影骰子都不会被调用。**气囊恒为第一顺位**,由 LOWEST 优先级 + 原版调用点(Pre 在 `isDeadOrDying` 判定之前,VN:1789 vs 1260)双重保证。
- 若生命仍归零:`die()` → `LivingDeathEvent`:
  1. `ChipDamageHandler` **HIGHEST**(N/`:75-85`):`tryNegateFatal` 成功则 `setCanceled(true)` + `setHealth(max(1, health))`;失败则放行。
  2. 默认优先级(顺序即扫描顺序):`EnderDiceHandler`(N/`:146-178`,取消 + 血量 1 + 清效果 + REGEN/ABSORPTION/FIRE_RES + 传送 + 5:00 冷却)与 `PlayerLifecycleHandler`(N/`:114-179`,默认优先级,取消则 return `:119`)。
  3. 其余击杀钩子(均默认优先级)在事件被取消后**仍会被派发**,其中四个不检查 `isCanceled()`(见 S1-C5)。
- 原版图腾:发生在 `hurt` 内的 `checkTotemDeathProtection`(VN:1260-1267),**早于** `die()`;因此原版图腾 > 末影骰子死亡事件图腾;同时 `EnderDiceHandler.onLivingUseTotem` 会给原版图腾补一次安全传送且**不消耗**末影骰子冷却(N/`:181-193`)。
- `DeathPreservedBonuses`(静态表 `ConcurrentHashMap`,N/`component/DeathPreservedBonuses.java:33-59`)由 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 调用 `preserveOnDeath`(N/`event/PlayerLifecycleHandler.java:124`),回写走 Clone(LOWEST,N/`:184-192`)与 Respawn(N/`:211-221`),取走即删(`:50-51`),**幂等,不会重复回血/重复加成**。

### 场景 E:真伤 / 穿甲 / 抗性 / 无敌

1. `astral_dice:true_damage` 的类型定义无特殊字段(`neoforge-1.21.1/src/main/resources/data/astral_dice/damage_type/true_damage.json:1-5`;forge 同)。
2. 它被写入 `minecraft:bypasses_armor` 标签(`neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json:1-5`;`forge-1.20.1/...` 同)。标签默认 `replace=false` 语义 → 与原版值并集(原版 `bypasses_armor` 含 `magic/out_of_world/generic_kill/...`)。
3. 折减路径:护甲+韧性被 `if (!damageSource.is(BYPASSES_ARMOR))` 整段跳过(VN:1725-1734 / VF:1613-1620);抗性提升仍生效(除非 `bypasses_resistance`,VN:1743);保护附魔仍生效(除非 `bypasses_enchantments`,VN:1762-1766 / VF:1647-1652)。**未**加入 `bypasses_resistance`/`bypasses_enchantments` → 真伤只穿甲,不穿抗性与保护附魔。
4. `BYPASSES_INVULNERABILITY` 标签(本机实测)= `[minecraft:out_of_world, minecraft:generic_kill]` → `/kill`(`generic_kill`)与虚空(`out_of_world`)能穿过实体无敌标志并一路走完 `LivingIncomingDamageEvent`/`LivingAttackEvent` → `LivingDamageEvent(.Pre)`;气囊最终判定**不检查该标签**(N/`event/ChipDamageHandler.java:45-66` 无 tag 判断)→ 气囊可抵消 `/kill` 与虚空;末影骰子**检查并排除**(N/`event/EnderDiceHandler.java:152-153`;F/`:153-154`);原版图腾也排除(VN:1306-1308 / VF:1247-1249)。
5. 电磁炮雷击(延迟 1s)走 `Entity#thunderHit` 注入 `trueDamage(level)`(无来源实体):`N/mixin/EntityThunderHitMixin.java:44-62`,目标筛选在 `LightningBolt#tick` 的谓词上收窄(`N/mixin/LightningBoltStrikeScopeMixin.java:36-48`)。它不属于"玩家直接攻击"(`getDirectEntity()==null` 且 `getEntity()==null`)→ 不触发骰战,也不给击杀归属。

### 场景 F:注入的所有权(`aoeProcessing` / `counterDepth` 闸门边界)

- 闸门只在 `DiceCombatEvents.onLivingDamagePre` 内部判定(N/`:200`),**晚于**受击钩子分发(N/`:192-196`)。
- 由此:溅射/法伤 AOE/反击注入的嵌套事件**仍会**调用 `BaseSignItem.invokeHurtHooks` 与 `BufferShieldChipItem.onHurt`。判定为**无冲突**的依据:唯一覆写 `onHurt` 的是 `LuluSignItem`(N/`item/sign/BaseSignItem.java:195-198`、`LuluSignItem.java:45`),其内部有 20 tick 去重(`LuluSignItem.java:46-50`);`BufferShieldChipItem.onHurt` 自带 15 秒冷却(`N/item/chip/BufferShieldChipItem.java:41-43`)。同一次攻击内的多次嵌套不会产生重复收益。

---

## 3. 冲突项

> 每条给出「现象 → 证据(file:line + 关键代码)→ 建议修法 → 是否两版本皆有」。
> `两版本`栏说明该冲突在 1.21.1 / 1.20.1 是否都有对应实现(即便数值口径不同)。

### S1-C1 | **blocker** | 大当家溅射在主目标上先于"最终伤害判定者"落地,气囊既可能被溅射空耗,又无法真正无效化本次命中

**现象**
1. 溅射在 `LivingDamageEvent.Pre` 的**默认优先级**阶段就通过 `victim.hurt(...)` 把伤害写进主目标血量;而安全气囊的判定在同一事件的 **LOWEST** 阶段,此时主目标已经掉过血。
2. 因此"气囊成功 → 本次伤害无效"只对**主伤害**成立:气囊把 `newDamage` 改成 0,但主目标已先吃到 `max(5, 0.88×finalDmg)` 的溅射伤害。
3. 更严重:若主目标(玩家)在溅射落地时满足 `splashDmg >= getHealth()`,`AirbagChipItem.tryNegateFatal` 会被**嵌套的那一次事件**消耗(扣 6 充能 + 1:00 冷却),随后外层那一笔致命伤害在 LOWEST 阶段发现冷却已启动而无法无效化 → 玩家死亡。安全气囊只挡住了 88% 的那一份。

**证据**
```java
// N/combat/DiceCombatEvents.java:604  先定稿并写入事件
event.setNewDamage((float) finalDmg);
// N/combat/DiceCombatEvents.java:628-630  溅射值以 finalDmg 为基数
float splashDmg = (float) Math.max(FenSignItem.SPLASH_DAMAGE_MIN, finalDmg * FenSignItem.SPLASH_RATIO);
// N/combat/DiceCombatEvents.java:648-653  主目标清零无敌帧后立即 hurt(嵌套=完整事件链)
if (isMainTarget) victim.invulnerableTime = 0;
victim.hurt(splashSource, splashDmg);
```
```java
// N/event/ChipDamageHandler.java:56-59  LOWEST,晚于上面的嵌套注入
if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
    event.setNewDamage(0.0F);
```
```java
// N/item/chip/AirbagChipItem.java:48-52  一次触发即扣充能 + 上冷却(嵌套那次会把它用掉)
if (isOnCooldown(player)) return false;
if (ChargeManager.getStacks(player) < CHARGE_COST) return false;
ChargeManager.consume(player, CHARGE_COST);
ModAttachments.setAirbagCooldownEnd(player, player.level().getGameTime() + COOLDOWN_TICKS);
```
另:嵌套事件的 `DiceCombatEvents` 提前 return 的判定在受击钩子之后(`N/combat/DiceCombatEvents.java:192-196` vs `:200`),所以嵌套溅射也会走一遍受击侧联动(当前因 20t/15s 去重无重复收益,见场景 F)。

**建议修法**(只读审计,不含实施)
- 把"注入型伤害"(溅射 / 法伤 AOE / 反击)与"本次命中的主伤害"分离:注入延迟到主伤害落地之后(如 `LivingDamageEvent.Post`(1.21.1 有该阶段,当前无订阅者)或下一 tick 调度),避免同一事件内先行扣血。
- 或:在 `ChipDamageHandler` 之外为"注入伤害"提供独立的致命判定会话(例如以 ThreadLocal/本次事件作用域记录"本次命中已用气囊",嵌套注入不得消耗气囊),让气囊只对主伤害生效。
- 至少应让"外层事件的 LOWEST 判定"看到"未受本次嵌套注入影响"的血量快照(例如把溅射对**主目标**的那一份排在 LOWEST 之后)。

**两版本**:皆有(`F/combat/DiceCombatEvents.java:601,620-667,645-653`;`F/event/ChipDamageHandler.java:45-66`;`F/item/chip/AirbagChipItem.java` 同逻辑)。

### S1-C2 | **high** | 骰战 `setNewDamage/setAmount` 无条件覆盖同阶段先前修饰 → 1.21.1 下雨中/水下 +40% 对骰战命中完全失效

**现象**:`EnderDiceHandler` 在 HIGH 把 `newDamage` 乘 1.4,`DiceCombatEvents` 在默认优先级用 `finalDmg` **整体替换**,前值作废。目标佩戴末影骰子且在雨中/水下时,被骰战命中不会得到 +40% 的放大(只有非骰战来源才吃得到)。

**证据**
```java
// N/event/EnderDiceHandler.java:142  HIGH,先放大
event.setNewDamage(event.getNewDamage() * RAIN_WATER_DAMAGE_MULTIPLIER);
// N/combat/DiceCombatEvents.java:604  默认优先级,后整体覆盖
event.setNewDamage((float) finalDmg);
```
骰战内部没有任何补偿该项的代码:它只读 `target` 的护甲/韧性/骰点/防御牌与 `WEAK_MARK`(`N/combat/DiceCombatEvents.java:563-596`),不读雨中状态。
1.20.1 侧同样被覆盖(`F/event/EnderDiceHandler.java:143` 在 `LivingHurtEvent`;`F/combat/DiceCombatEvents.java:601` 在 `LivingDamageEvent` 覆盖),但该平台的倍率会被七咒捕获顺带带进来(见 S1-C3),两版本结果不同。

**建议修法**:把"承受侧百分比修饰"从"事件内改值"改为骰战结算中的一个显式乘子(例如 `EXTERNAL_DAMAGE_FACTORS` 的承受侧条目,或在 `finalDmg` 定稿前读取这些状态),否则任何早于默认优先级的改值都会被静默丢弃。

**两版本**:皆有覆盖行为(1.21.1 表现为"丢失",1.20.1 表现为"仅经七咒 ratio 偶然保留")。

### S1-C3 | **high** | 1.20.1 的七咒 ratio 捕获与其它 `LivingHurtEvent` 修饰同阶段 → ratio 被雨中/火焰倍率污染并乘进骰战最终伤害;1.21.1 捕获在更早阶段,不含这些倍率

**现象**:1.20.1 用 `LivingAttackEvent`(HIGHEST)记原值 + `LivingHurtEvent`(LOWEST)算 `ratio = current/original`。`current` 里已经含 `EnderDiceHandler.onLivingHurt`(HIGH,×1.4)与 `ObsidianDiceHandler.onFireDamage`(默认,-70%)的结果,`original` 是 AttackEvent 的原值 → **ratio 被这些倍率污染**。该 ratio 随后经 `registerDiceCombatFactor` 乘进 `finalDmg`(`F/combat/DiceCombatEvents.java:597-599,920-929`)。
1.21.1 的捕获在 `LivingIncomingDamageEvent`(LOWEST),而雨中放大发生在之后的 `LivingDamageEvent.Pre`(HIGH)→ ratio 不含雨中倍率。

**证据**
```java
// F/item/card/FateGuidanceCardItem.java:90  LivingAttackEvent HIGHEST 记原值
ModAttachments.setCurseOriginalAmount(player, event.getAmount());
// F/item/card/FateGuidanceCardItem.java:104-106  LivingHurtEvent LOWEST 读"当前值"算倍率
float original = ModAttachments.getCurseOriginalAmount(player);
float current = event.getAmount();
float ratio = current > original && original > 0 ? current / original : 1.0f;
```
```java
// F/event/EnderDiceHandler.java:135-143  同一 LivingHurtEvent,HIGH,先乘 1.4
@SubscribeEvent(priority = EventPriority.HIGH)
public static void onLivingHurt(LivingHurtEvent event) { ... event.setAmount(event.getAmount() * RAIN_WATER_DAMAGE_MULTIPLIER); }
```
```java
// F/combat/DiceCombatEvents.java:920-925  污染后的 ratio 被当作"神秘遗物+第一诅咒倍率"使用
registerDiceCombatFactor((attacker, target, damage) -> {
    if (target instanceof Player cursed) {
        float ratio = ModAttachments.getDiceCurseRatio(cursed);
        if (ratio > 1.0f) { damage *= ... ratio ...; }
```
同时注意 `F/combat/DiceCombatEvents.java:917` 的注释仍写着「由 onCurseMitigation 在 LivingIncomingDamageEvent 捕获」——1.20.1 **没有**该事件,注释已失效(以代码为准)。

**建议修法**:1.20.1 侧把"原始值"与"只由神秘遗物+ 产生的倍率"分离(例如在 AttackEvent 记原值后,排除本模组自己在该阶段施加的倍率,或把本模组的雨中/火焰倍率移到 `LivingDamageEvent` 阶段),避免 ratio 混入非诅咒来源的倍率;或在骰战因子内显式扣除已知的本模组倍率。

**两版本**:皆有两套捕获实现,但阶段不同 → 同一场景数值不一致(1.20.1 会把雨中/火焰倍率"抬"进骰战伤害,1.21.1 会丢掉)。

### S1-C4 | **high** | `LivingDeathEvent` 上"末影骰子取消"与"死亡清理"同为默认优先级 → 顺序由类扫描顺序决定;顺序反转时会出现"救活了却已按死亡清理"

**现象**:`EnderDiceHandler.onLivingDeath` 与 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 都未声明优先级(LivingDeathEvent / 默认)。若清理先执行、取消后执行,则玩家被末影骰子救活,但:
- `HealingManager.clear`、`EffectTimerGuard.clear`(N/`event/PlayerLifecycleHandler.java:127-129`)、骰神赐福等效果被移除(`:171-178`)、出牌计数/轮次加成被清(`:155-157`)、立牌/筹码附件清零(`:131-148`)、护法剑气清零(`:163-170`)、`DiceCurioItem.removeGlassDiceOnDeath`(玻璃骰子及其卡牌被销毁,`:126`)已经发生。
- 且 `DeathPreservedBonuses.preserveOnDeath`(N/`:124`)把值存进静态表,直到**下一次**真正的 Clone/Respawn 才回写(N/`component/DeathPreservedBonuses.java:48-58`)。

当前两个已构建产物里 `EnderDiceHandler`(JarN 索引 75)早于 `PlayerLifecycleHandler`(84),所以取消先生效、清理因 `isCanceled()` 检查(`N/event/PlayerLifecycleHandler.java:119`)跳过——**结论当前是好的,但依赖不可契约的顺序**。

**证据**
```java
// N/event/EnderDiceHandler.java:146-157  默认优先级,取消死亡
@SubscribeEvent
public static void onLivingDeath(LivingDeathEvent event) { ... event.setCanceled(true);
```
```java
// N/event/PlayerLifecycleHandler.java:114-119  同样是默认优先级
@SubscribeEvent
public static void onPlayerDeathClearEffects(LivingDeathEvent event) { ... if (event.isCanceled()) return;
```
```java
// N/event/PlayerLifecycleHandler.java:126  会在"被取消的死亡"上执行(若它先跑)
DiceCurioItem.removeGlassDiceOnDeath(player);
```
顺序机制证据见 1.3 节(`BusN` LinkedHashSet + `forEach` 注册;JarN/JarF 索引)。

**建议修法**:把 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 显式降到 **LOW**/**LOWEST**(保命类订阅者(HIGHEST/HIGH)之后、仍在其他默认处理者之后),或在保命类处理器上显式声明 HIGH 并统一约定"死亡清理恒为最低优先级";这样不再依赖扫描顺序。

**两版本**:皆有(`F/event/EnderDiceHandler.java:147-158`、`F/event/PlayerLifecycleHandler.java:111-116`、`F/event/PlayerLifecycleHandler.java:123`)。

### S1-C5 | **medium** | 部分 `LivingDeathEvent` 奖励类订阅者不检查 `isCanceled()` → 末影骰子救活后仍发奖

**现象**:死亡被 `EnderDiceHandler`(或气囊 HIGHEST 兜底)取消后,事件仍会派发给后续同优先级监听器。以下四个不检查取消标志,会照常发奖/记账:

| 订阅者 | 发放内容 | file:line |
|---|---|---|
| `HaiqingSignItem.onWeakMarkKill` | 占星师 +3 星币;击杀者获得「命运的指引」 | `N/item/sign/HaiqingSignItem.java:131-143`(经 `grantWeakMarkKillReward`) |
| `BonnieSignItem.onBonnieKill` | `BaseSignItem.invokeKillHooks(killer, target)` → 秘密侦探被动给牌 + 调查员发活体书页 | `N/item/sign/BonnieSignItem.java:137-143` |
| `SatelliteChipItem.onSatelliteRangedMagicKill` | 探天卫星每 1:00 一次的随机效果牌 | `N/item/chip/SatelliteChipItem.java:98-108` |
| `CursedSwordChipItem.onCursedSwordKill` | 诅咒之剑每次赐福 +1 攻击力 | `N/item/chip/CursedSwordChipItem.java:118-125` |

对照组(有检查,不会发奖):`ChipDamageHandler.java:80`、`EnderDiceHandler.java:151`、`PlayerLifecycleHandler.java:119`、`ElectricSwordChipItem.java:77`、`SmartWatchChipItem.java:49`、`InvestigationEventUtil.java:124`。

**可触发性**:`HaiqingSignItem` 的目标可以是"非队友玩家"(`N/combat/DiceCombatEvents.java:1009-1011` 允许玩家目标施加 `WEAK_MARK`,N/`:234`),该玩家佩戴末影骰子且不在冷却时即命中本冲突。`BonnieSignItem.onBonnieKill` 更是**不要求目标敌对**(`N/item/sign/BonnieSignItem.java:139-142`),任何"玩家击杀玩家"都进入。

**证据**
```java
// N/item/sign/HaiqingSignItem.java:131-141  无 isCanceled 检查
@SubscribeEvent
public static void onWeakMarkKill(LivingDeathEvent event) {
    LivingEntity target = event.getEntity(); ...
    HaiqingSignItem.grantWeakMarkKillReward(applier, killer);
```
```java
// N/item/sign/BonnieSignItem.java:137-143  无 isCanceled 检查
public static void onBonnieKill(LivingDeathEvent event) { ... BaseSignItem.invokeKillHooks(killer, target);
```

**建议修法**:在这四个(以及未来新增的)死亡奖励钩子首行统一加 `if (event.isCanceled()) return;`;更稳妥的是让保命处理器把"已取消"表达为独立信号(如统一的 `DamageResolutionState`),避免每个订阅者各自判断。

**两版本**:皆有(`F/item/sign/HaiqingSignItem.java:130-143`、`F/item/sign/BonnieSignItem.java:136-143`、`F/item/chip/SatelliteChipItem.java:99-108`、`F/item/chip/CursedSwordChipItem.java:116-125`)。

### S1-C6 | **medium** | 同优先级订阅者顺序影响数值:电击手套 AOE 读 `event` 当前值,取"骰战最终值"还是"武器护甲后值"取决于扫描顺序

**现象**:`DamageEffectCardHandler`(默认)与 `DiceCombatEvents.onLivingDamagePre`(默认)订阅同一阶段;电击手套 AOE 用 `ctx.event.getNewDamage()`/`getAmount()` 作为波及伤害:
- 若 `DiceCombatEvents` 先跑 → 读到骰战最终值;
- 若 `DamageEffectCardHandler` 先跑 → 读到"武器伤害经护甲"的值。
当前 JarN(索引 8 < 72)/JarF(8 < 82)对应前者,但无契约。触发条件(同一命中同时被两条链路接管):伤害源 `getEntity()` 为玩家、`getDirectEntity()` 为玩家或法术、法伤作用域命中、且施法者主手是剑/斧/锤/三叉戟并处于赐福(`N/combat/DiceCombatEvents.java:201-212` 只要求"主手近战武器",不要求"这次伤害是近战")。

**证据**
```java
// N/combat/SpellDamageRegistry.java:363-366  读"当前事件值"做 AOE 基数
public void onHit(SpellDamageContext ctx, double bonus) {
    float total = ctx.event.getNewDamage();
```
```java
// N/combat/DiceCombatEvents.java:604  另一条链路会把该值整体替换
event.setNewDamage((float) finalDmg);
```
（1.20.1 对应 `F/combat/SpellDamageRegistry.java:365` 的 `ctx.event.getAmount()` 与 `F/combat/DiceCombatEvents.java:601`。）

**建议修法**:不要读事件当前值;AOE 基数应由本次命中的**本链路自身**计算并显式传递(骰战链路用 `finalDmg`,法伤链路用自身结算值),或给两条链路定义明确优先级(NORMAL 内也显式声明 HIGH/LOW)。

**两版本**:皆有。

### S1-C7 | **medium** | 骰战链路会整体接管"非近战但 `directEntity` 是玩家"的伤害,法伤数值被丢弃,而法伤链路同时仍在注入真伤加成

**现象**:骰战入口只校验 `directEntity instanceof Player` + 主手近战武器(`N/combat/DiceCombatEvents.java:201-212`),不校验伤害类型/是否近战命中;一旦成立且玩家有赐福+骰子,就在 `:604` 用 `finalDmg = max(1, attackPower - defensePower)` 替换整笔伤害 —— 该次法术自身算出的数值被丢弃。同一命中的 `DamageEffectCardHandler` 仍会额外注入一份"法伤加成真伤"(`N/event/DamageEffectCardHandler.java:62-71`),于是同一次命中被两条互不知情的链路同时改写:一笔被替换、一笔被追加。

**证据**
```java
// N/combat/DiceCombatEvents.java:201-212  只有这三个条件就进入骰战
if (!(directEntity instanceof Player player)) return;
if (target == player) return;
...
if (!isMeleeWeaponAttack(player)) return;   // 只看"手持近战武器",不看本次伤害类型
```
```java
// N/event/DamageEffectCardHandler.java:62-66  同一命中另追加一笔真伤
if (bonus > 0 && !APPLYING_TRUE_BONUS.get()) { ... target.hurt(ModDamageTypes.trueDamage(target.level(), player), (float) bonus);
```

**建议修法**:骰战入口补一条"本次伤害是近战命中"的判定(例如要求 `source.getDirectEntity() == source.getEntity()` 且非 `SpellDamageRegistry.isSpellDamage(...)`、或按 `DamageTypeTags.IS_PROJECTILE`/`IS_MAGIC` 排除),把两条链路的适用范围做成互补而不重叠。

**两版本**:皆有(`F/combat/DiceCombatEvents.java:197-208`、`F/event/DamageEffectCardHandler.java:59-68`)。

### S1-C8 | **medium** | 虚弱印记的"骰战跳过"判定按攻击者状态而非本次伤害来源 → 注入型真伤(溅射/法伤 AOE)错误地不吃 +10%/+30%

**现象**:`onWeakMarkDamage` 的跳过条件只检查 `source.getEntity()` 是否为"赐福 + 佩骰 + 手持近战武器"的玩家。注入型真伤用 `trueDamage(level, player)`(`getEntity()==player`、`getDirectEntity()==null`),因此当一个手持近战武器的赐福玩家造成法伤真伤/溅射时,该笔**独立伤害**的虚弱印记倍率被跳过(骰战那一笔早已在 `:590-596` 内部并入过倍率)。

**证据**
```java
// N/combat/DiceCombatEvents.java:946-951  只看攻击者,不看本次伤害是否就是骰战那一笔
if (event.getSource().getEntity() instanceof Player attacker
        && attacker.hasEffect(ModEffects.DICE_BLESSING)
        && attackerHasDiceCurio(attacker)
        && isMeleeWeaponAttack(attacker)) { return; }
...
event.setNewDamage(event.getNewDamage() * multiplier);   // :957
```
注入点:`N/combat/DiceCombatEvents.java:640-652`(溅射)、`N/event/DamageEffectCardHandler.java:65-66`(法伤加成)、`N/combat/SpellDamageRegistry.java:251-252`(定向爆破 AOE)、`:371-372`(电击手套 AOE)。`getEntity()`/`getDirectEntity()` 语义见 VN:61-68。

**建议修法**:跳过条件改为"本次伤害就是骰战结算的那一笔"(例如用一次性标记/TreadLocal 记录骰战已并入倍率的事件实例),而不是用攻击者特征反推。

**两版本**:皆有(`F/combat/DiceCombatEvents.java:940-951`)。

### S1-C9 | **medium** | 安全气囊的"致命"判定在吸收(absorption)前后不一致(跨版本),1.21.1 会空耗充能与冷却

**现象**:1.21.1 的 `LivingDamageEvent.Pre` 在 `ABSORPTION` 折减**之前**派发,气囊用"未扣吸收"的伤害与当前血量比较 → 即使伤害吸收足以吃下这一击也会判定"致命",消耗 6 充能并进入 1:00 冷却。1.20.1 的 `LivingDamageEvent` 在吸收**之后**派发(amount 已扣 absorption),吸收足够时 `damage <= 0` 提前返回,不消耗。

**证据**
```java
// VN:1789-1793  先 onLivingDamagePre,再算 ABSORPTION
float damage = CommonHooks.onLivingDamagePre(this, this.damageContainers.peek());
this.damageContainers.peek().setReduction(Reduction.ABSORPTION, Math.min(this.getAbsorptionAmount(), damage));
```
```java
// VF:1669-1680  f1 = max(amount - absorption, 0) 之后才派发 LivingDamageEvent
float f1 = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F); ... f1 = ForgeHooks.onLivingDamage(this, damageSource, f1);
```
```java
// N/event/ChipDamageHandler.java:51-56  与"当前血量"直接比较
float damage = event.getNewDamage(); if (damage <= 0.0F) return;
if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
```

**建议修法**:1.21.1 侧把"致命"判定改为"扣掉可用伤害吸收后仍会致死",或在气囊成功时不消耗充能而只把该笔伤害压成"由吸收承接"的量;两版本择一统一。

**两版本**:皆有(实现位置不同,口径因此不同)。

### S1-C10 | **medium** | 末影骰子触发时清效果的口径跨版本不同(1.20.1 清空全部效果,1.21.1 只清"图腾可治愈"的效果)

**现象**:1.20.1 `removeAllEffects()`,1.21.1 `removeEffectsCuredBy(PROTECTED_BY_TOTEM)`。前者会把本模组所有增益(骰神赐福、治愈、狂暴、岿然不动、汲取、命运指引、剑气流派附件等)一并抹掉(效果本身),后者不会。两者各自对齐本平台原版图腾行为(1.20.1 原版 `LivingEntity:1270 removeAllEffects()`;1.21.1 原版 `LivingEntity:1329 removeEffectsCuredBy(...)`),但**本模组跨版本表现因此不一致**。

**证据**
```java
// F/event/EnderDiceHandler.java:160-161
player.setHealth(1.0F);
player.removeAllEffects();
```
```java
// N/event/EnderDiceHandler.java:159-160
player.setHealth(1.0F);
player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
```

**建议修法**:若追求功能对等,把两版本都限制为"只清本模组认可的可清除效果"(显式列表或标签),而不是随平台原版行为漂移。

**两版本**:皆有(行为不同)。

### S1-C11 | **low** | 汲取回血在同一命中上有两个不同基数(攻击者侧用局部 `finalDmg`,受伤者侧用事件最终值),且溅射会再触发一次受伤侧回血

**现象**:
- 攻击者侧:`player.heal(max(1, (int) finalDmg / 2))`,基数是骰战局部值,**不含**后续 berserk(+1×层)/weakmark(×1.1/1.3)的修改。
- 受伤者侧:`heal = max(1, (int) event.getNewDamage() / 2)`,基数是 LOWEST 时的**最终事件值**(含上述修改,且若气囊已把值改成 0 则 `damage<=0` 提前 return,不再回血)。
- 大当家溅射会为同一目标再触发一次受伤侧回血(嵌套事件)。

**证据**
```java
// N/combat/DiceCombatEvents.java:676-678  局部 finalDmg 基数
if (!player.level().isClientSide() && player.hasEffect(ModEffects.PAPARA_BITE)) { player.heal(Math.max(1, (int) finalDmg / 2)); }
```
```java
// N/item/sign/PaparaSignItem.java:56-63  LOWEST,事件最终值基数
@SubscribeEvent(priority = EventPriority.LOWEST)
public static void onPaparaBiteHurtHeal(LivingDamageEvent.Pre event) { ... int heal = Math.max(1, (int) event.getNewDamage() / 2);
```

**建议修法**:明确"汲取按哪一次结算值"的口径并统一(建议统一取 LOWEST 的最终值),并对同一次命中的嵌套注入做去重(与 S1-C1 同一根因)。

**两版本**:皆有(1.20.1:`F/combat/DiceCombatEvents.java:672-675`、`F/item/sign/PaparaSignItem.java:56-63`)。

### S1-C12 | **low** | 1.20.1 的取消点(`LivingAttackEvent`)早于 `abilities.invulnerable`/`isDeadOrDying`,补偿不完全

**现象**:1.20.1 的 `ForgeHooks.onLivingAttack` 是 `hurt` 首语句(玩家侧 `Player#hurt:841-842`),早于 `isInvulnerableTo`(:843)、`abilities.invulnerable`(:845)、`isDeadOrDying`(:849);1.21.1 的 `LivingIncomingDamageEvent` 在 `LivingEntity#hurt` 内且玩家侧经 `super.hurt`(VN Player:922-952)。因此 1.20.1 的取消型处理器需要在自身补位判定。已补位:`DiceCombatEvents.isImmuneToDamage`(`F/combat/DiceCombatEvents.java:1081-1088`,用于破绽闪避 `:1102`)与 `AdrenalineChipItem`(`F/item/chip/AdrenalineChipItem.java:91-92`)。**未补位**:`NancyLuSignItem.onNancyLuEnderPearlDamage`(`F/item/sign/NancyLuSignItem.java:296-305` 直接 `setCanceled(true)`)。因该分支只取消摔落伤害(取消即 `hurt` 立刻 return false),副作用限于"创造/濒死/无敌时仍返回 false",无额外伤害/回血,故严重度低。

**证据**
```java
// F/combat/DiceCombatEvents.java:1081-1087
public static boolean isImmuneToDamage(LivingEntity target, DamageSource source) {
    if (target.isInvulnerableTo(source)) return true;
    if (target instanceof Player player && player.getAbilities().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
    return target.isDeadOrDying();
```
```java
// F/item/sign/NancyLuSignItem.java:296-304  无补位
public static void onNancyLuEnderPearlDamage(LivingAttackEvent event) { ... event.setCanceled(true);
```

**建议修法**:1.20.1 三个取消点统一走 `isImmuneToDamage` 前置检查(把该判定上移到 `applyDodgeCancel` 内部亦可)。

**两版本**:仅 1.20.1 存在该阶段差异(1.21.1 由平台阶段天然覆盖)。

### 3.1 判定为「无冲突」的订阅点及依据(逐条给出不冲突的代码理由)

| 订阅点 | 判定 | 依据 |
|---|---|---|
| `ChipDamageHandler.onLivingDeath`(HIGHEST 兜底)vs 末影骰子 | 无重复取消、无重复回血 | HIGHEST 恒早于默认优先级;ChipDamageHandler 成功取消后,`EnderDiceHandler` 因 `:151 if (event.isCanceled()) return;` 直接返回,`setHealth(1.0F)` 只在一处执行;若气囊在伤害阶段已无效化则不产生死亡事件,N/`event/ChipDamageHandler.java:69-73` 所述路径无叠加 |
| `DeathPreservedBonuses` 的暂存/回写 | 无重复加成 | 静态表 `PRESERVED.remove(uuid)` 取走即删(`N/component/DeathPreservedBonuses.java:50-51`),且回写用 `if (preserved[i] > 当前值)` 的单调取大(`:53-58`);Clone(LOWEST)与 Respawn 两次调用幂等 |
| `BaseSignItem.invokeHurtHooks` / `BufferShieldChipItem.onHurt` 在嵌套注入中重复调用 | 无重复收益 | 唯一覆写 `onHurt` 的是 `LuluSignItem`,其自带 20 tick 去重(`N/item/sign/LuluSignItem.java:46-50`);缓冲盾牌自带 15 秒冷却(`N/item/chip/BufferShieldChipItem.java:41-43`) |
| 闪避取消路径(`applyDodgeCancel`)重复派发受击钩子 | 无重复 | 取消后 `hurt` 立即返回,不会再进入 `actuallyHurt`,因此 `LivingDamageEvent(.Pre)` 不再派发(N/`combat/DiceCombatEvents.java:1070`;`F/combat/DiceCombatEvents.java:1064`) |
| `ObsidianDiceHandler`(火焰 -70%)与护甲/抗性 | 无叠加异常 | 1.21.1 在 `LivingIncomingDamageEvent`(护甲前)乘 0.3,1.20.1 在 `LivingHurtEvent`(护甲前)乘 0.3,与护甲/抗性/保护附魔均为乘法链,顺序无关 |
| `IronSpellbooksCompat.onChangeMana` | 与伤害管线无关 | 订阅 `ChangeManaEvent`,仅改魔力(`N/event/IronSpellbooksCompat.java:16-27`;`F/...:14-25`),不接触伤害事件 |
| `DiceCombatEvents.onLivingChangeTarget` | 与伤害顺序无关 | 订阅 `LivingChangeTargetEvent`(`N/combat/DiceCombatEvents.java:785-786`),只改索敌目标 |
| 大当家溅射对"非玩家敌对目标" | 无中途取消 | `aoeProcessing` 只影响骰战结算,不会取消;气囊仅对 Player 生效(`N/event/ChipDamageHandler.java:49`),故 C1 的"气囊被空耗"只发生在主目标是玩家时 |

---

## 4. 跨版本对等性差异

| # | 维度 | 1.21.1 | 1.20.1 | 代码证据 |
|---|---|---|---|---|
| D1 | 最前置事件 | `LivingIncomingDamageEvent`(可取消,`getOriginalAmount()/getAmount()/setAmount()`) | `LivingAttackEvent`(可取消,只有 `getAmount()`,原始值需自己记录) | VN:1152-1153 / VF:1088-1090;`N/item/card/FateGuidanceCardItem.java:85-87` / `F/item/card/FateGuidanceCardItem.java:86-96` |
| D2 | 最终值事件 | `LivingDamageEvent.Pre`(在 `ABSORPTION` 之前) | `LivingDamageEvent`(在 `ABSORPTION` 之后,"FINAL value") | VN:1789-1793 / VF:1669-1683 → 衍生 S1-C9 |
| D3 | 火焰减伤阶段 | `LivingIncomingDamageEvent`,**HIGH** | `LivingHurtEvent`,默认(NORMAL) | `N/event/ObsidianDiceHandler.java:29` / `F/event/ObsidianDiceHandler.java:28`(**注**:1.21.1 类注释写 "HIGHEST+1",与代码不符) |
| D4 | 雨中/水下 +40% 阶段 | `LivingDamageEvent.Pre`(**护甲后**) | `LivingHurtEvent`(**护甲前**) | `N/event/EnderDiceHandler.java:134-143` / `F/event/EnderDiceHandler.java:135-144` → 同一 +40% 在 1.20.1 会被护甲削弱,1.21.1 不会 |
| D5 | 七咒倍率捕获 | 一个订阅点在 `LivingIncomingDamageEvent` LOWEST,直接用 `getOriginalAmount()` | 两点组合:`LivingAttackEvent` HIGHEST 记原值 + `LivingHurtEvent` LOWEST 算 ratio;额外内存态附件 `curse_original_amount` | `N/item/card/FateGuidanceCardItem.java:85-117`;`F/item/card/FateGuidanceCardItem.java:86-126`;`F/component/ModAttachments.java:244` → 衍生 S1-C3 |
| D6 | 印记/狂暴加成的读取对象 | 同一文件 `LivingDamageEvent.Pre`,依赖注册顺序 | 同一文件 `LivingDamageEvent`,依赖注册顺序 | 两版本结构同构(`N/combat/DiceCombatEvents.java:894/940` vs `F/...:888/934`) |
| D7 | 伤害容器栈泄漏修补 | 有 `mixin/fixes/LivingEntityDamageContainerMixin`(HEAD 记栈深、RETURN 截断;两个注入器均 `require = 0`) | 无对应(Forge 无 `DamageContainer` 栈) | `N/mixin/fixes/LivingEntityDamageContainerMixin.java:42-78`;配置 `neoforge-1.21.1/src/main/resources/astral_dice.neoforge_fixes.mixins.json:2`(`"required": false`)、`:9-11`(`defaultRequire: 0`);对照 VN:1152-1153(`push` + 取消即 `return false`)与 VF:1088-1090(无容器栈) |
| D8 | 末影骰子清效果 | `removeEffectsCuredBy(PROTECTED_BY_TOTEM)` | `removeAllEffects()` | `N/event/EnderDiceHandler.java:160` / `F/event/EnderDiceHandler.java:161` → S1-C10 |
| D9 | 取消点前置判定 | 平台事件天然晚于无敌/濒死判定 | 需自补 `isImmuneToDamage`(已用于破绽闪避/肾上腺素,未用于末影珍珠免疫) | `F/combat/DiceCombatEvents.java:1081-1088,1102`;`F/item/chip/AdrenalineChipItem.java:91-92`;`F/item/sign/NancyLuSignItem.java:296-305` → S1-C12 |
| D10 | 法伤 AOE 取值 API | `event.getNewDamage()` | `event.getAmount()` | `N/combat/SpellDamageRegistry.java:365` / `F/combat/SpellDamageRegistry.java:365` |
| D11 | 真伤/`bypasses_armor` 资源 | 同内容 | 同内容 | `neoforge-1.21.1/src/main/resources/data/astral_dice/damage_type/true_damage.json:1-5`、`.../data/minecraft/tags/damage_type/bypasses_armor.json:1-5`;`forge-1.20.1/...` 两文件逐字节同内容 |
| D12 | 死亡奖励钩子漏检 `isCanceled` | 四个 | 同样四个 | `N/item/sign/HaiqingSignItem.java:131`、`N/item/sign/BonnieSignItem.java:137`、`N/item/chip/SatelliteChipItem.java:98`、`N/item/chip/CursedSwordChipItem.java:118` 与 `F/` 对应行 → S1-C5 |

### 4.1 代码与注释不一致处(仅记录,结论以代码为准)

1. `N/event/ObsidianDiceHandler.java:14` 注释「在 LivingIncomingDamageEvent(HIGHEST+1)」——代码是 `EventPriority.HIGH`(`:29`)。
2. `F/combat/DiceCombatEvents.java:917` 注释「由 onCurseMitigation 在 LivingIncomingDamageEvent 捕获」——1.20.1 无该事件,实际为 `LivingHurtEvent`(`F/item/card/FateGuidanceCardItem.java:94-96`)。
3. `N/event/ChipDamageHandler.java:21-22`/`F/...:21` 的"生命扣减尚未发生"对 1.21.1 成立(VN:1789 在扣血前),但 1.20.1 阶段已扣 absorption(VF:1669-1670)——注释未体现该差异。

---

## 5. 未能判定 / 需人工确认

1. **同优先级实际顺序在 dev 与正式包中是否一致 —— 未判定**。已证实顺序来源是"类扫描顺序 = 模组文件条目顺序"(1.3 节),并在两个**已构建 jar**(`JarN`/`JarF`)中实测 `EnderDiceHandler` 早于 `PlayerLifecycleHandler`、`DiceCombatEvents` 早于 `DamageEffectCardHandler`、`ChipDamageHandler` 早于 `PaparaSignItem`。但 `runClient`(dev)从 `build/classes/java/main` 目录扫描,目录枚举顺序与 Gradle 打包顺序未必一致;两个加载器都不承诺该顺序。**需要一次实机日志确认**(可用 FML 的 `Attempting to inject @EventBusSubscriber classes…` DEBUG 日志或临时探针),否则 S1-C1/C4/C6 的"当前孰先"只能按 jar 证据表述。
2. **神秘遗物+(enigmaticlegacyplus)施加第一诅咒倍率的事件与优先级 —— 未判定**。本仓库不含该模组源码;1.21.1 的捕获假定它发生在 `LivingIncomingDamageEvent` 且早于本模组 LOWEST,1.20.1 假定它发生在 `LivingHurtEvent` 且早于 LOWEST(`F/item/card/FateGuidanceCardItem.java:79-85` 的推断未经外部验证)。若其真实阶段不同,S1-C3 的数值影响需重新评估。
3. **`isSpellDamage` 白名单的覆盖度 —— 未判定**。S1-C7/C8 的触发范围取决于第三方模组法伤是否被 `SpellDamageRegistry.isSpellDamage` 判为真(N/`combat/SpellDamageRegistry.java:117-123,159-174`);未逐一核对全部被列出的模组伤害类型是否真实存在(它们出现在硬编码列表里,但缺失类型只会静默不匹配,不会报错)。
4. **口径是否"故意如此" —— 需用户裁定**:(a) 气囊对 `BYPASSES_INVULNERABILITY`(`/kill`、虚空)生效而末影骰子与原版图腾不生效(S1-C10 相关,`N/event/ChipDamageHandler.java:45-66` 无 tag 判定 vs `N/event/EnderDiceHandler.java:152-153` 有);(b) 溅射先于"最终伤害判定者"落地(S1-C1)是否为有意的"溅射独立结算";(c) 1.20.1 的 `removeAllEffects` 是否作为平台对齐而刻意保留(S1-C10)。
5. **`LivingDamageEvent.Post`(1.21.1)是否可用作注入延迟点 —— 未判定**。本仓库零订阅者(grep 无命中),因此其在本仓库的可用性与与 `DamageContainer` 的交互未经验证;S1-C1 的建议修法若要落到该事件,需要一次实机验证。
6. **`invulnerableTime` 清零对主目标的副效应 —— 未判定**。N/`combat/DiceCombatEvents.java:648-656` 在 finally 中恢复 `invulnerableTime`,但嵌套 `hurt` 已改写 `lastHurt`(VN:1190-1204 的赋值顺序),本审计只做静态阅读,未评估"同一 tick 内多次注入后 `lastHurt` 残留"对后续命中的影响(需要游戏内行为测试)。
7. **1.21.1 `LivingIncomingDamageEvent` 的取消与 `damageContainers` 栈泄漏是否已被上游修复 —— 未判定**。`N/mixin/fixes/LivingEntityDamageContainerMixin.java:16-40` 的注释声明上游已修但未 backport;本机 `neoforge-21.1.235` 反编译源码(VN:1152-1153)显示**取消分支仍然直接 `return false`、没有 `pop`**,故该补丁在 21.1.235 上是必需的(与注释一致);更高版本行为未核对。
