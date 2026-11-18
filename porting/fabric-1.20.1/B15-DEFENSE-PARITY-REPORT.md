# B15 — 防御力 ↔ 护甲折算口径对齐（审计编号 D-06）

> 目标线：`fabric-1.20.1` ｜ 基准线：`forge-1.20.1` ｜ 日期：2026-11-01 ｜ 依据：用户 2026-10-25 裁决「按 forge 1:1」
> 结论：fabric 线折算口径已按 forge 逐字移植；守门 D8[fabric-1.20.1] 旧折算读数 **7 处 → 0 处**；`scripts/__javac_check.py` **REAL errors = 0**；三语 lang 复核**无需改动**（已是「2 基数 + 1÷系数」）。

## 1. 口径（唯一事实源 = starengine `CombatFormula`）

- 系数：玩家 `PLAYER_ARMOR_COEF = 0.30` ⇒ 1 防御力 = 1/0.30 ≈ **3.3333** 护甲；生物 `MOB_ARMOR_COEF = 0.40` ⇒ 1 防御力 = **2.5** 护甲。两阵营**必须分开换算**，`playerSide` 由 `entity instanceof Player` 判定 —— 全仓无一处「一律传 `true`」的生物侧写点（雷暴将至走生物系数）。
- 基础式：玩家 `2 + 护甲×0.30 + 0.85×韧性`；生物 `初始值 + 护甲×0.40 + 1.0×韧性`。**基数 2 由库提供**（`CombatFormula.PLAYER_BASE_DEFENSE = 2.0`），本线代码**无**硬编码基数常量：`grep -rn BASE_DEFENSE fabric-1.20.1/src/main/java` 仅命中 `item/sign/AmeSignItem.java:99`（该物品自身的「基础防御 6 点」，与公式基数无关）。原先 fabric 多处注释写「4 + 护甲×0.30」属**注释错误**，已按 forge 改为「2 + 护甲×0.30」。
- 有效护甲取 `Attributes.ARMOR` 的 double 终值（`effectiveArmorPoints`），**禁用** `LivingEntity#getArmorValue()`：它是 `Mth.floor(ARMOR)`，26.6667 会被截成 26（2026-10-25 实机暴露）；所有 `floor` 取整带 `+ 1.0E-6D` 浮点尘埃容差。

## 2. forge 行号 → fabric 新行号

### 2.1 `combat/DiceCombatModifiers.java`（四个函数 + 两个常量，逐字移植）

| 内容 | forge | fabric |
|---|---|---|
| `PLAYER_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.PLAYER_ARMOR_COEF` | 198 | 196 |
| `MOB_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.MOB_ARMOR_COEF` | 200 | 198 |
| `armorValueForDefensePoints(double, boolean)` | 206 | 204 |
| `defensePointsForArmor(double, boolean)` | 211 | 209 |
| `armorValueForDefensePoints(double, LivingEntity)` | 218 | 216 |
| `effectiveArmorPoints(LivingEntity)` | 229 | 227 |
| `setDefenseArmorBonus(Player, String, int)` / 内层折算调用 | 245 / 251 | 243 / 249 |
| `setDefenseArmorBonus(LivingEntity, String, int)` / 内层折算调用（本线**新增**） | 305 / 310 | 266 / 271 |
| `defensePowerOf` → `Math.floor(defensePowerBase(...) + 1.0E-6D)` | 913 | 842 |
| `defensePowerBase` → `effectiveArmor = Math.max(0, effectiveArmorPoints(player) + armorValueForDefensePoints(modifierDefense, true))` | 1061 | 980 |

### 2.2 调用点 / 内容侧

