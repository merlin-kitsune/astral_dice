# 超天酱立牌（kangel）+ 地雷女立牌（ame）— 冻结规格（v1，2026-10-16）

> **v1.1（2026-10-17，用户裁决批准）**：`kangel_fan` 拆成两个效果 id —— `kangel_fan` 只保留「目标标记」语义，新增 `kangel_fan_indicator` 承担「佩戴者计数指示器」。裁决理由见 §1 裁决 #9；两 id 的语义边界见 §3.7。

> 本文件是两张新立牌的**冻结规格**：行为以本文件为准。实施范围：**P0 两线**（`neoforge-1.21.1` 先落地 → 验收后 `forge-1.20.1` 按 `docs/compat-1.20.1-forge.md` 移植）；P1 两线（26.1.2 / fabric）本批不动，待移植批次。
> 品质：**奇特**（`AstralRarities.bizarre()`），照 sherry/hanna 档。

## 0. 需求（用户原话，2026-10-16）

> ### 超天酱立牌（品质：奇特）：
> - 主动技能（网络天使）：立即获得（1+ 粉丝数）的星币，最多可获得10个。如果粉丝数不少于3个，还会使生命值立即恢复全满。如果玩家粉丝数不少于9个，还会额外使得友方玩家获得3星币
> - 被动技能（小天使请安）：玩家主动攻击或攻击玩家的目标会被施加“粉丝”状态，该目标攻击超天酱时，造成的伤害-1
> - （备注栏）一心同体：当玩家队伍中有地雷女时，使得该角色被动技能同样对其生效
> - 补充说明：粉丝数永久有效，且死亡后不会清空。即使目标被击杀，其拥有的粉丝数一样保留（玩家拆卸立牌后重置为0）。佩戴超天酱立牌的玩家，若存在队伍，则主动技能额外发星币覆盖全队，如果玩家没有队伍，则改为自身范围64格的其他玩家。玩家同队内存在地雷女，则他攻击目标或被目标攻击的粉丝数归于超天酱玩家。若对内有多个超天酱玩家，则每个人都相应获得1个粉丝。
>
> ### 地雷女立牌（品质：奇特）：
> - 主动技能（感情过量摄入）：立即恢复“爱”层数x2的生命值，以及移动速度加成（百分比），持续1:00。时间结束后扣除4层“爱”，并使“爱”层数上限+1（最多32层）
> - 被动技能（无法承受的爱）初始状态下玩家拥有6点防御力。受到伤害时，获得1层“爱”，每 30秒 只能获得1层。初始状态上限只有4层，玩家装备地雷女立牌时的初始层数为2层
> - （备注栏）一心同体：当玩家队伍中有超天酱时，使得该角色被动技能同样对其生效。
> - 补充说明：每层”爱“使地雷女攻击力+1，防御力-1（最低为0），爱层数死亡后不会清空。玩家队伍中所有超天酱玩家（不论多少），其受到伤害同样会使得地雷女获得“爱”层数，且玩家队伍内有多个地雷女时，超天酱受到伤害将会平等的为所有地雷女获得“爱”层数
> - 补充说明追加（2026-10-16）：玩家”爱“层数大于4层时，超出部分使玩家获得额外”爱“层数的最大生命值。
>
> 注意：添加立牌时，Tooltip内主动技能和被动技能以及备注区内容必须严格按照玩家描写的原样引入，不得擅自增删内容。
>
> 追加（2026-10-16）：超天酱粉丝数上限为10。使用“粉丝.png”作为状态指示器图标（等级关联粉丝数，并且使计时为无限）。地雷女使用“爱.png”作为状态指示器图标（等级关联“爱”层数，并且使计时为无限）。

## 1. 已裁决（用户，2026-10-16）

