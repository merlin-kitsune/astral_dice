# 方案二（Mixin 底层改写）可行性验证报告

> 只读取证报告。回答「用 mixin 彻底改写伤害底层逻辑、让我们的伤害类型从底层绕过原版护甲机制」是否可行、是否必要、破坏面多大。
> 取证对象：`neoforge-1.21.1`（NeoForge 21.1.235）与 `forge-1.20.1`（Forge 47.4.10）两线；原版事实一律取 `build/moddev/artifacts` 下的 Mojang 映射 sources jar，不取记忆、不取 wiki。
> 关联审计：`docs/features/attack-damage-chain-audit.md`（N11 :186-188、:311；N15 :25、:310；§7.4a :306-315）。
> 本报告**未**改任何源码/资源/数据，**未**跑 Gradle、**未**起游戏。扫描脚本落在 `temp/`（见 §3.1）。

---

## §0 结论速览

| 问题 | 判定 | 一句话结论 |
|---|---|---|
| **Q1** 现况绕过了什么 / 还需不需要 mixin | **SUPPORTED（不需 mixin）** | 三处减免（护甲 / 保护附魔 / 抗性药水）全部由 `DamageSource#is(DamageTypeTags.…)` 门控，**数据标签已经足够**；`true_damage` 缺的只是 `bypasses_enchantments` / `bypasses_resistance` 两个条目（或新建 `bypasses_effects`），纯数据、2 行改动。 |
| **Q2** 可行注入点 | **SUPPORTED（技术可行）但无必要** | 两线均有可注入的 `getDamageAfterArmorAbsorb` / `getDamageAfterMagicAbsorb(DamageSource, float)`（`protected`，带 `DamageSource` ⇒ **能**只对我们自己的伤害类型生效）。但 `CombatRules#getDamageAfterMagicAbsorb(FF)F` 与 1.20.1 的 `CombatRules#getDamageAfterAbsorb(FFF)F` **没有** `DamageSource`，在那里无法区分来源。 |
| **Q3** 同方法冲突面 | **SUPPORTED（已实测）** | 本机整合包 508 个 jar / 772 份 mixin 配置 / 4905 个 mixin 类中，`@Mixin(LivingEntity)` 且引用伤害链方法名的类 **18 个 / 14 个 modid**；**与我们候选注入点同方法**的只有 **3 个类**（`getDamageAfterArmorAbsorb` 3、`getDamageAfterMagicAbsorb` 1）。另实测到**神化（Apotheosis）用 `@Overwrite` 覆写了 `CombatRules.getDamageAfterAbsorb`**。 |
| **Q4** 方案二能否解决 N15（两次 hurt） | **REFUTED** | **毫无作用**。第二次 `hurt` 是本模组自己发的（`DamageEffectCardHandler.java:95`），mixin 改的是「单次 hurt 的实际数值」，不是「结算次数」；且 `hurt` 没有 `amount<=0` 早退，把加成段改成 0 也消不掉第二次 `hurt` 的钩子。 |
| **Q5** 增量价值 / 是否推荐 | **SUPPORTED（不推荐）** | 相对「纯数据标签」增量价值 ≈ 0（唯一真增量是「按实例而非按伤害类型 bypass」，N11 不需要）；破坏面却包含：神化护甲公式被整体跳过、森罗物语两线在 `getDamageAfterArmorAbsorb` HEAD 的注入顺序不确定、Iron 系饰品/法术的护甲加成静默失效、以及**50 个 jar 在 `LivingDamageEvent` / `LivingHurtEvent` 层做的事完全不受控**。 |

**最推荐路径**：`true_damage` 补标签（P0，纯数据）＋ 方案一（N15 单次结算）；**不建议**方案二。
**必须先裁决的分歧点**：「以法伤为标准」有两种解读（§4.4）——按字面解读，`true_damage` 与 `card_spell` 目前**已经**一致（都只 bypass 护甲+冷却），那 N11 的「修」甚至可能是不改标签。这条决定「补标签」还是「两者一起补」。

---

## §1 现况标签与消费点对照表

### 1.1 本仓实际存在的标签文件（逐字实测）

两线 `src/main/resources/data/minecraft/tags/damage_type/` 下只有 **4 个** 文件（任务书里列的 7 个里，`bypasses_invulnerability` / `no_knockback` / `witch_resistant` **不存在**）：

| 文件 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|
| `bypasses_armor.json` | `true_damage, card_spell, skill_damage, extra_damage, card_cost, unreducible_damage` | 同（逐字节一致） |
| `bypasses_cooldown.json` | `true_damage, skill_damage, extra_damage, card_cost, unreducible_damage, card_spell, dice_damage` | 同（逐字节一致） |
| `bypasses_enchantments.json` | 仅 `card_cost, unreducible_damage` | 同 |
| `bypasses_resistance.json` | 仅 `card_cost, unreducible_damage` | 同 |

⇒ **`true_damage` 与 `card_spell` 都在 armor+cooldown，但都不在 enchantments/resistance**；`dice_damage` 只在 cooldown（连护甲都不 bypass）。这就是 N11 的全部数据事实。