| 内容 | forge | fabric |
|---|---|---|
| `combat/DiceCombatEvents` 骰战 `effectiveArmor = Math.max(0, effectiveArmorPoints(target) + armorValueForDefensePoints(modifierDefense, target instanceof Player))` | 1083 | 705 |
| `combat/DiceCombatEvents` 生物→玩家路径 `CombatFormula.playerDefense(effectiveArmorPoints(target), ARMOR_TOUGHNESS)` | 1452 | 1062 |
| `event/EventEffects` 雷暴生物护甲 `2 * DiceCombatModifiers.MOB_ARMOR_PER_DEFENSE` | 139 | 147 |
| `item/chip/PiercingGunChipItem#getTargetDefense` 护甲读取 `effectiveArmorPoints(target)` | 27 | 27 |
| `item/dice/ObsidianDiceItem#ARMOR_BONUS`（int → double） | 28 / 42 | 28 / 42 |
| `item/chip/MotoHelmetChipItem#ARMOR_PER_DEFENSE` | 20-21 | 20-21 |
| `item/sign/DorothySignItem` 暖意护甲写入（`dorothy_warmth_armor`） | 87-88 | 114-115 |
| `item/sign/DorothySignItem` 护甲 → 防御力反算（`defensePointsForArmor + 1.0E-6D`） | 213-216 | 237-238（调用）/ 260-264（helper） |
| `item/sign/JillSignItem` 每张防御牌护甲 `defense * 2 * PLAYER_ARMOR_PER_DEFENSE` | 153 | 179 |

## 3. 变更清单（23 个文件，全部 edit/write 落盘）

**口径核心**
- `combat/DiceCombatModifiers.java`：新增两个 `*_ARMOR_PER_DEFENSE` 常量 + 四个折算函数 + `LivingEntity` 重载 `setDefenseArmorBonus`；`setDefenseArmorBonus(Player,…)` 内 `defensePoints * 2.0` → `armorValueForDefensePoints(defensePoints, true)`；`defensePowerBase` 改走 `effectiveArmorPoints(player)` 并按玩家侧折算；`defensePowerOf` 补 `+ 1.0E-6D`；类/方法 javadoc 由「×2.0 遗留、只兑现 60%、待用户裁决」改为「1÷阵营护甲系数（2026-10-25 用户裁决）」；五处「4 + 护甲×0.30」→「2 + 护甲×0.30」。
- `combat/DiceCombatEvents.java`：骰战 `target.getArmorValue() + modifierDefense * 2.0` → `effectiveArmorPoints(target) + armorValueForDefensePoints(modifierDefense, target instanceof Player)`；生物→玩家路径 `target.getArmorValue()` → `effectiveArmorPoints(target)`；块注释重写（生效读法 + 基数 2 + 阵营分式）。
**内容侧折算点（删除全部 `* 2.0` 硬编码）**
- `item/dice/ObsidianDiceItem.java:28`：`int ARMOR_BONUS = DEFENSE_BONUS * 2` → `double ARMOR_BONUS = DEFENSE_BONUS * PLAYER_ARMOR_PER_DEFENSE`（唯一使用点 :42 的 `AttributeModifier` 接受 double）。
- `item/chip/MotoHelmetChipItem.java:20-21`：本地 `ARMOR_PER_DEFENSE = 2.0` → 引用 `PLAYER_ARMOR_PER_DEFENSE`。
- `item/sign/DorothySignItem.java`：删除本地 `ARMOR_PER_DEFENSE = 2.0D`；暖意护甲改 `stacks * PLAYER_ARMOR_PER_DEFENSE`；`convertArmorToDefensePoints` 改走 `defensePointsForArmor(armor, true) + 1.0E-6D`。
- `item/sign/JillSignItem.java:179`：`defense * 2 * DorothySignItem.ARMOR_PER_DEFENSE` → `defense * 2 * PLAYER_ARMOR_PER_DEFENSE`。
- `event/EventEffects.java`：删除本地 `MOB_ARMOR_PER_DEFENSE = 2.0D` 及其 TODO-PLATFORM javadoc（本类不再留字面量），雷暴将至改 `2 * DiceCombatModifiers.MOB_ARMOR_PER_DEFENSE`（生物系数 0.40 ⇒ 2 防御力 = +5 护甲）。
- `item/chip/PiercingGunChipItem.java:27`：`target.getArmorValue()` → `effectiveArmorPoints(target)`（与 forge 逐字一致）。
**注释口径收口（纯注释，无行为变更）**
- 「1 防御力 = 2 护甲值」→「1 防御力 = 1÷护甲系数 护甲值」：`combat/DiceCombatModifiers`、`effect/DragonRoarBreakEffect`、`event/PlayerTickEvents`(×2)、`item/dice/NetherStarDiceItem`、`item/sign/{Deepseek,Jasmine,Nardis(×3),NancyLu,Padman,Moses}SignItem`、`item/chip/{Adrenaline(×2),RevengeHalberd,PrimordialCore}ChipItem`。
- `effect/DragonRoarBreakEffect.java:43`：「−8 护甲 = −4 点防御力」→「−8 护甲 ≈ −2.4 点防御力（玩家侧 1 防御力 = 1÷0.30 ≈ 3.33 护甲值）」。
- `item/dice/NetherStarDiceItem.java:21`：「折算为 +4 护甲」→「玩家侧 1 点 ≈ 3.33 护甲 ⇒ 每星约 +6.67 护甲」。
- `item/sign/AmeSignItem.java:100/179`：「×2.0 折真实护甲」→「按 1÷玩家护甲系数 折真实护甲」。
- `effect/UnwaveringEffect.java:9`、`item/card/UnwaveringCardItem.java:15`：「防御 = 4 + 护甲×0.30 …」→「防御 = 2 + 护甲×0.30 …」（+8 护甲 = +2.4 防御力一项复核仍成立）。