| # | 议题 | 裁决 |
|---|---|---|
| 1 | 地雷女主动移速加成数值 | **固定 +20%**（1:00；`ADD_MULTIPLIED_TOTAL` 0.2，照 `HannaFloatEffect`；1.20.1 侧 `MULTIPLY_TOTAL` + UUIDv5 字符串 id，照 hanna_float 先例） |
| 2 | 「爱」卸下立牌 | **清零、上限回 4**（与超天酱粉丝「拆卸重置为 0」、全体立牌 `clearSignData` 口径一致） |
| 3 | 一心同体（地雷女侧）覆盖范围 | **超天酱只做供给方**：她不攒「爱」、不吃攻+1/防-1 修正、不吃最大生命加成；仅获得 **6 点基础防御**；她受到敌对伤害时队内**所有**地雷女各 +1 爱（各自受自身 30 秒闸门与上限约束） |
| 4 | 超天酱配方 | **`GNG/WEW/ADA`**：G=黄金星盘×2、N=音符盒、W=白色羊毛×2、A=红石灯×2、E=空白立牌、D=紫晶骰子（骰子固定中下；2026-10-16 更正：初稿误写 `ALA` 漏了 D 位） |
| 5 | 地雷女配方 | **`GKG/WEW/CDC`**：G=黄金星盘×2、K=黑色羊毛、W=蜘蛛网×2、C=墨囊×2、E=空白立牌、D=紫晶骰子 |
| 6 | 注册 id | `kangel_sign` / `ame_sign`；英文名 `KAngel Sign` / `Ame Sign` |
| 7 | 粉丝数上限 | **10**（2026-10-16 追加） |
| 8 | 指示器图标 | `kangel_fan_indicator` ← `images/粉丝.png` 加金色描边的视觉变体；`ame_love` ← `images/爱.png`；均为 **32×32**；指示器**等级 = 计数**（amplifier = 计数-1）、**无限时长**（计数不随时间衰减，镜像只在计数变化时刷新、归 0 移除） |
| 9 | 拆双效果 id（2026-10-17 落地） | **`kangel_fan` = 目标标记**（HARMFUL、无限时长，**「是否粉丝」与 −1 减伤的唯一真值**）；**`kangel_fan_indicator` = 佩戴者计数指示器**（BENEFICIAL、无限时长、等级 = 粉丝数、**不参与任何战斗判据**）。拆前同 id 的两条 PvP 缺陷随之消失：① 两个超天酱佩戴者互殴时，攻击方身上的指示器被 `hasEffect(kangel_fan)` 当成「她是粉丝」⇒ 误判 −1；② 被标记成粉丝的超天酱佩戴者，其自身计数镜像（先移除再施加）会清掉别人给她的标记 |

## 2. 冻结 id 与落点

| 项 | kangel | ame |
|---|---|---|
| 物品 id | `kangel_sign`（超天酱立牌 / KAngel Sign） | `ame_sign`（地雷女立牌 / Ame Sign） |
| 品质 | 奇特 = `AstralRarities.bizarre()`（两立牌同） | 同左 |
| 立牌类 | `item/sign/KangelSignItem` | `item/sign/AmeSignItem`（均 extends `BaseSignItem`、`@EventBusSubscriber` 照 hanna） |
| 主动名 | 网络天使 / Network Angel（瞬时，非选择器类） | 感情过量摄入 / Emotional Overdose（瞬时，非选择器类） |
| 被动名 | 小天使请安 | 无法承受的爱 |
| 备注名 | 一心同体 | 一心同体 |
| 效果 | `kangel_fan`（粉丝，HARMFUL，目标标记）+ `kangel_fan_indicator`（粉丝数，BENEFICIAL，佩戴者计数指示器；2026-10-17 拆出） | `ame_love`（爱，BENEFICIAL，层数镜像）、`ame_overdose`（感情过量摄入，BENEFICIAL，1200t，+20% 移速） |
| 效果图标 | `textures/mob_effect/kangel_fan.png` ← `images/粉丝.png`（逐字节复制）；`textures/mob_effect/kangel_fan_indicator.png` = 同图**加金色轮廓描边**的视觉变体，由 `tools/gen_kangel_fan_indicator_texture.py` 确定性生成（`--check` 幂等） | `textures/mob_effect/ame_love.png` ← `images/爱.png`；`textures/mob_effect/ame_overdose.png` ← `images/地雷女立牌.png` **逐字节复制**（照 hanna_float 先例） |
| 物品贴图 | `textures/item/kangel_sign.png` ← `images/超天酱立牌.png` | `textures/item/ame_sign.png` ← `images/地雷女立牌.png` |
| 附件 | `kangel_fan_count`（int，copyOnDeath，sync）、`kangel_fan_targets`（String UUID CSV，copyOnDeath，不 sync，上限截断） | `ame_love_layers`（int，copyOnDeath，sync）、`ame_love_cap`（int，copyOnDeath，sync，默认 4）、`ame_love_gain_cd_end`（long，不跨死亡，不 sync） |
| 手册 | `entries/signs/kangel_sign.json`（sortnum 24） | `entries/signs/ame_sign.json`（sortnum 25） |
| 标签 | `data/astral_dice/tags/item/signs.json` + `data/curios/tags/item/stand.json`（两立牌都补；1.20.1 为 `tags/items/`） | 同左 |
| 赏金板 | **不进**（奇特档惯例：sherry/hanna 均不在 `astral_rews.json`） | 同左 |
| 创造栏 | `init/ModCreativeTabs`，接在 `HANNA_SIGN` 之后 | 接在 kangel 之后 |