### 1.2 「加了标签 ⇒ 跳过哪一步」（1.21.1 / 1.20.1 双列）

| 标签 | 消费点（1.21.1 / 1.20.1） | 跳过什么 |
|---|---|---|
| `bypasses_armor` | `LivingEntity.java:1726` / `:1614` | 跳过 `hurtArmor`（护甲耐久）+ `CombatRules.getDamageAfterAbsorb`（护甲减伤；**整合包里这段公式已被神化 `@Overwrite` 改写**） |
| `bypasses_cooldown` | `LivingEntity.java:1190` / `:1131` | 跳过「无敌帧窗口内只结算增量」分支 ⇒ 每次 `hurt` 全额结算（`amount - lastHurt` 不再发生） |
| `bypasses_enchantments` | `LivingEntity.java:1762` / `:1647` | 跳过保护附魔段（`EnchantmentHelper.getDamageProtection` + `CombatRules.getDamageAfterMagicAbsorb`）。⚠️ **但抗性药水段仍会先吃**（:1743 / :1629） |
| `bypasses_resistance` | `LivingEntity.java:1743` / `:1629` | 跳过抗性药水段（`MobEffects.DAMAGE_RESISTANCE` 的 `(amp+1)*5%` 减免）。⚠️ **但保护附魔段仍会吃** |
| `bypasses_effects`（**本仓未建**） | `LivingEntity.java:1740-1741` / `:1626-1627` | 一条短路：**同时**跳过抗性 + 保护附魔（整段魔法减免） |
| `bypasses_shield` | `LivingEntity.java:1369` / `:1310` | 跳过盾牌格挡判定（本轮不涉及） |
| `bypasses_invulnerability` | `LivingEntity.java:1307` / `:1248` | 跳过图腾/无敌检查（本轮不涉及） |
| `no_knockback` | `LivingEntity.java:1241`（仅 1.21.1 有常量） | 跳过击退（本轮不涉及） |
| `witch_resistant_to` | `Witch.java:223` / `:191` | 女巫对特定类型减伤（本轮不涉及） |

关键结构（1.21.1 `getDamageAfterMagicAbsorb`，`LivingEntity.java:1739-1780`）：

```
1739  protected float getDamageAfterMagicAbsorb(DamageSource damageSource, float damageAmount) {
1740      if (damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)) { return damageAmount; }   // 短路全跳
1743      if (hasEffect(DAMAGE_RESISTANCE) && !source.is(BYPASSES_RESISTANCE)) { ... }      // 抗性段
1762      else if (source.is(BYPASSES_ENCHANTMENTS)) { return damageAmount; }               // 保护段前早退
1767      f3 = EnchantmentHelper.getDamageProtection(serverlevel, this, source);             // 保护段
1773      damageAmount = CombatRules.getDamageAfterMagicAbsorb(damageAmount, f3);
```

⇒ 抗性与保护是**两段独立**的 `if`，只给一个标签只跳一段。`true_damage` 要「不被削减」就必须两段都跳（或直接给 `bypasses_effects`）。1.20.1 结构同构（`:1625-1658`）。

### 1.3 那为什么还需要 mixin？—— **不需要，数据标签已足够**

原因是结构性的：**这三处减免全部在标签门控之后**，标签是 datapack 层加法合并的（本仓已有自建标签文件先例：forge 线同样自带 `bypasses_cooldown.json`），加条目的成本 = 2 行 JSON，且客户端/服务端一致、无需字节码、不随原版重命名失效。

反例排查（各种「标签可能不够」的猜想，逐条判定）：

| 猜想 | 判定 | 依据 |
|---|---|---|
| 「目标公式被第三方 `@Overwrite` 了，标签打不到」 | **不成立（反而更稳）** | 标签在**调用之前**短路：`!BYPASSES_ARMOR` 为假 ⇒ 整个 `CombatRules.getDamageAfterAbsorb` 调用不发生。实机取证：`ApothicAttributes-1.21.1-2.11.0.jar` 的 `dev.shadowsoffire.apothic_attributes.mixin.CombatRulesMixin` 用 `@Overwrite` 覆写该方法（`temp/q3d.py`），标签对神化的公式与非神化公式**同样有效**。 |
| 「伤害类型的 id 是别人命名空间的」 | **不成立** | 标签可以列任意命名空间的 id；这属于「作用域/是否越权」问题，不是能力问题。N11 只涉及本模组自己的类型。 |
| 「需要同一类型有时 bypass 有时不」 | **成立，但 N11 不需要** | 标签是**类型粒度**；实例粒度做不到（这才是 mixin 的唯一真增量，见 §5.1）。 |
| 「第三方在事件层再加一层减免」 | **成立，但 mixin 也解决不了** | `LivingHurtEvent`（1.20.1 `LivingEntity.java:1665`，护甲**前**）/ `LivingDamageEvent.Pre`（1.21.1 `LivingEntity.java:1789`，护甲后吸收前）都在 absorb 方法**之外**。实测：本机整合包 **50 个 jar** 引用 `LivingDamageEvent` 或 `LivingHurtEvent`（含神化、Iron 法术、诡厄巫法、新生魔艺、咒法学系、车万女仆万法皆通…）。 |