## 4. 自检原始输出

### 4.1 `python scripts/__javac_check.py`
```
EXIT 1 ERRORS 171 jars 685 srcs 519
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```
> `EXIT 1` 由 NOISE_AW / NOISE_IFACE_INJECTION 触发，**REAL errors 为空**（要求满足）。
>
> 过程记录：中途一次运行曾出现 1 条 REAL（`item/card/TemporaryCardUtil.java:760` 找不到 `CardInventoryMenu#purgeIllegalCards()`），该文件 mtime 19:17:48、`screen/CardInventoryMenu.java` mtime 19:18:10 —— 属并发协作者的接线尚未落盘造成的竞态，非本批改动；其落盘后重跑即 REAL = 0（见上）。

### 4.2 `python tools/verify_defense_unit_parity.py`
```
...
PASS  D1/D2-a..c/D3-a..b/D4/D5-a..e/D6/D7/D9-a..d  —— P0 两线 42 项
D8[neoforge-26.1.2]  P1 未移植（旧折算命中 1 处）
D8[fabric-1.20.1]  P1 未移植（旧折算命中 0 处）
数值自检（系数倒数折算后，防御力贡献必须 1:1）
  玩家: +2 ⇒ +6.6667 护甲 ⇒ 2.0000
  玩家: +8 ⇒ +26.6667 护甲 ⇒ 8.0000
  生物: +2 ⇒ +5.0000 护甲 ⇒ 2.0000
  生物: +8 ⇒ +20.0000 护甲 ⇒ 8.0000
GATE: PASS（42 项全部满足）
```

### 4.3 `python tools/verify_fabric_assets.py`（与本批无关的既有失败，原样贴出）
```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 979 / en_us 979 / ja_jp 979
[6] 手册闭环: 条目 157 / 引用配方 138
[7] 创意标签覆盖: 156/157（已知与基线一致（不阻塞）: ['repulsion']）
[8] 饰品槽位图标: 检查 7 条 icon
=== FAIL ===
  ✗ 方块物品资源不全(blockstate/model/texture=['card_shop','chip_shop','event_drawer'])
  ✗ 缺模型(紫黑格): ['abyss_reagent_chip','alana_sign','ame_sign','card_shop','chip_shop','deepseek_sign','dorothy_sign','effect_card_barricade','effect_card_repulsion','event_drawer','event_ticket','extra_battery_chip','flashlight_burst_chip','fortune_stick_chip','infinite_grid_chip','jill_sign','kangel_sign','lingling_sign','repulsion','sensor_chip']
  ✗ 缺贴图: 同上去掉 card_shop/chip_shop/event_drawer 共 17 项
```
> 该脚本校验的是 **已构建 jar**（`build/libs/…jar`），而未重跑构建（本批禁止 gradle）；缺失项与本批涉及的 `java` 文件无关（本批未改任何资源）。是否重跑构建后复检由用户裁决。