## 3. 行为细节（冻结）

### 3.1 超天酱「网络天使」（主动）
- 判定顺序照 `BaseSignItem.performSkill` 既有链（未佩戴骰子 ⇒ 拒绝提示；冷却中 ⇒ 无效）。
- 效果（服务端）：自身获得 `min(1 + 粉丝数, 10)` 星币（走 `ResourceConversion.giveItem` 钱包漏斗）；粉丝数 ≥ 3 ⇒ `setHealth(getMaxHealth())`；粉丝数 ≥ 9 ⇒ 友方玩家各得 3 星币（**有队伍** ⇒ `PartyRelations.collectTeamPlayers` 全队；**无队伍** ⇒ 自身 64 格内其他玩家；两者都不含自己——自己的那份已由第一段发放）。
- 冷却：全局默认链（`WeirdDiceHandler.signCooldownTicks`，基础 180s，含诡异骰子 -50% 等减免），**不覆写** `activeCooldownBaseTicks`。
- 反馈：actionbar（`msg.astral_dice.kangel_*`，黄字，照 `sendSignActionBar`）。

### 3.2 超天酱「小天使请安」（被动）
- **施加入口（两条）**：① 佩戴者**近战直接命中**目标（照 sherry 侦探出击判据：只认 `getDirectEntity()` 是玩家本人的近战；弹射物/法术/AOE 不算）——挂点在 `DiceCombatEvents` 调用 sherry 的同一位置附近；② 佩戴者**受到敌对攻击**（`BaseSignItem.invokeHurtHooks` 的 `isHostileAttack` 闸门，环境伤害/自伤不算）⇒ 攻击者被施加。
- 「粉丝」状态 = `kangel_fan` 效果，HARMFUL，**无限时长**，目标死亡自然消失。**玩家身上有 `kangel_fan` ⇔ 她是被标记的粉丝**（`kangel_fan_indicator` 不算，见 §3.7）。
- **计数规则**：粉丝数 = 玩家级附件 `kangel_fan_count`，**上限 10**；**每个目标对每个超天酱玩家只计 1 次**（`kangel_fan_targets` 记录目标 UUID 集，照 sherry 已推理目标模式，设上限截断防无限增长）；目标被杀计数**不**减少；**跨死亡保留**（copyOnDeath）；**卸下立牌清零**（`clearSignData`：计数/目标集清零 + 移除自身指示器）。
- **同一触发的多方计入**：任何一次施加（含一心同体由地雷女触发的）使触发者队内**每个**超天酱佩戴者各 +1（各自判自己的目标集去重、各自不超 10）。
- **-1 伤害**：带 `kangel_fan` 的目标攻击**佩戴者**时，造成的伤害 -1（`ChipDamageHandler` @LOWEST 固定点数减法，照 sherry 挚友守护段；允许扣到 0；不可减免伤害类型除外）。

### 3.3 超天酱「一心同体」（备注）
- 前提：`PartyRelations.hasTeam` 且队内存在**地雷女立牌佩戴者**。
- 生效内容：§3.2 的两条施加入口对地雷女玩家同样成立（她近战命中/她被敌对攻击 ⇒ 目标上粉丝）；粉丝计数归队内每个超天酱佩戴者；粉丝目标攻击地雷女时伤害 -1（与 §3.2 同一减法段的第二判定分支）。

### 3.4 地雷女「感情过量摄入」（主动）
- 瞬时：立即恢复 `爱层数 × 2` 生命（`heal`，不超过最大生命）；经 `EffectTimerGuard.apply` 施加 `ame_overdose`（1200 tick，+20% 移速，图标可见计时）。
- **锁定（三态化）**：覆写 `startActiveLockOnUse`（照 fanny/fen/jasmine 模式），锁定跟随 `ame_overdose` 存续，到期后进入冷却（无空档）。
- **仅自然到期**（`MobEffectEvent.Expired`）触发结算：爱 **-4**（下限 0）、爱上限 **+1**（最高 32）；**奶/命令清除不触发**（不加移除钩子）。
- 卸下立牌 ⇒ `clearSignData` 同时移除 `ame_overdose`（不触发结算）。