### 1.4 标签解决不了、方案二同样解决不了的两层

1. **吸收/黄心**：1.21.1 `LivingEntity.java:1790-1792`、1.20.1 `:1669-1670`，发生在魔法减免**之后**，不受任何标签控制；mixin 若只注入两个 absorb 方法，同样碰不到。
2. **第三方事件层 + 第三方独立抗性系统**：见上表最后一行。整合包里 50 个 jar 在事件层介入 ⇒ 「真伤 vs 法伤完全一致」不可能靠「单方面让真伤不吃减免」达成，因为两者都会吃这些层。

---

## §2 可行注入点（两线签名）

### 2.1 neoforge-1.21.1（`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`）

| 目标 | 签名 / 描述符 | 行 | 备注 |
|---|---|---|---|
| `net.minecraft.world.entity.LivingEntity#getDamageAfterArmorAbsorb` | `protected float (DamageSource, float)` → `(Lnet/minecraft/world/damagesource/DamageSource;F)F` | `LivingEntity.java:1725` | 方法体 `:1726` 才有 `BYPASSES_ARMOR` 判定 |
| `LivingEntity#getDamageAfterMagicAbsorb` | `protected float (DamageSource, float)` → `(…DamageSource;F)F` | `:1739` | 抗性+保护两段，`:1740` 有 `BYPASSES_EFFECTS` 短路 |
| `LivingEntity#actuallyHurt` | `protected void (DamageSource, float)` → `(…DamageSource;F)V` | `:1785` | `:1787` 护甲、`:1788` 魔法（**返回值被丢弃**）、`:1789` `onLivingDamagePre` |
| `LivingEntity#hurt` | `public boolean (DamageSource, float)` → `(…DamageSource;F)Z` | `:1142` | 结算次数在这一层，与「减免」无关 |
| `net.minecraft.world.damagesource.CombatRules#getDamageAfterAbsorb` | `public static float (LivingEntity, float, DamageSource, float, float)` → `(L…LivingEntity;FL…DamageSource;FF)F` | `CombatRules.java:16` | NeoForge 专用 5 参重载（带 `DamageSource`） |
| `CombatRules#getDamageAfterMagicAbsorb` | `public static float (float, float)` → `(FF)F` | `:32` | **无 `DamageSource`** |
| `MobEffects.DAMAGE_RESISTANCE` 读取点 | `LivingEntity.java:1743-1744`（`hasEffect` / `getEffect(...).getAmplifier()`） | — | 抗性段唯一入口 |

### 2.2 forge-1.20.1（`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`）

| 目标 | 签名 / 描述符 | 行 | 差异 |
|---|---|---|---|
| `LivingEntity#getDamageAfterArmorAbsorb` | `protected float (DamageSource, float)` → `(…DamageSource;F)F` | `LivingEntity.java:1613` | 与 1.21.1 签名同 |
| `LivingEntity#getDamageAfterMagicAbsorb` | `protected float (DamageSource, float)` | `:1625` | 无 `DamageContainer`/`setReduction`，抗性段只做统计 `:1637-1641` |
| `LivingEntity#actuallyHurt` | `protected void (DamageSource, float)` | `:1663` | `:1665` `ForgeHooks.onLivingHurt`（**护甲前**）、`:1680` `onLivingDamage`（护甲+吸收后） |
| `LivingEntity#hurt` | `public boolean (DamageSource, float)` | `:1088` | `:1089` `onLivingAttack` 可取消 |
| `CombatRules#getDamageAfterAbsorb` | `public static float (float, float, float)` → `(FFF)F` | `CombatRules.java:12` | **无 `DamageSource` 参数**（关键差异） |
| `CombatRules#getDamageAfterMagicAbsorb` | `public static float (float, float)` | `:18` | 同 1.21.1 |
| `MobEffects.DAMAGE_RESISTANCE` 读取点 | `LivingEntity.java:1629-1630` | — | — |

### 2.3 每个注入点能否「只对我们自己的伤害类型生效」+ 用哪种注入器

