# B11 — 诡异骰子 1.5.0 三处真漂移 fabric 补齐报告

- 仓库：`D:/MCProject/astral_dice_multiloader`，分支 `multi-main`（工作树四条线同仓）。
- 基准线：`forge-1.20.1`（只读参考，本轮未改动）；目标线：`fabric-1.20.1`（本轮唯一改动侧）。
- 目标：把 forge 已落地的 2026-10-21「诡异骰子立牌主动冷却 -50% 已删除」三处行为漂移逐条移植到 fabric。
- 改动文件 17 个，全部位于 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`。
- 未运行 gradle / 未启动游戏 / 未 datagen / 未 commit / 未 git add / 未删除任何文件；未触碰 `build.gradle`、`gradle.properties`、`AstralDiceMod.java`。

## 一、漂移 1：立牌主动冷却 −50% 从 WeirdDiceHandler 删除

| forge 行号 | 内容 | fabric 新行号 |
|---|---|---|
| `forge-1.20.1/.../event/WeirdDiceHandler.java:39-48` | `signCooldownTicks` 单行转调 `ChargeManager.signCooldownTicks`，javadoc 明写「**不提供**诡异骰子的 -50%」 | `fabric-1.20.1/.../event/WeirdDiceHandler.java:39-48` |
| `forge-1.20.1/.../event/WeirdDiceHandler.java:15-19` | 类头功能清单不含「冷却 −50%」，改为「立牌主动技能**结束**时恢复 5 点生命值」 | `.../event/WeirdDiceHandler.java:15-19` |

- fabric 原 `if (hasWeirdDice(player)) ticks = Math.max(1, ticks / 2);` 已删除，方法体现在为：
  `return (int) ChargeManager.signCooldownTicks(player, GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);`
- 平台差异保留：`hasWeirdDice` 仍用本线 `astral_dice.compat.curios.CuriosApi`（未照抄 forge 的 `starengine` Curios 口径），`rollD6` 未动。

## 二、漂移 2：立牌主动结束后回 5 点血（fabric 原本完全没有）

| forge 行号 | 内容 | fabric 新行号 |
|---|---|---|
| `forge-1.20.1/.../event/WeirdDiceHandler.java:24-28` | 常量 `HEALTH_ON_ACTIVE_END = 5` | `fabric-1.20.1/.../event/WeirdDiceHandler.java:24-27` |
| `forge-1.20.1/.../event/WeirdDiceHandler.java:89-93` | `onSignActiveEnded(Player)`：null/客户端/未佩戴三重早退 + `player.heal(5)` | `.../event/WeirdDiceHandler.java:84-93` |
| `forge-1.20.1/.../item/sign/BaseSignItem.java:193-194` | `boolean enteredLock = sign.startActiveLockOnUse(player, now); if (enteredLock) { ... }` | `fabric-1.20.1/.../item/sign/BaseSignItem.java:183-184` |
| `forge-1.20.1/.../item/sign/BaseSignItem.java:215-219` | 瞬时型立牌：`if (!enteredLock) WeirdDiceHandler.onSignActiveEnded(player);` | `.../item/sign/BaseSignItem.java:207-211`（调用在 `:210`） |
| `forge-1.20.1/.../item/sign/BaseSignItem.java:296` | 目标选择器出口：`if (getSignActiveLockSignId(player).isEmpty()) onSignActiveEnded(player);` | `.../item/sign/BaseSignItem.java:287-291`（调用在 `:290`） |
| `forge-1.20.1/.../item/sign/SignActiveLockMachine.java:122-124` | 锁型立牌 `endLockAndStartCooldown` 出口调用 | `.../item/sign/BaseSignItem.java:534-536`（调用在 `:536`；本线该方法留在 BaseSignItem，无 SignActiveLockMachine） |

- `player.level().isClientSide` 字段写法按本线改成 `player.level().isClientSide()`；其余逐字对齐。
- 必要结构性改动：fabric 原为内联 `if (sign.startActiveLockOnUse(player, now)) {`，为了让 `enteredLock` 可复用于第 6 步判断，改成先存局部变量再判断（与 forge 同形），未改变控制流。
- forge 该方法 javadoc 里「治愈走 HealingManager，**不直接改生命值**」的句子与自身方法体（`player.heal`）互相矛盾，属 forge 侧陈旧文案，**未照抄**；本线 javadoc 按方法体实际语义（直接回血）撰写。

## 三、漂移 3：`signCooldownTicksBase` / `sign_active_max_cooldown_base` / 命运指引卡取值链

| forge 行号 | 内容 | fabric 新行号 |
|---|---|---|
| `forge-1.20.1/.../item/ChargeManager.java:146-159` | 新增 `signCooldownTicksBase(Player,long)`（只做充能封顶，不乘额外电池 ×0.5） | `fabric-1.20.1/.../item/ChargeManager.java:140-147` |
| `forge-1.20.1/.../component/ModAttachments.java:616` | 附件键 `sign_active_max_cooldown_base`（`Codec.LONG`，`0L`，无 `.sync()`、不进 SYNCED 名单） | 本线**已在** `fabric-1.20.1/.../component/SignAttachments.java:47-48`（同参数、同无 `.sync()`、未登记 SYNCED），本批未新增 |
| `forge-1.20.1/.../component/ModAttachments.java:604-610` 附近 | 访问器 `get/setSignActiveMaxCooldownBase` | `.../component/SignAttachments.java:50-56`（既有） |
| `forge-1.20.1/.../item/card/FateGuidanceCardItem.java:75-87` | 三段取值链：`getSignActiveMaxCooldownBase` → `getSignActiveMaxCooldown` → `ChargeManager.signCooldownTicksBase(...)` | `fabric-1.20.1/.../item/card/FateGuidanceCardItem.java:66-78` |
| `forge-1.20.1/.../item/sign/BaseSignItem.java:429-432` | `activeCooldownBaseTicksNoBattery` 单行委派 | `fabric-1.20.1/.../item/sign/BaseSignItem.java:463-465`（原为内联封顶 + TODO，整段平台差异 javadoc 已删） |
| `forge-1.20.1/.../item/chip/CurrentCoreChipItem.java:107-109` | 写 0：`setSignActiveMaxCooldown(player, 0)` + `setSignActiveMaxCooldownBase(player, 0L)` | `fabric-1.20.1/.../item/chip/CurrentCoreChipItem.java:126-128` |
| `forge-1.20.1/.../item/sign/LuluSignItem.java:129-133` | 成对写 max 与 base（base 走 `signCooldownTicksBase`） | `fabric-1.20.1/.../item/sign/LuluSignItem.java:123-127` |

- 全 `fabric-1.20.1/src/main/java` 复查结果：`sign_active_max_cooldown` 的写点为 BaseSignItem（2 处 + 锁结束 1 处）、CurrentCoreChipItem、Lulu；据此认定只有 CurrentCoreChipItem 与 Lulu 两处缺 `_base` 配对写，均已补。
- Megas / Moses / Dorothy / Haiqing / Bonnie / Zhao / Jill / Teru / Ren 在 **forge 侧同样只写 max 不写 base**（Dorothy/Jill/Teru 等靠 `activeCooldownBaseTicksNoBattery` 覆写间接生效），故本批**未**给它们加写点，避免造出 forge 没有的行为。

## 四、brief 之外、同源的额外残留（已一并处置）

1. **三处立牌自带复制的减半逻辑**（漂移 1 的漏网点；只改 Handler 的话它们仍会减半）：
   - `fabric-1.20.1/.../item/sign/SherrySignItem.java:220-229` 删除本地 `if (hasWeirdDice(...)) ticks = Math.max(1, ticks / 2);` ⇒ 对应 forge `SherrySignItem.java:216-222`（forge 无该块）。
   - `fabric-1.20.1/.../item/sign/MosesSignItem.java:141-143` 同上删除 ⇒ forge `MosesSignItem.java:133-139`。
   - `fabric-1.20.1/.../item/sign/MegasSignItem.java:176-178` 同上删除 ⇒ forge `MegasSignItem.java:167-173`。
   - 三个文件的 `import ...event.WeirdDiceHandler;` 随之成为无用导入，已删（forge 侧本就无该导入）；三者的 `signCooldownTicks` 现分别收在 `SherrySignItem.java:223` / `MosesSignItem.java:142` / `MegasSignItem.java:177`。
2. **Dorothy 的 `activeCooldownBaseTicksNoBattery` 覆写已补齐**：该文件原 javadoc ④ 明写「本线 BaseSignItem 没有该虚方法 ⇒ 删掉覆写防编译失败」。本批新增该虚方法后，若继续不覆写，本立牌自定义基准 `COOLDOWN_TICKS = 2400` 会被全局常量 9000(=180s) 顶替，`_base` 附件写错值。现补 `fabric-1.20.1/.../item/sign/DorothySignItem.java:95-103`（对应 forge `DorothySignItem.java:59-67`），并把 ④ 改写为「已补齐」。
3. **Sherry 成对覆写**：`fabric-1.20.1/.../item/sign/SherrySignItem.java:226-238` 新增 `activeCooldownBaseTicksNoBattery`（对应 forge `SherrySignItem.java:224-236`），镜像既有 `activeCooldownBaseTicks` 仅换 `signCooldownTicksBase`。
4. **陈旧注释对齐**（forge 已改口径，fabric 仍宣称已被删除的 −50%）：
   - `BonnieSignItem.java:80`、`RenSignItem.java:86`、`HaiqingSignItem.java:67`、`ZhaoSignItem.java:166`、`TeruSignItem.java:158`：「含诡异骰子 -50% 与充能递减」⇒「含充能封顶与额外电池筹码减成」。
   - `LuluSignItem.java:116` 同句替换（forge `LuluSignItem.java:122` 已是新口径）。
   - `ChargeManager.java:19`：「其它减免(诡异骰子 -50% 等)」⇒「其它减免(额外电池筹码等)」；`:121` ⇒「其它减免(额外电池筹码 ×0.5、枪匠「精密技巧」的 120 秒基础值等)」。
   - `ModItems.java:207` 诡异骰子注释：去掉「立牌主动冷却 -50%」，按 forge `ModItems.java:211` 改成「战斗骰低点数(1-3)概率提升 50%,立牌主动技能结束后恢复 5 点生命值」。
   - `component/ModAttachments.java:1182`（MAMUSHI_FORCED_COOLDOWN_UNTIL javadoc）：「后者可被诡异骰子/电流核心等减免」⇒「后者可被电流核心等减免」。

## 五、未做 / 无法判定

1. **Parunan 立牌的 no-battery 覆写未补**：本线 `ParunanSignItem` 连 `activeCooldownBaseTicks` 覆写都没有（forge `ParunanSignItem.java:83/:97` 两个都有），属此前批次遗留缺口（见 `porting/fabric-1.20.1/B4-FOLLOWUP-REPORT.md`）。只补 `_base` 一个会造成半套覆写，故整体留待该缺口单独处理。
2. **forge 的 `skipCooldownAfterLock()` 分支未移植**：forge `SignActiveLockMachine.java:99/102/110` 的该分支此前批次已判定不适用本线（本线 mamushi 走 `forcedActiveCooldownTicks` / `forcedCooldownUntil`），`BaseSignItem.java` 内既有注释已说明，本批沿用不改。
3. **forge 自身陈旧文案按原样保留**（与本线同文本 = 一致，不属漂移）：`NardisSignItem.java:72`（forge :73 同文）、`SherrySignItem.java:45` 类头（forge :45 同文）、`BaseSignItem.java:453`「诡异骰子折算同名同序」（forge :421 同文）；`ChargeManager` 中「megas / moses / parunan / sherry 仍各自按诡异骰子再折 ×0.5」的注释在 **forge 侧**同样过时，未跟随修改以免两线再分叉。
4. **资产侧 FAIL 无法在本批复核**：用户禁止跑 gradle，`tools/verify_fabric_assets.py` 读的是既有构建产物 `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`，与本批 17 个 Java 源文件无关；其 3 行 FAIL 为模型/贴图/blockstate 缺失（详见第六节），属既有基线问题。
5. **并发批次痕迹**：开工前 `component/ModAttachments.java`（18:56）、`item/sign/BaseSignItem.java`（18:37）、`component/SignAttachments.java`（18:13）mtime 均晚于本会话，疑为同仓其他批次在同日改写；本批只做上述定点插入，未回滚他人改动。
6. **本批遗留临时脚本**（用户禁止删文件，故保留）：仓库根 `_tmp_b11_bytes.py`（17 个文件 CRLF/BOM 字节自检）、`_tmp_b11_fix.py`（Dorothy 换行还原）。可随时删除。
7. **lang 键零改动**：本批未新增/修改任何 lang 键（`sign_active_max_cooldown_base` 是附件键，不是语言键），故无需三语同步。

## 六、自检原始输出

### 6.1 `python scripts/__javac_check.py`（exit 0）
```
EXIT 1 ERRORS 171 jars 685 srcs 519
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```
REAL errors 为空（171 条全部落在 NOISE_AW / NOISE_IFACE_INJECTION 桶，系 Loom 访问加宽与接口注入导致，javac 侧无法复现）。

### 6.2 `python tools/verify_fabric_assets.py`（exit 0）
```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980
[6] 手册闭环: 条目 157 / 引用配方 138
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== FAIL ===
  ✗ 方块物品资源不全(blockstate=['card_shop', 'chip_shop', 'event_drawer'] / model=['card_shop', 'chip_shop', 'event_drawer'] / texture=['card_shop', 'chip_shop', 'event_drawer'])
  ✗ 缺模型(紫黑格): ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_drawer', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
  ✗ 缺贴图: ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
```
结论：**语言 [5]（980/980/980）与手册 [6]（157/138）均未新增 FAIL**；三行 FAIL 全在模型/贴图/blockstate 资产侧，且读的是既有构建产物 jar，与本批 Java 改动无关（既有基线问题，不由本批引入）。

### 6.3 `python tools/check_lang_sync.py --line fabric-1.20.1`（exit 0）
```
[OK]   fabric-1.20.1: en_us=980 ...
```
（该脚本在 GBK 控制台下后半段中文显示为乱码，行首 `[OK]` 与退出码 0 可确认通过。）

### 6.4 CRLF / BOM 字节自检（python 直读字节，17 个文件全绿）
```
WeirdDiceHandler.java        bytes=  4645 bareLF=0 crlf=95  lines=95  bom=False
ChargeManager.java           bytes=  9802 bareLF=0 crlf=188 lines=188 bom=False
BaseSignItem.java            bytes= 49448 bareLF=0 crlf=723 lines=723 bom=False
FateGuidanceCardItem.java    bytes=  8236 bareLF=0 crlf=133 lines=133 bom=False
CurrentCoreChipItem.java     bytes=  8735 bareLF=0 crlf=140 lines=140 bom=False
LuluSignItem.java            bytes= 11418 bareLF=0 crlf=184 lines=184 bom=False
DorothySignItem.java         bytes= 15636 bareLF=0 crlf=294 lines=294 bom=False
SherrySignItem.java          bytes= 31822 bareLF=0 crlf=531 lines=531 bom=False
MosesSignItem.java           bytes= 10425 bareLF=0 crlf=191 lines=191 bom=False
MegasSignItem.java           bytes= 20306 bareLF=0 crlf=395 lines=395 bom=False
BonnieSignItem.java          bytes=  7939 bareLF=0 crlf=150 lines=150 bom=False
RenSignItem.java             bytes=  7968 bareLF=0 crlf=145 lines=145 bom=False
HaiqingSignItem.java         bytes=  7981 bareLF=0 crlf=145 lines=145 bom=False
ZhaoSignItem.java            bytes= 29560 bareLF=0 crlf=502 lines=502 bom=False
TeruSignItem.java            bytes= 42283 bareLF=0 crlf=709 lines=709 bom=False
ModItems.java                bytes= 75452 bareLF=0 crlf=1188 lines=1188 bom=False
ModAttachments.java          bytes=105339 bareLF=0 crlf=1841 lines=1841 bom=False
BAD_FILES=0
```
（过程中 `DorothySignItem.java` 曾被编辑器整文件转成 LF，已按字节还原为 CRLF 后复测，上表为最终态。）

## 七、纪律核对

- 改动手段：仅 `read` / `write` / `edit` 工具（`_tmp_b11_*.py` 为字节自检与换行还原辅助脚本，只读写上述 Java 文件）。
- 全部 Java 文件 CRLF + UTF-8 无 BOM：见 6.4，`bareLF=0` / `bom=False` 全绿。
- 未跑 gradle / 未启动游戏 / 未 datagen / 未 commit / 未 `git add` / 未删除任何文件。
- 未改 `build.gradle`、`gradle.properties`、`AstralDiceMod.java`（漂移 2 的调用点全部落在 `BaseSignItem` / `WeirdDiceHandler`，无需改主类）。
- 平台差异未回退：`compat/curios/CuriosApi`、`platform/*` 垫片、`isClientSide()` 调用风格一律保持本线形态。