### 3.5 地雷女「无法承受的爱」（被动）
- 佩戴期间基础防御 6 点；每层爱：骰战**攻击力 +1**（`DiceCombatModifiers.registerAttackModifier` 注册点，照 sherry 推理时间，条件 = 攻击者佩戴 ame 立牌）、**防御力 -1（最低 0）**。
- 防御力落地：`DiceCombatModifiers.setDefenseArmorBonus(player, "ame_love", max(0, 6 - 爱))`（×2.0 既有系数，不改）。
- **最大生命加成（2026-10-16 追加）**：爱 > 4 时，`MAX_HEALTH` 瞬态修饰器 `ADD_VALUE` = `爱 - 4`。
- **获得**：受到敌对攻击（同 §3.2 的 `isHostileAttack` 闸门）⇒ +1 层；**每 30 秒最多 1 层**（附件 `ame_love_gain_cd_end`；该闸门同时约束一心同体供给方送入的层数）；受自身上限约束（初始 4）。
- **装备时**：`onEquip`（Curios 签名 `onEquip(SlotContext, ItemStack prevStack, ItemStack stack)`，照芯片类先例）若当前 0 层 ⇒ 置 2 层。
- **寿命**：层数与上限**跨死亡保留**（copyOnDeath）；**卸下清零**（层数 0、上限回 4、移除 `ame_love`/`ame_overdose` 与全部修饰器）。

### 3.6 地雷女「一心同体」（备注）
- 前提：`PartyRelations.hasTeam` 且队内存在**超天酱立牌佩戴者**。
- 生效内容（**供给方口径**，裁决 #3）：超天酱玩家获得 **6 点基础防御**（`setDefenseArmorBonus(player, "ame_link", 6)`；不攒爱、无攻防修正、无最大生命加成）；超天酱玩家受到敌对伤害 ⇒ 队内**所有**地雷女佩戴者各 +1 爱（各自过自身 30 秒闸门与上限）。

### 3.7 指示器（裁决 #8）
- **`kangel_fan_indicator`** = 佩戴者自身的计数指示器：amplifier = 粉丝数 - 1（**等级 = 粉丝数**）、**无限时长**、BENEFICIAL、图标 = `粉丝.png` 加金色描边的变体；粉丝数 0 或卸下 ⇒ 移除。
- ⚠️ **语义边界（2026-10-17 拆 id）**：指示器**只做 HUD 镜像**，**任何战斗判据都不得读它**；`kangel_fan` 只作目标标记。卸下立牌只移除指示器，**不**移除玩家身上的目标标记（那是别的立牌给的）。
- `ame_love` = 爱层数镜像：amplifier = 层数 - 1、**无限时长**、图标 `爱.png`；层数变化时按「先移除再施加」刷新（照 SherryReasoningEffect.mirror，但**无 1 分钟衰减**、不走 `onPassiveStackExpired` 扣层链）；归 0 ⇒ 移除。
- 目标侧的 `kangel_fan` 标记：amplifier 0、无限时长（与上面两个指示器完全独立）。

### 3.8 动态修饰器重挂（防死亡/重登丢失）
- 附件跨死亡保留、瞬态属性修饰器不跨 ⇒ 两立牌在 `onCurioTick`（节流 20t）内按附件真值**幂等重挂**各自修饰器（ame：防御 + 最大生命；kangel：一心同体 6 防御的施加/移除），自带重生/登录/队伍变动自愈。

## 4. Tooltip 文案（逐字冻结）

> 用户红线：主动/被动/备注内容**逐字引入原话**，不增删；仅允许按 tooltip 染色规则加 § 码（数值黄 `§e`、时间蓝 `§9`、回落 `§7`）与 `%%` 转义。数字与汉字之间的空格保持原话形态。