| 注入点 | 能否区分来源 | 推荐注入方式 | 坑 |
|---|---|---|---|
| `getDamageAfterArmorAbsorb`（两线） | **能**（首参就是 `DamageSource`） | `@Inject(method=..., at=@At("HEAD"), cancellable=true)`，命中则 `cir.setReturnValue(damageAmount)` | 同时跳过 `hurtArmor`（护甲耐久）——这正好是 `bypasses_armor` 的语义，可接受 |
| `getDamageAfterMagicAbsorb`（两线） | **能** | 同上 | ⚠️ **1.21.1 必须用 HEAD-cancellable**：`:1788` 的返回值被丢弃，真正生效靠方法**内部**的 `damageContainers.peek().setReduction(MOB_EFFECTS/ENCHANTMENTS, …)`（`:1751` / `:1774`）。改成 `@ModifyReturnValue` 或 `@Redirect` 调用点 ⇒ 减免记录已写入容器、结果却没人清 ⇒ 数值与 HUD 都对不上 |
| `actuallyHurt`（两线） | **能** | `@Inject HEAD` 或 `@Redirect` 里面两处调用（1.21.1 `:1787-1788`；1.20.1 `:1667-1668`） | 1.21.1 的 `@Redirect` 拿到的是「reduction 差额」而不是最终值（容器才是事实源，`:1787`），徒增复杂度 |
| `hurt`（两线） | **能** | 仅当要改「结算次数」时才需要（N15 语义） | 见 §4.3：这是唯一能碰 N15 的地方，但破坏面最大（§3.2 里 hurt 同方法 12 个类） |
| `CombatRules.getDamageAfterAbsorb`（1.21.1 5 参） | **能**（有 `entity` + `damageSource`） | 不推荐 | 神化已 `@Overwrite` 该方法 ⇒ 我们的注入与它顺序不定/可能硬冲突 |
| `CombatRules.getDamageAfterAbsorb`（1.20.1 3 参） | **不能** | 不推荐 | 只有数值，拿不到 `DamageSource` |
| `CombatRules.getDamageAfterMagicAbsorb`（两线） | **不能** | 不推荐 | 只有 `(damage, enchantModifiers)`；只能靠 ThreadLocal 旁路信号，脆弱 |
| `MobEffects.DAMAGE_RESISTANCE` 读取点 | **不能** | 不推荐 | 是全局效果查询，无法按来源区分 |

结论：**若真要走 mixin，最小、最稳的形态是「两个 absorb 方法各一个 HEAD-cancellable `@Inject`」**（不碰 `hurt`、不碰 `CombatRules`、不碰 `setHealth`）——即 §3 里「同方法冲突面仅 3 类」的那条路线。

### 2.4 接线成本（配置声明位置）

- **neo 线**：mixin 配置在 `neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml:50-64` 声明了三份（`astral_dice.mixins.json`、`astral_dice.neoforge_fixes.mixins.json`、`astral_dice.jade.mixins.json`）。新类可挂进 `astral_dice.neoforge_fixes.mixins.json`（`required:false` + `defaultRequire:0` ⇒ 注入点漂移时**静默失效不崩服**，但也不会自己冒出来，需日志核对）。
- **forge 线**：`forge-1.20.1/src/main/resources/astral_dice.mixins.json`（`required:true`、`refmap: astral_dice.refmap.json`）+ `forge-1.20.1/build.gradle:404-405` 的 `mixin { config 'astral_dice.mixins.json' }`；生产走 `mods.toml` 的 `[[mixins]]`（`forge-1.20.1/src/main/templates/META-INF/mods.toml:47-48`）。
- 两线共用同一个源码目录结构（`com.merlinkitsune.astral_dice.mixin`），新类需要**两线各一份**（平台差异见 §2.1/§2.2）。

### 2.5 与本仓既有 mixin 的冲突（⚠️ 含一条对前期结论的纠正）

本仓**已经有一个 `@Mixin(LivingEntity.class)` 注入 `hurt`**：

- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/fixes/LivingEntityDamageContainerMixin.java:42` = `@Mixin(LivingEntity.class)`；`:62` = `@Inject(method = DamageStackSanitizer.HURT_DESCRIPTOR, at = @At("HEAD"), require = 0)`；`:75` = `@Inject(… at = @At("RETURN"))`。它不改数值，只修 NeoForge `hurt` 在事件取消分支漏 `pop()` 造成的 `damageContainers` 栈泄漏（`damageContainers` 影子字段见 `:50-51`）。
  ⇒ 若方案二重写 `hurt` 的结算语义（尤其 HEAD 早退/重排），这套「进入深度 → RETURN 截断」的修复需要一起回归；只注入两个 absorb 方法则与它**零交集**。
- 其余本仓 mixin 均不碰伤害链：`mixin/EntityThunderHitMixin.java:36-63`（`Entity.thunderHit`）、`mixin/LightningBoltStrikeScopeMixin.java:33-48`（`LightningBolt.tick` 的 `@ModifyArg`）、`mixin/PiglinAiMixin.java:19-31`、`mixin/RegenerationMobEffectMixin.java:20-33`、`mixin/HealOrHarmMobEffectMixin.java:24-51`。
- forge 线没有 `fixes` 配置（`neoforge_fixes` 是 neo 专属），故 1.20.1 侧无这套栈修复。

---

## §3 同方法 mixin 冲突面清单（含统计数字）

### 3.1 扫描口径

- 扫描目标：`D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`（该实例为 **1.21.1 NeoForge**，600+ jar 中实为 508 个 `.jar`）
- 脚本（全部只读、只打印 stdout）：`temp/q3_scan6.py`（精确注解扫描）、`temp/q3b.py`（放宽目标类集合）、`temp/q3c.py`（事件层引用统计）、`temp/q3d.py`（冲突类注解类型取证）、`temp/probe_src2.py` / `temp/probe_hurt.py`（sources jar 取源码）。
- 方法：手写 class 文件解析器读 `CONSTANT_Utf8` + 完整解析 `RuntimeVisible/InvisibleAnnotations`，取 `@Mixin` 的 `value`/`targets` 与方法级注解的 `method=`。**不用**「常量池里出现 `LivingEntity` 字面量」这种宽松口径（宽松口径得 472 类，精确口径 96 类，差 376 个假阳性，全是 `instanceof`/`checkcast`）。

**总体统计**：jar 508 ｜ 含 mixin 配置的 mod jar 504 ｜ mixin 配置文件 772（含 jar-in-jar 内）｜ mixin 类 4905 ｜ `@Mixin` 目标 = `LivingEntity` 的类 **96** ｜ 其中引用 `hurt|actuallyHurt|getDamageAfterArmorAbsorb|getDamageAfterMagicAbsorb|die|setHealth` 之一者 **18 类 / 14 modid** ｜ 解析失败 0。
放宽到目标类 ∈ {`LivingEntity`,`Entity`,`Player`,`ServerPlayer`,`CombatRules`}：23 类 / 14 modid；`BY_TARGET = {LivingEntity:18, ServerPlayer:3, Player:2, Entity:0, CombatRules:0}`；`BY_METHOD = {hurt:12, die:7, actuallyHurt:5, getDamageAfterArmorAbsorb:3, setHealth:2, getDamageAfterMagicAbsorb:1}`。

### 3.2 `@Mixin(LivingEntity)` 且命中伤害链方法的 18 个类

| # | modid | jar | mixin 类 | 命中方法 | 注入类型 / 备注 |
|---|---|---|---|---|---|
| 1 | aces_spell_utils | `aces_spell_utils-1.2.7.5-1.21.1.jar` | `…mixins.server.LivingEntityMixin` | hurt | `@Inject`，`@At` 字段 `LivingEntity.invulnerableTime` |
| 2 | ae2lt | `[AE2 闪电科技] ae2lt-2.1.2-beta4.jar` | `…mixin.LivingEntityShieldHitFeedbackMixin` | hurt | `@Redirect`(INVOKE，MixinExtras `WrapOperation`) |
| 3 | ae2lt | 同上 | `…mixin.LivingEntityUndyingMixin` | setHealth | `@Inject` HEAD(cancellable) on `kill` + `setHealth` |
| 4 | amendments | `amendments-1.21-2.2.2-neoforge.jar` | `…amendments.mixins.LivingEntityMixin` | hurt | `@Inject` HEAD(cancellable) |
| 5 | artifacts | `[奇异饰品] artifacts-neoforge-13.2.5.jar` | `…attribute.invincibilityticks.LivingEntityMixin` | hurt | `@Inject`，字段 `invulnerableTime` |
| 6 | azurelib | `azurelib-neo-1.21.1-3.2.1.jar` | `…mixins.LivingEntityMixin_AzHitboxDamage` | hurt | `@ModifyVariable` HEAD(argsOnly) |
| 7 | baguettelib | `baguettelib-1.21.1-NeoForge-2.0.7.jar` | `…mixin.LivingEntityDeathMixin` | die | `@Inject` HEAD(cancellable) |
| 8 | dummmmmmy | `[试验假人] dummmmmmy-1.21-2.1.2-neoforge.jar` | `…mixins.LivingEntityMixin` | actuallyHurt, hurt | `@WrapOperation` actuallyHurt→`setHealth(F)V`；另 `heal` |
| 9 | fabric_entity_events_v1 | `fabric-entity-events-v1-1.8.0+e46a02ca19.jar` | `…fabric.mixin.entity.event.LivingEntityMixin` | die, hurt | `@WrapOperation` die(INVOKE) |
| 10 | **irons_lib** | `irons_lib-1.21.1-2.3.0.jar` | `…internal.mixin.CommonLivingEntityMixin` | **getDamageAfterArmorAbsorb** | `@Redirect`（**方法体内** INVOKE，`temp/q3d.py` 实证）★同方法 |
| 11 | **kaleidoscope_end** | `[森罗物语：末地] kaleidoscope_end-1.1.0-neoforge+mc1.21.1.jar` | `…mixins.kaleidoscope_end.LivingEntityMixin` | **getDamageAfterArmorAbsorb, getDamageAfterMagicAbsorb**, setHealth | `@Inject` HEAD + `CallbackInfoReturnable<Float>`；另 `@ModifyExpressionValue` on setHealth ★同方法 |
| 12 | **kaleidoscope_nether** | `[森罗物语：下界] kaleidoscope_nether-1.1.4-neoforge+mc1.21.1.jar` | `…mixins.kaleidoscope_nether.LivingEntityMixin` | **getDamageAfterArmorAbsorb** | `@Inject` HEAD + `CIR<Float>` ★同方法 |
| 13 | kaleidoscope_nether | 同上 | `…kaleidoscope_nether.StarBlessingMixin` | hurt（**待复核**） | 注解原文含 `at`/`knockback` 片段，方法名可能来自常量池 |
| 14 | resourcefullib（jar 内还有 adastra） | `adastra-1.21.1-1.16.26-neoforge.jar` | `…adastra.mixins.common.LivingEntityMixin` | hurt（**待复核**） | 方法名仅在常量池出现，可能是 `tick`/`travel` 的引用；modId 抽取取到 jar 内首个 modid |
| 15 | shtreimel | `shtreimel-neoforge-1.21.1-0.6.0.jar` | `…guard.mixin.LivingEntityGuardMixin` | hurt | `@Inject` HEAD(cancellable, require) |
| 16 | touhou_little_maid_spell | `[车万女仆：万法皆通] touhou_little_maid_spell-1.21.1-1.9.1-neoforge.jar` | `…mixin.FriendlyDamageHealthMixin` | actuallyHurt, die, hurt | `@Inject` hurt HEAD |
| 17 | touhou_little_maid_spell | 同上 | `…mixin.WinefoxDamageClampMixin` | actuallyHurt, hurt | `@WrapOperation` actuallyHurt→`getHealth()F`（clamp 伤害） |
| 18 | touhou_little_maid_spell | 同上 | `…mixin.accessor.LivingEntityInvoker` | actuallyHurt | `@Invoker` |

其它目标（不在 LivingEntity 上，但同属伤害链）：`ServerPlayer` 3 类（ae2lt `ServerPlayerUndyingMixin`(die)、baguettelib `ServerPlayerDeathMixin`(die)、fabric `ServerPlayerEntityMixin`(die)）；`Player` 2 类（baguettelib `PlayerDeathMixin`(die)、dummmmmmy `PlayerMixin`(actuallyHurt)）。

### 3.3 与方案二候选注入点**同方法**的冲突面（= 直接冲突面）

| 我们的候选注入点 | 同方法第三方类 | 它们的形态 | 冲突性质 |
|---|---|---|---|
| `getDamageAfterArmorAbsorb` HEAD | `irons_lib` `CommonLivingEntityMixin` | `@Redirect`（方法体内的 INVOKE） | 我们 HEAD 取消 ⇒ 方法体不执行 ⇒ 它的 redirect **静默失效**（不崩，但功能消失） |
| `getDamageAfterArmorAbsorb` HEAD | `kaleidoscope_end` / `kaleidoscope_nether` `LivingEntityMixin` | `@Inject` HEAD（带 `CIR<Float>` ⇒ 可能 `setReturnValue`） | **同注入点**：谁先谁后由 mixin 应用顺序决定 ⇒ 可能出现「我们想放行、被它拉回减免」或「它的规则被我们吞掉」，且随整合包更新漂移 |
| `getDamageAfterMagicAbsorb` HEAD | `kaleidoscope_end` 同左 | `@Inject` HEAD（`CIR<Float>`） | 同上 |
| `CombatRules.getDamageAfterAbsorb`（若走「改底层公式」路线） | **神化** `ApothicAttributes` `CombatRulesMixin` | **`@Overwrite`**（`temp/q3d.py` 实证：`Lorg/spongepowered/asm/mixin/Overwrite;` + `ALCombatRules` + `getDamageAfterArmor`） | `@Overwrite` 与任何同方法注入互斥/顺序敏感 ⇒ 会与整合包核心平衡模组正面冲突 |
| `hurt` HEAD/RETURN（若想碰 N15） | 12 个类（表 §3.2 中 hurt 行） | 含 HEAD(cancellable)、`@ModifyVariable`、`@Redirect`、`@WrapOperation` | **最大冲突面**；其中 **本仓自己** 就有一个（§2.5） |

补充取证：`ApothicAttributes` 的 `CombatRulesMixin` 常量池里有一条字符串 `"PInvocation of CombatRules#getDamageAfterMagicAbsorb is bypassing protection pen."` —— 说明「有人替换 `getDamageAfterMagicAbsorb` 调用」这件事在神化侧是被**显式识别**的（该字符串的确切触发条件本轮未复核，见 §6）。

### 3.4 统计汇总

| 口径 | 数量 |
|---|---|
| `@Mixin` 目标 = LivingEntity 的 mixin 类 | 96 |
| 其中引用 6 个伤害链方法名之一 | 18 类 / 14 modid |
| 与 `getDamageAfterArmorAbsorb` 同方法 | **3 类**（irons_lib / kaleidoscope_end / kaleidoscope_nether） |
| 与 `getDamageAfterMagicAbsorb` 同方法 | **1 类**（kaleidoscope_end） |
| 与 `hurt` 同方法 | 12 类 |
| 与 `actuallyHurt` 同方法 | 5 类 |
| 与 `die` 同方法 | 7 类 |
| 与 `setHealth` 同方法 | 2 类 |
| `CombatRules` 被 `@Overwrite` | 1 例（神化） |
| 事件层引用 `LivingDamageEvent`/`LivingHurtEvent` 的 jar | 50 |

### 3.5 兼容性判定（静态）

- **不会硬崩**：`@Inject` HEAD-cancellable 与别人的 HEAD `@Inject`/`@Redirect`/`@WrapOperation` **并存不报错**；被取消时后来的 handler 只是不执行。
- **会静默改变第三方行为**：irons_lib 的 redirect 被跳过、森罗两线的末地/下界规则可能被吞掉 —— 这类「不崩但功能消失」最难排查（参 `mc-mixin-crash-triage` 与「静默失效」口径）。
- **顺序不可控**：同注入点的执行顺序取决于 mixin 应用顺序（配置声明顺序 + 优先级），整合包增删模组可能改变结果。
- **`CombatRules` 层是雷区**：神化 `@Overwrite` + Iron 系生态 ⇒ 一旦走「替换公式」路线，冲突从「静默」升级为「顺序敏感/可能硬失败」。

---

## §4 方案二 vs 数据标签 vs 方案一

### 4.1 三者各自管哪一层

| 层（按调用链） | 纯数据标签 | 方案二（mixin 改写底层） | 方案一（预补偿成单次结算） |
|---|---|---|---|
| 护甲 / 保护附魔 / 抗性 | ✅ 可全跳 | ✅ 可全跳（且能做得更多） | ❌ 不改变（仍吃减免，只是提前算） |
| 吸收 / 黄心 | ❌ | ❌（除非再注入 `:1790`/`:1669`） | ✅ 可在补偿里扣 |
| 第三方事件层（50 jar） | ❌ | ❌ | ❌（同层，但方案一可把结果压成一次结算） |
| **结算次数（N15 两次 hurt）** | ❌ | ❌ | ✅ **这才是方案一的靶心** |

### 4.2 方案二对 N11 覆盖多少 —— 部分成立，但**等价于标签**

它能做到：让我们的伤害类型在单次 `hurt` 内不吃护甲/保护/抗性（= `BYPASSES_ARMOR` + `BYPASSES_EFFECTS` 的语义）。
它做不到的：吸收、事件层、第三方独立系统（§1.4）。
⇒ 在 N11 这个需求上，**mixin 能达成的效果与「补 2 行标签」完全重合**，而代价是 §3 的 3 类同方法冲突 + 神化 `@Overwrite` 正面对撞风险 + 两线各一份字节码。

### 4.3 方案二对 N15（两次 hurt）—— **毫无作用**（REFUTED）

证据链：

1. 第二次 `hurt` **是本模组自己发的**：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:95` `target.hurt(bonusSource, (float) bonus)`。前置的 `APPLYING_TRUE_BONUS`（`:33`、`:82`、`:88`、`:97`）只防**递归**，不防「第二次 `hurt` 本身」。
2. `hurt` **没有 `amount<=0` 早退**：1.21.1 `LivingEntity.java:1142-1206` 只有 4 个早退分支（`isInvulnerableTo` / `isClientSide` / `isDeadOrDying` / `IS_FIRE`+抗火）；1.20.1 `:1088-1145` 同理（外加 `onLivingAttack` 可取消）。⇒ 即使把加成段数值压到 0，`.hurt()` 仍会走完 `push container → onEntityIncomingDamage → actuallyHurt(0) → onLivingDamagePre/Post → onDamageTaken`，所有「每次受击」钩子照样翻倍。
3. 方案二改的是「**单次 hurt 的实际数值**」（护甲/保护/抗性减免），而 N15 是「**结算次数**」问题 —— 两个正交维度。改减免只会让每次的数值更大，不会让次数变 1。