### 4.4 `python tools/check_lang_sync.py --line fabric-1.20.1`
```
[OK]   fabric-1.20.1: en_us=979 键，与 zh_cn 键集一致
```
（控制台为 GBK 输出，原文含中文处显示为乱码，`[OK]` 与计数可读。）

### 4.5 落盘字节复核（23 个被改文件）
```
全部 23 文件：CRLF 行数 = 文件行数，bareLF = 0，BOM = false
其中 item/sign/{AmeSignItem, DeepseekSignItem, JillSignItem}.java 本批接触前即为 LF 文件
（fabric 线现存 20 个 LF 源文件，均为本线新建未跟踪文件），已按硬性要求统一转为 CRLF，
转换前后「\r\n→\n」归一化 sha256 逐字节一致（内容零变更），复核 bareLF=0 / BOM=false。
```

## 5. 未做项 / 待裁决

1. **守门 D8 未收紧**：按指示未改 `tools/verify_defense_unit_parity.py`，D8 对 P1 仍「只统计不判 FAIL」。现读数 fabric-1.20.1 = 0 处、neoforge-26.1.2 = 1 处。**是否把 fabric 线纳入 D1–D7/D9 的判定集（即 D8 升为 FAIL 判据）请裁决**；本批未动。
2. **forge 侧注释残留（本线按正确值写，故与 forge 注释文字不完全一致）**：`forge-1.20.1/.../event/EventEffects.java:129`（现写「+4 护甲」，实际代码已给 +5）、`forge-1.20.1/.../item/sign/AmeSignItem.java:53,196`（仍写「×2.0 折真实护甲」）、`forge-1.20.1/.../item/dice/NetherStarDiceItem.java:22`（仍写「折算为 +4 护甲」）、`forge-1.20.1/.../effect/DragonRoarBreakEffect.java:43`（仍写「−8 护甲 = −4 点防御力」）。本批**未改 forge 线**，请裁决是否另批同步。
3. **内容数值差异（非折算口径，D-06 范围外，未改）**：`item/chip/MotoHelmetChipItem.java` fabric `DEFENSE_HIGH = 6` / `TOUGHNESS_BONUS = 2` / 类注释「+2/+4/+6」，forge 同名文件为 `DEFENSE_HIGH = 8` / `TOUGHNESS_BONUS = 0` / 「+2/+4/+8」。请裁决是否另开批次对齐。
4. **`getDisplayDefenseRange`** 仍用 `Math.floor(dp)`（无 `+1e-6`），与 forge `:1088` **同形**，故本批**未动**（保持 1:1）；若 P0 立项统一容差，两线需同改。
5. **`docs/compat-1.5.0-porting.md`** 已就地更新：§3 取证命令行注释改为「fabric-1.20.1 已归零，neoforge-26.1.2 仍 1 处」，§4 追加第 5 条登记本次落地（含 lang 无需改动的结论）。
6. **未触碰**：`combat/EliteTargets.java:57`（`getArmorValue() > ELITE_ARMOR_THRESHOLD` 是精英判定语义，非防御公式）、`mixin/jade/ArmorValueProviderMixin`（显示层）、`mixin/client/ArmorBarCancelMixin`（mixin 目标签名）。
7. **未跑构建 / 未跑游戏 / 未 commit / 未删文件**（按硬性要求）；`build.gradle`、`gradle.properties`、`AstralDiceMod.java`、`shop/**` 一律未改。