- **kangel 主动**（标题：网络天使）：`立即获得（1+ 粉丝数）的星币，最多可获得10个。如果粉丝数不少于3个，还会使生命值立即恢复全满。如果玩家粉丝数不少于9个，还会额外使得友方玩家获得3星币`
- **kangel 被动**（标题：小天使请安）：`玩家主动攻击或攻击玩家的目标会被施加“粉丝”状态，该目标攻击超天酱时，造成的伤害-1`
- **kangel 备注**（紫色，无标题）：`一心同体：当玩家队伍中有地雷女时，使得该角色被动技能同样对其生效`
- **ame 主动**（标题：感情过量摄入）：`立即恢复“爱”层数x2的生命值，以及移动速度加成 +20%，持续1:00。时间结束后扣除4层“爱”，并使“爱”层数上限+1（最多32层）`（⚠️ 原话的「（百分比）」按裁决 #1 落实为 `+20%`，属数值落实、非内容增删）
- **ame 被动**（标题：无法承受的爱）：`初始状态下玩家拥有6点防御力。受到伤害时，获得1层“爱”，每 30秒 只能获得1层。初始状态上限只有4层，玩家装备地雷女立牌时的初始层数为2层`
- **ame 备注**（紫色，无标题）：`一心同体：当玩家队伍中有超天酱时，使得该角色被动技能同样对其生效。`
- 计数器行：kangel = `粉丝数：§e%s§7 / §e%s§r`（当前/10）；ame = `爱：§e%s§7 / §e%s§r`（当前/上限）。
- 补充说明全文进**手册条目**（tooltip 不放）。

## 5. 手册条目

- `entries/signs/kangel_sign.json`（sortnum 24）/ `ame_sign.json`（sortnum 25），页面结构照 sherry：spotlight（介绍+主动）→ text（被动）→ text（备注 + 补充说明全文）→ crafting。
- lang 键 `astral_dice.guide.entry.kangel_sign.1..3` / `astral_dice.guide.entry.ame_sign.1..3`，zh_cn + en_us 双写；`tools/audit_patchouli_keys.py` 必须无悬空。

## 6. lang 键清单（每线 zh_cn + en_us）

- 物品：`item.astral_dice.kangel_sign` / `item.astral_dice.ame_sign`
- 效果（name + description 成对）：`effect.astral_dice.kangel_fan`（粉丝）、`effect.astral_dice.kangel_fan_indicator`（粉丝数）、`effect.astral_dice.ame_love`（爱）、`effect.astral_dice.ame_overdose`（感情过量摄入）
- tooltip：`tooltip.astral_dice.sign.kangel_active` / `kangel_passive` / `kangel_note` / `kangel_fans`；`tooltip.astral_dice.sign.ame_active` / `ame_passive` / `ame_note` / `ame_love_layers`
- actionbar：`msg.astral_dice.kangel_*`（主动结果：获得星币数/回满/友方发放）、`msg.astral_dice.ame_*`（主动结果、到期结算）
- guide：§5 六键
- 守门：`tools/check_lang_sync.ps1` 两线 exit=0；`scripts/audit/tooltip_color_audit.ps1` PASS。

## 7. 验收守门（两线全绿为准）

- `scripts/test/mt_build.ps1`（Windows 侧 pwsh 互操作）两线 OK；
- 两线 `runData`：模型 + 配方 + 进度各 2 组生成物落盘并提交；
- `scripts/verify/verify_crafting_recipe_uniqueness.ps1`：两条新配方 0 碰撞；
- `tools/verify_effect_icons.py`：3 新效果图标覆盖；
- `tools/audit_patchouli_keys.py`、`tools/check_lang_sync.ps1`、`scripts/audit/tooltip_color_audit.ps1`、`verify_bountiful_pools.ps1`（回归）、`scripts/verify/verify_resource_integrity.ps1`。
- 版本号四线同步 bump（.127 → .128，含未改动的 P1 两线）；两份 CHANGELOG 各 +1 条；AGENTS.md 立牌/效果/lang 计数**实测后订正**。

## 8. 假定（用户未指定，按既有口径锁定）

1. 两主动冷却均走全局默认链，不覆写 `activeCooldownBaseTicks`。
2. 粉丝可对**敌对玩家**（PvP）施加，-1 伤害同样生效（照 wiki「角色和怪物」口径）。
3. 「爱」获得仅限敌对攻击（环境伤害/自伤不算，2026-09-29 全体受击被动裁决）。
4. 一心同体的「队伍中」= `PartyRelations.hasTeam`（计分板/FTB/OPAC 并集）；无队伍不生效（照 sherry 挚友守护）。
5. 效果配色：kangel_fan=HARMFUL、kangel_fan_indicator / ame_love / ame_overdose=BENEFICIAL；EN 文案按既有风格翻译。
6. 实机测试不在本批（用户另行下达）；交付登记实机欠账。