唯一能让 mixin 碰到 N15 的形态是「重写 `hurt` 的合并语义」（例如把同一 tick 内同源嵌套伤害合并），但那会把冲突面从 3 类扩到 **12 类**（§3.4），并且要重写原版无敌帧/`lastHurt` 语义，还要和本仓 `fixes/LivingEntityDamageContainerMixin` 的栈深修复对齐 —— 得不偿失。

### 4.4 「以法伤为标准」的两种解读（**必须先裁决**）

| 解读 | 含义 | 现在的差距 | 落法 |
|---|---|---|---|
| **(i) 字面：以 `card_spell` 的实际行为为准** | 让 `true_damage` 和 `card_spell` 的减免行为完全一致 | **两者现在已经一致**：都在 `bypasses_armor`+`bypasses_cooldown`，都不在 `bypasses_enchantments`/`bypasses_resistance`（§1.1）⇒ 都吃抗性与保护附魔 | 什么都不用改；N11 变成「不改」或「两者一起补」 |
| **(ii) 意图：「真伤不该被削减」，法伤是参照的实现 | 让两者都真正穿抗性/保护 | 需要一个短路标签 | 补 `bypasses_enchantments`+`bypasses_resistance`（或新建 `bypasses_effects`），**两个类型一起补**，否则又会不一致 |

