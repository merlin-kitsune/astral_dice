# B13 — `item/sign/ParunanSignItem.java` fabric-1.20.1 按 forge 蓝本整类重写

基准线 `forge-1.20.1` → 目标线 `fabric-1.20.1`；日期 2026-11-01。

## 1. 结论

- `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java` 已按 forge 蓝本整体重写：**114 行 / 6217 B / CRLF 114 / bareLF 0 / bareCR 0 / BOM false / 末尾带 CRLF**。
- 主动 = 「网络购物」远程开商店（`CardShopOpener.open(player, true)`），不施加效果、不消耗星光、不进锁定态；冷却链 `ACTIVE_COOLDOWN_SECONDS = 120` + `activeCooldownBaseTicks` / `activeCooldownBaseTicksNoBattery`。
- 旧 1.4.x 语义（星光兑换 + 三选一随机增益 + 锁定态 + 每 N 秒 +1 星光 + `gainStarlightOnBlessing`）已全部移除。
- 连带删除 fabric 侧唯一调用点 `combat/DiceCombatEvents.java` 的赐福调用块与 import（对位 forge 同名位置）。
- 前置确认通过：`shop/CardShopOpener.open(Player, boolean viaActiveSkill)` 存在且与 forge 逐字同签名，未改 `shop/**`。

## 2. 逐区块对照（forge 行号 → fabric 新行号）

| forge | fabric | 区块 | 差异 |
|---|---|---|---|
| 1-2 | 1-2 | package + 空行 | 逐字（整体 -1 行偏移起点） |
| 3-4 | 4 | `ChargeManager` import | 逐字 |
| 4 | — | `item/CurioSlotUtil` import | **未搬运**：forge 侧即为未使用残留 |
| 5-7 | 5-7 | `ModItems` / `CardShopOpener` / `GameplayConstants` | 逐字 |
| 8 | — | `starengine.item.CuriosCompat` import | **未搬运**：forge 侧未使用残留 |
| — | 3 | `compat.curios.CuriosApi` import | **新增**：本线平台写法（替代 forge 的 `SignEquipCheck`） |
| 9-12 | 8-11 | `InteractionResultHolder` / `Player` / `ItemStack` / `Level` | 逐字 |
| 14-28 | 13-27 | 类头 javadoc 前半（主动「网络购物」+ 2026-10-06 裁决段） | 逐字（含 `{@value #ACTIVE_COOLDOWN_SECONDS}`） |
| 29-34 | 28-33 | 「1.20.1 移植（按 1.21.1 新版语义整体重写本文件）」段 | 仅 `Forge 移植` → `Fabric 移植` |
| 35-47 | 34-46 | 被动五条 `<ol>` + 2026-10-08 裁决段 | 逐字（含 `10%%` / `50%%` 双写百分号） |
| 48 `*/` | 49 `*/` | javadoc 收尾 | fabric 多出 47-48 两行 |
| — | 47-48 | 新增末行 | `<p>fabric-1.20.1:本文件原为 1.4.x 旧版（星光兑换 + 三选一随机增益 + 锁定态）,本次按 forge 蓝本整体对齐。` |
| 49 | 50 | `public class ParunanSignItem extends BaseSignItem {` | 逐字 |
| 51-52 | 52-53 | `private static final int ACTIVE_COOLDOWN_SECONDS = 120;` + 注释 | 逐字 |
| 54-56 | 55-57 | 构造器 `super(properties)` | 逐字 |
| 58 | 59-65 | `isEquipped` javadoc | fabric 扩写：保留原一行 + 追加 `<p><b>平台差异(fabric-1.20.1)</b>…`（本线无 `SignEquipCheck`、改用 `CuriosApi`、与 `MemberRecommendationChipItem#isEquipped` 同型且**不**加骰子总闸门） |
| 59-61 | 66-70 | `isEquipped` 方法体 | forge `SignEquipCheck.isEquipped(player, ModItems.PARUNAN_SIGN.get())` → fabric `CuriosApi.getCuriosInventory(player).map(h -> h.findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get())).isPresent()).orElse(false)`；签名 `public static boolean isEquipped(Player)` 不变 |
| 63-66 | 72-75 | `handleUse` javadoc | 逐字 |
| 67-76 | 76-85 | `handleUse` | 逐字，仅 `level.isClientSide` → `level.isClientSide()`（本线方法写法）；`CardShopOpener.open(player, true);` 与 `任务T §3（2026-10-10 用户裁决）` 两行注释按原序保留 |
| 78-81 | 87-90 | `activeCooldownBaseTicks` javadoc | 逐字 |
| 82-89 | 91-98 | `activeCooldownBaseTicks` | 逐字（`ACTIVE_COOLDOWN_SECONDS * 20` / `GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS` / `ChargeManager.signCooldownTicks`） |
| 91-95 | 100-104 | `activeCooldownBaseTicksNoBattery` javadoc | 逐字 |
| 96-103 | 105-112 | `activeCooldownBaseTicksNoBattery` | 逐字（仅 `signCooldownTicksBase`） |
| 104-106 | 113-114 | 类收尾 `}` | 逐字 |

