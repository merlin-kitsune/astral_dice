# 自定义伤害类型分工与选型（2026-09-24）

> 由「怪力侦探立牌误走法伤链」修正引出：本模组有 **3 个**自定义伤害类型，职责必须分清。
> 本文是该分类的权威说明，新增任何「造成伤害」的功能前**先读这张表**。

---

## 1. 三类型对照

| | `astral_dice:card_spell` | `astral_dice:skill_damage` | `astral_dice:true_damage` |
|---|---|---|---|
| 中文口径 | 法伤（远程 / 魔法伤害） | **技能类伤害** | 真伤 |
| 是否进**法伤修饰器链** | ✅ 是（白名单 matcher 4） | ❌ **否**（禁止加入） | ❌ 否 |
| 无视护甲值 / 盔甲韧性 | ✅（`bypasses_armor`） | ✅（`bypasses_armor`） | ✅（`bypasses_armor`） |
| 穿透无敌帧 | ❌ | ✅（`bypasses_cooldown`） | ✅（`bypasses_cooldown`） |
| 来源实体形状 | direct 为空、击杀归属 `causing` | 同左 | 同左（另有 `trueDamage(Level)` 无归属形态） |
| 当前使用者 | 活体书页命中（`LivingPageImpact`） | **怪力侦探「怪力投掷」落地**（`SherryThrowManager#settle`） | 修饰器加成的独立结算、大当家溅射、定向爆破 / 电击手套 AOE、飞星、火卡、电磁炮雷击、雷击 Mixin |
| 资源文件 | `damage_type/card_spell.json` | `damage_type/skill_damage.json` | `damage_type/true_damage.json` |

三者都登记在 `bypasses_armor.json` ⇒ 基础值都跳过护甲；`true_damage` 与 `skill_damage` 另登记 `bypasses_cooldown.json` ⇒ 二者都**穿透受击无敌帧**（`card_spell` 不穿透）。

---

## 2. 选型判据（按顺序问）

1. **它是不是「玩家的远程 / 魔法攻击」或「伤害效果牌」？**
   → 是 ⇒ `card_spell`。它**应该**吃忍术飞镖 / 贯穿之铳 / 紫晶骰子 / 标记喷罐 / 魔法箭袋 / 效果牌加成。
2. **它是不是「立牌 / 技能打的固定点数」？**
   → 是 ⇒ `skill_damage`。它**不该**被任何法伤加成放大。
3. **它是不是「修饰器加成的独立结算」或「范围波及的第二段伤害」？**
   → 是 ⇒ `true_damage`（避开「加成再触发加成」的循环，且穿透无敌帧）。
4. 都不是（普通环境伤害） ⇒ 用原版类型或 `generic()`。

**反例（本轮修的就是这个）**：怪力侦探立牌的 2(+5) 点固定伤害曾用 `card_spell` ⇒ 落进第 1 类，
于是被整条法伤链放大、还会触发电击手套的范围波及。固定点数不该有这种乘算。

---

## 3. 为什么不能「就近复用」

| 复用候选 | 后果 |
|---|---|
| `card_spell` | 吃满法伤修饰器链（忍术飞镖 / 贯穿之铳 / 紫晶骰子 / 标记喷罐 / 魔法箭袋 / 效果牌加成），并可能触发电击手套 AOE ⇒ 固定点数失控 |
| `true_damage` | 会**穿透无敌帧**（`bypasses_cooldown`）⇒ 一次技能可能打出多段；语义上也把「技能伤害」与「真伤」混为一谈 |
| `damageSources().playerAttack(...)` | 被当作**玩家近战** ⇒ 吃骰战伤害、触发「战斗伤害类」筹码（2026-09-22 已明确否决） |
| `damageSources().generic()` | 会被护甲减免，与「固定点数」文案不符 |

⇒ **语义不同就必须建新类型**。代价只有一个资源 json + 一个 `ResourceKey` + 三语死亡消息键。

---

## 4. 新增一个伤害类型的 checklist（三线）

1. `data/astral_dice/damage_type/<id>.json`（复刻既有类型的 3 个字段：`message_id` / `scaling` / `exhaustion`）
2. `data/minecraft/tags/damage_type/bypasses_armor.json` 追加 `astral_dice:<id>`（若需无视护甲）
3. （可选）`bypasses_cooldown.json` —— 只在需要穿透无敌帧时加
4. `damage/ModDamageTypes`：`ResourceKey` + 工厂方法（形状照 `trueDamage(Level, Entity)`）
5. lang：`death.attack.<id>` / `.player` / `.item` **三语三条** —— 缺失会在聊天栏显示原始 key
6. ⚠️ **平台 API 逐线核对**（照抄必炸）：

   | | 1.20.1 (Forge) | 1.21.1 (NeoForge) | 26.1.2 (NeoForge) |
   |---|---|---|---|
   | 资源定位符 | `new ResourceLocation(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)` |
   | 取 Holder 的私有方法名 | 就地实现 | `holder(...)` | **`trueHolder(...)`** |

7. 若新类型**不是**法伤：**不要**动 `SpellDamageRegistry` 的白名单；并在白名单处留一句警告注释（防后人误加）。
8. 三线 `compileJava` + 开包核验（`data/…` 资源 + class 常量池里的方法名 + lang 键）。

---

## 5. 禁止事项

- ❌ **禁止**把 `astral_dice:skill_damage` 加进 `SpellDamageRegistry.MAGIC_DAMAGE_TYPES` 或任何 matcher
  （`SpellDamageRegistry` 里已留警告注释）。
- ❌ **禁止**用 `card_spell` 承载「立牌 / 技能的固定点数伤害」。
- ❌ **禁止**用 `playerAttack` 承载立牌 / 技能伤害（会吃骰战与战斗伤害类筹码）。