⇒ 无论哪种解读，方案二都不是必需工具：解读 (i) 不需要改，解读 (ii) 纯数据即可。这一点建议在落地前先向用户确认（§6）。

---

## §5 风险分级与推荐

### 5.1 相对「纯数据标签」的增量价值

唯一真实增量：**实例粒度**（同一 `DamageType` 在不同上下文有不同 bypass 语义），因为标签是类型粒度、mixin 可以看 `DamageSource` 之外的状态（自定义数据、ThreadLocal 标志）。N11 **不需要**这个能力（真伤在任何上下文都该穿）。
其它所谓增量（绕过第三方公式、绕过第三方系统）都不成立：第三方公式在 `CombatRules` 里，而标签在**调用之前**就短路了；第三方独立系统在**事件层**，mixin 到 absorb 也碰不到。

### 5.2 破坏面（会不会让第三方魔法模组的抗性彻底失效）

- **会（对我们自己的伤害类型而言）**：神化 `@Overwrite` 的护甲公式、森罗两线的末地/下界伤害规则、Iron 系护甲加成，对 `true_damage` 都会失效或被吞。但注意：**`bypasses_armor` 标签现在就已经在做同一件事**（神化公式早就不参与真伤计算）—— 所以「破坏」不是方案二新增的，方案二新增的是「顺序不确定 + 维护成本 + 与神化 `@Overwrite` 对撞」。
- **不会**让「第三方魔法模组的抗性」整体失效：因为这些模组的抗性多在自己的 `LivingDamageEvent`/`LivingHurtEvent` 处理里，方案二既没增强也没削弱它们（§1.4）；真正会被方案二改掉的是「走 `CombatRules` 的原版护甲/保护/抗性三段」。
- **平衡崩坏风险**：真伤段数值会从「被抗性/保护削减后」跳到「全额」，用户可感知（同一张牌伤害数字变大）。这不是 bug，是设计变更，需要和 N11/N15 的裁决一起定。