members 行号偏移：`isEquipped` 之后统一 +9（javadoc 扩写 +6 行、三元体 +2 行、插入行 +1 行）。`python difflib` 全文比对 FORGE vs FABRIC 实得差异仅上表所列 5 处：`+CuriosApi` / `-CurioSlotUtil` / `-CuriosCompat` / `Forge→Fabric` 措辞 / `+1` 条 javadoc 末行，外加 `isEquipped` 平台改写与 `isClientSide()`；无意外漂移。

## 3. 删除清单

成员（旧 114 行版 → 现文件 0 处）：`@Override onCurioTick(...)`（每 `GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS` +1 星光）、`private static final List<MobEffect> LOCK_GATE_EFFECTS`、`@Override startActiveLockOnUse(Player, long)`、`@Override isGateEffectActive(Player)`、`public static void gainStarlightOnBlessing(Player, int)`、`handleUse` 内旧主动体（`StarLightManager.get(...)`、`ResourceConversion.starlightToStarCoins(player, -1)`、`ThreadLocalRandom.current().nextInt(3)` 三选一 SATURATION/LUCK/HERO_OF_THE_VILLAGE、`EffectTimerGuard.apply(...)`、`beginActiveLock(stack, player)`）。

import（逐个确认改后无残留引用再删）：`EffectTimerGuard`、`MobEffectInstance`、`MobEffects`、`CurioSlotUtil`、`SlotContext`、`ThreadLocalRandom`、`ResourceConversion`、`StarLightManager`。

`LOCK_GATE_EFFECTS` / `startActiveLockOnUse` / `isGateEffectActive` 三个名字**保留在 `BaseSignItem` 基类**（:183 / :387 / :402 / :413），Papara / NancyLu / Misaki / Komachi / Jasmine / Fen / Fanny / Ame 等立牌仍在使用；经商立牌改为走基类默认（`startActiveLockOnUse` → false、`isGateEffectActive` → false）＝ 不进锁定态，与 forge 同义。

调用点：`fabric-1.20.1/.../combat/DiceCombatEvents.java:523`（原）`ParunanSignItem.gainStarlightOnBlessing(player, baseDice);` → 按 forge `DiceCombatEvents.java:896-897` 对位替换为同 2 行注释（现 fabric `:518-519`）：

- `// ⚠️ 2026-10-08 用户裁决「重写经商立牌被动」:原「触发骰神赐福后立即获得 骰点×2 星光」已移除`
- `// (被动「传奇商人」改为商店/兑换向的五条内容,见 shop/CardShopMenu 与 AGENTS 立牌技能表)。`

并同步删除 `import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;`。该文件现 1921 行 / 126481 B / CRLF 1921 / bareLF 0 / BOM false；`triggeredBlessing` 在 :459 / :540 / :897 另有使用者，删除调用块未产生孤儿变量。

## 4. `gainStarlightOnBlessing` 全仓 grep（改后实查）

- fabric 线：**0 处可执行引用**（仅 `ParunanSignItem.java:44` javadoc 描述性提及，属蓝本原文）。
- `neoforge-1.21.1` / `forge-1.20.1`：只剩 javadoc 提及，方法定义已无。
- `neoforge-26.1.2`：**仍存在**旧实现 `ParunanSignItem.java:142` 与调用 `DiceCombatEvents.java:557`；该线不在本批范围（AGENTS 记「26.1.2 / fabric 待移植」），未动，仅记录。
- `docs/` 与 `AGENTS.md.bak-*` 历史条目提及：忽略。

其余 `ParunanSignItem` 引用点（改后仍在，均只依赖保留下来的 `isEquipped`）：`shop/CardShopMenu.java:299 / :581 / :679`、`screen/CardShopScreen.java:1107`、`item/ModItems.java:421`（注册）。编译面无破坏。

## 5. 自检原始输出

字节复核（改后实测）：
```
ParunanSignItem.java bytes=6217 BOM=False CRLF=114 bareLF=0 bareCR=0 endsCRLF=True lines=114
DiceCombatEvents.java bytes=126481 BOM=False CRLF=1921 bareLF=0 bareCR=0 endsCRLF=True lines=1921
```
`python scripts/__javac_check.py`：
```
EXIT 1 ERRORS 171 jars 685 srcs 519
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```
`ERRORS 171` / `164 NOISE_AW` / `7 NOISE_IFACE_INJECTION` 与改动前基线逐项相同，`REAL errors` 段为空。

`python tools/verify_fabric_assets.py`（exit 0）：
```
[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980
[6] 手册闭环: 条目 157 / 引用配方 138
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
=== FAIL ===
  ✗ 方块物品资源不全(blockstate=['card_shop', 'chip_shop', 'event_drawer'] / model=[...] / texture=[...])
  ✗ 缺模型(紫黑格): ['abyss_reagent_chip', 'alana_sign', ..., 'sensor_chip']
  ✗ 缺贴图: [... 同上 17 项]
```
FAIL 段 3 项全部是**构建前既有**的资源/旧 jar 噪声（对象为 `build/libs/astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`），与 `porting/fabric-1.20.1/B0-TAIL-REPORT.md:92-94`、`porting/fabric-1.20.1/B11-WEIRD-DICE-DRIFT-REPORT.md:105-107` 记录逐项一致；语言/手册两类**未新增 FAIL**。

`PYTHONIOENCODING=utf-8 python tools/check_lang_sync.py --line fabric-1.20.1`：
```
[OK]   fabric-1.20.1: en_us=980 键，3 份语言键集一致
```

## 6. 未做 / 无法判定

1. **孤儿 lang 键（按要求不删，仅报告）**：`msg.astral_dice.parunan_active_no_starlight` 在 fabric 全部 java 源码中已 **0 引用**（grep 确认），保留原键待键集守门批次统一处理。
2. **tooltip 文案仍是旧版（未做）**：`fabric-1.20.1/.../event/ModTooltipHandler.java:667-673` 仍连线 `tooltip.astral_dice.sign.parunan_active` / `parunan_passive`（`:670` 还传 `formatSignTime(GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS)`）/ `parunan_starlight`；forge 同名位置 `ModTooltipHandler.java:686-690` 已不带该参数 ⇒ fabric 的 tooltip 正文与 3×2 条 lang 值仍是「套现 / 三选一 / 每 %s 秒 +1 星光」。跨语言文件，按本批硬性要求（不改/不删 lang 键）不在本批执行。
3. **手册条目同上**：`astral_dice.guide.entry.parunan_sign.1/.2` 仍是旧被动描述（套现 / 三选一 / 每 N 秒 +1 星光），未改。
4. **`BaseSignItem.java:455-456` 注释过期**：仍写「本线现为 Dorothy / Sherry —— Parunan 的覆写尚未移植」；本次已移植，但该修正落在禁止改动的 `BaseSignItem.java`，仅报告。
5. **`resource/ResourceConversion.java:58` javadoc** 仍称「`ParunanSignItem` → `starlightToStarCoins` 同样经过它」；纯注释陈述、该类仍被商店自身使用，代码行为不受影响，未改。
6. **行为验证未做**：按硬性要求未启动游戏/gradle；进店链路（+1 星币、战斗牌 +1、10%% 降价、转账降价、兑换 +50%）为 forge 蓝本既有逻辑，本批只做逐字对齐与编译面核对，未做实机断言。
7. **`neoforge-26.1.2` 仍有旧 `gainStarlightOnBlessing`**（见 §4），跨线移植未做。

## 7. 改动面核对（mtime 证据：禁改面未触碰）

本批仅写 3 个文件：`item/sign/ParunanSignItem.java`（19:12:18）、`combat/DiceCombatEvents.java`（19:12:28）、本报告（19:14:39）。禁改面 mtime 均早于本批：`fabric-1.20.1/build.gradle` 18:50:27、`item/sign/BaseSignItem.java` 19:06:25、`event/WeirdDiceHandler.java` 19:06:02、`shop/CardShopMenu.java` 18:03:40、`gradle.properties` 2026-09-22 18:03:49。未跑 gradle / 游戏 / datagen / git commit，未删除任何文件。