### 5.3 分级与推荐

| 方案 | 改动面 | 风险 | 结论 |
|---|---|---|---|
| **A. 纯数据标签**：`true_damage`（必要时 `card_spell`）补进 `bypasses_enchantments` + `bypasses_resistance`，或新建 `bypasses_effects.json` | 两线各 1–2 个 JSON 文件 | **极低**（纯数据、可被 datapack 覆盖、无字节码、无版本漂移） | **推荐（P0）** |
| **B. 方案二 mixin**：两个 absorb 方法 HEAD-cancellable | 两线各 1 类 + 配置 | **中高**：3 类同方法冲突（1 静默失效 + 2 顺序敏感）、神化 `@Overwrite` 对撞风险、需兼容性回归、本仓 `fixes` mixin 需一起验 | **不推荐** |
| **C. 方案一（N15 单次结算）** | 结算链重构 + HUD/跳字时序 + 多魔法模组抗性适配 | **中**：是 N15 的**唯一**正解 | 与 N15 一并裁决，优先 |

**落地建议顺序**：① 先定 §4.4 的解读（一条裁决，决定要不要动标签）；② 若走 A，两线同改、纯 JSON；③ N15 走方案一；④ 方案二仅作为「将来需要实例粒度 bypass」时的备用方案存档，不进 P0。

---

## §6 未验证项

1. **1.20.1 整合包未扫**：本机只有 1.21.1 NeoForge 实例（`狐の航空学 Voxy Edition`），§3 的全部统计**只代表 1.21.1**。1.20.1 侧的冲突面未知（`forge-1.20.1/run/` 里的实例不是完整整合包）。
2. **`kaleidoscope_end` / `kaleidoscope_nether` / `irons_lib` 的 `cancellable` 属性与 `@At` 偏移未解析**（注解只取到方法名与 `HEAD`；是否 `setReturnValue` 需进一步反编译方法体）。
3. **神化那条 `"PInvocation … is bypassing protection pen."` 的触发条件未复核**（只知道它是常量池里的字符串）。
4. **`CombatRules` 精确注解扫描结果为 0**，与 `temp/q3d.py` 的字节码取证（`@Overwrite` + `ALCombatRules`）**矛盾**，原因未查明（可能是该 class 的注解解析路径/`required:false` 配置过滤），故 §3 对神化只采用字节码证据。
5. **`hurt` 里「第二次 `hurt` 传 0 仍会走完链路」是源码推断**，未实机验证（规则禁止起游戏）；该结论依赖 1.21.1 `:1142-1206` / 1.20.1 `:1088-1145` 的早退分支清单完整。
6. **未跑 Gradle**：新 mixin 类若落地，两线编译/refmap（forge 线 `astral_dice.refmap.json`）/生产 `mods.toml` 声明均未验证。
7. **`neoforge-26.1.2` 与 `fabric-1.20.1` 两线未查**（P1，且 fabric 线伤害链实现不同）。
8. **未验证「补标签后实际数值」**：抗性/保护段被跳过后，`DamageContainer.Reduction` 的两条记录（`MOB_EFFECTS`/`ENCHANTMENTS`）会变成 0，HUD 上「减免显示」与统计（`Stats.DAMAGE_RESISTED`）会同步变化，是否符合预期需实机确认。
9. **§3 中标注「待复核」的两行**（`StarBlessingMixin`、adastra `LivingEntityMixin`）可能是不该计入的假阳性；若剔除，`LivingEntity` 命中数应视为 **16 类**，`hurt` 同方法数应视为 **10–12 类**。

---

*本报告全部结论来自 sources jar 源码 + 整合包 jar 字节码 + 本仓源码的只读取证；无任何实机、构建或写操作。*
