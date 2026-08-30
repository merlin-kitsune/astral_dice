# Changelog / 更新日志

## 未发布 / Unreleased（multiloader 迁移）

### 新内容 / New Content
- 骰神赐福期间,骰子卡牌界面禁止插入/移除卡牌(服务端权威 + 客户端同步拦截),界面顶部显示红色提醒文字(仅 1.21.1)。 / During the Dice Blessing, cards can no longer be inserted into or removed from the dice card interface (server-authoritative with client-side blocking), and the screen shows a red warning at the top (1.21.1 only).
- 手册「基本介绍」分类图标更换为「头号玩家立牌」(仅 1.21.1)。 / The handbook's "Getting Started" category icon is now the "No.1 Player Sign" (1.21.1 only).
- 效果牌复制/返还覆盖全部效果牌(忍者立牌/魔法秘典/魔法箭袋不再有排除项;含治疗/伤害/专属牌,专属牌复制后绑定获得者)(仅 1.21.1)。 / Effect-card copy/refund now covers every effect card — the Komachi sign, Magic Tome and Magic Quiver no longer exclude any card (healing/damage/exclusive cards included; exclusive copies are bound to their new owner) (1.21.1 only).

### 已修复BUG / Bug Fixes
- **1.20.1 同步修复**:效果移除拦截误伤本模组主动移除(新增内部移除通道,待命/计数/治愈效果不再永久残留)、以毒攻毒 CME 崩溃风险、效果牌复制/返还错发王之力、随机卡牌池不一致(ALL 补齐以毒攻毒、BATTLE 含防御牌)、帕鲁南手持释放绕过冷却、FTB Teams UUID 成员永不入队、AOE(顺劈/溅射)目标二次吃骰战、标记发光 HUD 图标隐藏、4 个 tooltip 单 `%` 转义、技能改名(运功/远程侵入)。 / **1.20.1 synced fixes**: removal guard no longer swallows the mod's own removals (new internal-removal channel — indicators no longer linger), 以毒攻毒 CME risk, wrong King-Power copy/refund mappings, diverging random card pools (ALL gains 以毒攻毒, BATTLE includes defense cards), Parunan in-hand cooldown bypass, FTB Teams UUID members never added, AoE victims taking a second full dice-combat hit, mark-glow HUD icon hidden, 4 tooltip `%` escapes, skill renames (运功/远程侵入).
- **1.20.1 专属修复**:骇客立牌 `onCurioTick` 隐身到期判断在附件默认 0 时恒真,每 tick 误删其他来源的隐身;卸载仅清除自身授予的无敌/隐身(附件有效时)(仅 1.20.1,1.21.1 同款问题待同步)。 / **1.20.1-specific fix**: Nancy Lu's `onCurioTick` expiry check was always true at the default attachment value, stripping invisibility from other sources every tick; unequip now only clears invulnerability/invisibility the sign granted itself (1.20.1 only; same latent issue exists in 1.21.1).
- 顺劈/定向爆破等 AOE 波及的目标不再进入骰战结算(此前被波及目标会二次吃到完整骰战伤害)(仅 1.21.1)。 / Targets hit by AoE (cleave / directional blast) no longer go through the dice-combat pipeline — previously they could take a full second dice-combat hit (1.21.1 only).
- 骇客立牌卸载仅清除自身授予的无敌/隐身(附件标记有效时),不再误清其他来源的公共状态(仅 1.21.1)。 / Unequipping the Nancy Lu sign now only clears the invulnerability/invisibility it granted itself (while its attachments are active) and no longer wipes public state from other sources (1.21.1 only).
- 修复帕秋莉手册部分条目显示 `Format error` 的问题:guide 文本中的字面百分号未转义(18 个条目,中英 36 条),经 `I18n.get`/`String.format` 时抛异常;已统一改为 `%%`,并同步修复 4 个同隐患 tooltip key(仅 1.21.1)。 / Fixed "Format error" showing in some Patchouli handbook entries: literal percent signs in guide texts (18 entries, zh+en) were unescaped and broke `I18n.get`/`String.format`; they are now written as `%%`, and the 4 tooltip keys with the same latent issue were fixed too (1.21.1 only).
- 修复牛奶/`/effect clear` 清除发光但保留标记的问题:标记携带的发光效果与标记同寿命,标记仍存在时发光不再被单独清除(仅 1.21.1)。 / Fixed glow being cleared by milk or `/effect clear` while the mark persisted: the glow carried by a mark now shares its lifetime, and is no longer removed alone while the mark is still active (1.21.1 only).
- 标记携带的发光效果不再显示 HUD 效果标识器(仅保留实体轮廓发光,不占用效果图标栏;仅 1.21.1)。 / The glow carried by a mark no longer shows a HUD effect icon — only the entity outline remains, without occupying the effect indicator row (1.21.1 only).

### 工程 / Project
- **1.20.1 同步**:移植卡牌栏赐福锁定(服务端权威 clicked/quickMoveStack 拦截 + 客户端红色提醒)并随之移除 `charge_defer` 延迟转换逻辑(赐福结束一律返还全力攻击);移除写而未读的 PlayerResourceRegistry;统一效果牌复制映射与随机卡牌池;跳数字发送 3 处重复收敛为 ModNetwork.DamageNumberMessage.send;清理死代码(空覆写、isChipItem 手写链、骰子冗余构造参数、8 个未用数据键、maxAttackCardPoints/rollDice 重载、重复模型生成等)。 / **1.20.1 synced**: ported the Dice-Blessing card-slot lock (server-authoritative clicked/quickMoveStack guards + red client warning) and removed the `charge_defer` deferral logic (charge always converts to Full Power when the blessing ends); removed the write-only PlayerResourceRegistry; unified the effect-card copy mapping and random card pools; consolidated the 3 duplicated damage-number senders into ModNetwork.DamageNumberMessage.send; removed dead code (empty overrides, the isChipItem chain, redundant dice ctor args, 8 unused data keys, maxAttackCardPoints/rollDice overload, duplicate model generation, etc.).
- 移除写而未读的 PlayerResourceRegistry(PlayerResource/ResourceType 与治愈/星光注册实现)(仅 1.21.1)。 / Removed the write-only PlayerResourceRegistry (PlayerResource/ResourceType and the healing/starlight registration stubs) (1.21.1 only).
- ModEventHandlers 订阅拆分到各自功能实现处:骰战管线 → `combat/DiceCombatEvents`、tooltip → `ModTooltipHandler`、战利品 → `LootInjectionHandler`、铁砧 → `AnvilUpgradeHandler`、生命周期 → `PlayerLifecycleHandler`、效果守卫/计时器 → `ModEffectEvents`、玩家 tick → `PlayerTickEvents`;击杀/受击/治疗等事件移入对应立牌/筹码/卡牌/管理器类(仅 1.21.1)。 / ModEventHandlers subscribers moved to their feature homes: dice combat → `combat/DiceCombatEvents`, tooltips → `ModTooltipHandler`, loot → `LootInjectionHandler`, anvil → `AnvilUpgradeHandler`, player lifecycle → `PlayerLifecycleHandler`, effect guard/timer → `ModEffectEvents`, player tick → `PlayerTickEvents`; kill/hurt/heal events now live in their sign/chip/card/manager classes (1.21.1 only).
- 移除蓄力卡"赐福进行中放入"的延迟转换验证代码(`charge_defer` 附件及相关分支)——卡牌栏赐福锁定后该场景不再可能发生;骰神赐福结束后返还「全力攻击」保持不变(仅 1.21.1)。 / Removed the charge-deferral timing code for Charge cards placed mid-blessing (the `charge_defer` attachment and its branches) — impossible now that the card slots lock during the Blessing; Full Power is still refunded when the Blessing ends (1.21.1 only).
- 全量代码审计与清理(仅 1.21.1):修复效果移除拦截误伤本模组主动移除的问题(新增内部移除通道,待命/计数/治愈等效果不再永久残留);修复以毒攻毒移除效果时遍历实时视图的并发修改崩溃风险;统一随机卡牌池(ALL 补齐以毒攻毒、BATTLE 按定义含防御牌)与效果牌复制/返还映射(以毒攻毒/活体书页不再错发王之力);帕鲁南立牌手持释放与立牌栏共用冷却/等待校验;修复 FTB Teams UUID 成员永不入队;移除每 tick 重复施加效果导致的同步包(美工刀/复仇之戟/治愈显示)与每 tick 附魔资源键分配;修复客户端伤害数字因错误总线订阅永不消失;跳数字发送、效果牌类型映射等重复实现收敛为单一入口;删除 8 个未使用数据组件、大量未使用方法/导入/空覆写与死分支。 / Full code audit and cleanup (1.21.1 only): the effect-removal guard no longer swallows the mod's own removals (new internal-removal channel — ready/count/healing indicators no longer linger forever); fixed a ConcurrentModificationException risk when 以毒攻毒 removes effects while iterating a live view; unified random card pools (ALL now includes 以毒攻毒, BATTLE includes defense cards per its contract) and the effect-card copy/refund mapping (以毒攻毒/活体书页 no longer wrongly grant King Power); Parunan's in-hand active now shares the cooldown/wait checks with the equipped path; fixed FTB Teams UUID members never being added; removed per-tick effect re-apply sync packets (cutter/revenge-halberd/healing displays) and per-tick enchantment ResourceKey allocation; fixed client damage numbers never expiring due to a wrong-bus subscription; consolidated duplicated damage-number senders and card-type mappings; removed 8 unused data components, unused methods/imports, empty overrides and dead branches.
- 仓库结构迁移为 **multiloader 单仓**（改编自 Player005/multiloader-mod-template）：原 `astra_dice` 仓库 `1.21.1-main` 分支整体迁入 `neoforge-1.21.1/` 子项目，`1.20.1-forge` 分支整体迁入 `forge-1.20.1/` 子项目；因跨 MC 大版本 API 差异，不设 common 共享源码层，两子项目沿用各自原构建脚本。 / Repository restructured as a **multiloader monorepo** (adapted from Player005/multiloader-mod-template): the `1.21.1-main` branch of the former `astra_dice` repo moved wholesale into the `neoforge-1.21.1` subproject and the `1.20.1-forge` branch into `forge-1.20.1`. Due to cross-MC-version API differences there is no shared `common` source set; each subproject keeps its original build scripts.
- 原仓库 git 排除内容（AGENTS.md、docs/、temp/、scripts/、run/ 开发环境、deploy.ps1、opencode.json、.zcode/ 等）一并迁入；`tools/check_lang_sync.py`（原 1.20.1 分支）移至根 `tools/`。 / Formerly git-excluded content (AGENTS.md, docs/, temp/, scripts/, run/ dev environments, deploy.ps1, opencode.json, .zcode/) migrated as well; `tools/check_lang_sync.py` (from the 1.20.1 branch) moved to the root `tools/` directory.
- CI(build.yml) 改为双 JDK(17/21) 构建两个子项目并对两者执行 lang 同步检查。 / CI (build.yml) now builds both subprojects with dual JDKs (17/21) and runs the lang sync check for both.


## 1.1.2-rc1

### 新内容 / New Content
- **复仇之戟**:tooltip 新增当前攻击力/防御力加成显示;触发任意加成时显示"复仇之戟"状态效果(图标为物品本体图标),加成消失时自动移除。 / **Revenge Halberd**: tooltip now shows the current attack/defense bonus; a status effect with the halberd's own icon appears while either bonus is active and is removed when the bonus ends.
- 新增**计时器守卫**:本模组所有有时长效果严格按 20 tick/秒 流动,不受任何加快/减慢效果时间的 buff(如神秘遗物+ 的烈焰之核)影响。 / Added a timer guard: all timed effects of this mod now strictly tick at 20 t/s, immune to buffs that speed up or slow down effect durations (e.g. the Blazing Core from Enigmatic Legacy+).
- 卡牌栏选择器改为 **3 列** 显示,同屏可见更多卡牌。 / The card selector now displays **3 columns**, showing more cards at once.
- 同步本地中文文本修改(看板立牌 tooltip 措辞、命运的指引描述)。 / Synced local zh_cn.json text updates (Mimi sign tooltip wording, Fate Guidance description).
- 移除 CurseForge / Modrinth 独立更新日志文件及其关联。 / Removed the separate CurseForge and Modrinth changelog files and their references.

### 内容与平衡性调整 / Balance Changes
- 岿然不动、狂暴持续时间调整为 **3:00**。 / Unwavering and Berserk durations adjusted to **3:00**.

### 已修复BUG / Bug Fixes
- 修复骰神赐福进行中放入蓄力会立即转换为全力攻击的问题:赐福进行中放入的蓄力本次赐福不生效、不转换,改为下次触发骰神赐福时生效并在其结束时转换。 / Fixed Charge converting to Full Power immediately when placed during an active Dice Blessing: a Charge placed mid-blessing is inactive for the current blessing and now only takes effect on the next blessing, converting to Full Power when that blessing ends.
- 修复标记效果消失后发光效果残留的问题:发光改为与标记同寿命(不再使用无限时长),多层标记同步刷新,标记结束时发光随之消失。 / Fixed the glowing effect lingering after the mark disappears: glowing now shares the mark's lifetime (no more infinite duration), is refreshed on layer transitions, and ends together with the mark.

### 工程 / Project
- 版本号更新为 `1.1.2-rc1`。 / Version updated to `1.1.2-rc1`.
## 1.1.1-rc1

### 新内容 / New Content
- 看板立牌（mimi）技能重做：被动改为“合成、奖励、返还卡牌时，每获得一张战斗牌 +1 星币；装备时筹码栏位 +1；每累计 25 个星币获得 1 个随机筹码（蓝色 60% / 紫色 35% / 金色 5%）”；主动改为“回收物品栏中全部卡牌（含专属牌），返还 N+1 张随机卡牌（不含专属牌）”。 / Mimi sign reworked: passive now grants +1 Star Coin per battle card gained from crafting, rewards, or card returns, +1 chip slot while equipped, and a random chip every 25 Star Coins (Blue 60% / Purple 35% / Gold 5%); active now recycles all cards in the inventory (including exclusive cards) and returns N+1 random cards (exclusive cards excluded).
- 新增通用谜之炖菜配方；诅咒之剑配方改用 `astral_dice:suspicious_stews` 标签。 / Added a generic Suspicious Stew recipe; Cursed Sword now uses the `astral_dice:suspicious_stews` tag.

### 界面与显示 / UI & Display
- 新增骇客立牌“远程骇入”效果图标与描述；立牌主动技能 ActionBar 改用本地化名称；命运指引备注颜色修正。 / Added Nancy Lu "Remote Hack" effect icon and description; sign active-skill ActionBar now uses localized names; fixed Fate Guidance note color.

### 已修复BUG / Bug Fixes
- 修复治愈图标在计时结束且未再次触发时未移除的问题。 / Fixed the healing icon not being removed when its timer ends without re-triggering.

### 工程 / Project
- 版本号更新为 `1.1.1-rc1`。 / Version updated to `1.1.1-rc1`.

## 1.1.0-rc1

### 新内容 / New Content
- 新增效果牌：以毒攻毒（移除最多 3 个原版负面效果，并获得生命恢复）。 / Added Fight Poison with Poison: removes up to 3 vanilla negative effects and grants Regeneration.
- 新增效果：青之诅咒（护甲值 -20%，盔甲韧性归 0）。 / Added Blue Curse: -20% armor and zero armor toughness.
- 新增筹码：维生素药丸、诅咒之剑、复仇之戟、贯穿之铳、可口糖果、友情徽章、探天卫星。 / Added chips: Vitamin Pill, Cursed Sword, Revenge Halberd, Piercing Gun, Candy, Friendship Badge, Satellite.
- 新增立牌：骇客立牌（nancy_lu）。 / Added Nancy Lu (Hacker) sign.
- 新增手持风扇-小 / 手持风扇-大筹码。 / Added Hand Fan Small and Hand Fan Big chips.

### 内容与平衡性调整 / Content & Balance Changes
- 手持风扇-小配方调整为羽毛上排；手持风扇-大改为通用蓝→紫升级配方（以手持风扇-小为原料）。 / Adjusted Hand Fan Small recipe; Hand Fan Big now uses the generic Blue→Purple upgrade recipe.
- 狂暴配方简化为 1 火药 + 1 星币。 / Simplified Berserk recipe to 1 Gunpowder + 1 Star Coin.
- 诅咒之剑：骰神赐福期间每轮最多触发一次击杀攻击力加成；上限默认 16、最大 32。 / Cursed Sword kill bonus now triggers at most once per Dice Blessing; cap default 16, max 32.
- 占星师立牌、忍者立牌品质提升为史诗，配方由黄金骰子改为钻石骰子。 / Astrologer and Ninja signs upgraded to Epic and now require Diamond Dice.
- 完全隐身期间生物无法将玩家设为索敌目标。 / While fully invisible, mobs cannot target the player.
- 创造栏筹码按星光、治愈、标记、无流派分类摆放；美工刀归入治愈类。 / Creative chip tab regrouped by Starlight, Healing, Mark, and No-school; Cutter chips moved to Healing.

### 界面与显示 / UI & Display
- 治愈类筹码与史莱姆立牌 tooltip 显示当前治愈点。 / Healing chips and Slime sign now show current healing points.
- 修复 tooltip 中按键名、`%` 号、计数器符号无法正确染色的问题。 / Fixed sign key, percent sign, and counter symbol coloring in tooltips.
- 修复诅咒之剑 tooltip 中青之诅咒描述多余空行。 / Fixed an extra blank line around Blue Curse in the Cursed Sword tooltip.
- 立牌/材料 tooltip 分类从 `card` 移至 `sign`/`material`。 / Moved sign and material tooltip keys out of the card category.

### 已修复BUG / Bug Fixes
- 修复大侦探立牌被动无法触发（主动技能与击杀“隐匿调查”目标）。 / Fixed Great Detective passive not triggering from active skill or Undercover Investigation kills.
- 修复维生素药丸 tooltip 缺失；拾取卡牌不再触发维生素药丸效果。 / Fixed missing Vitamin Pill tooltip; picking up cards no longer triggers it.
- 友情徽章配方改为必须使用瞬间治疗药水。 / Friendship Badge recipe now requires an Instant Healing potion.
- 修复 tooltip 颜色代码导致部分文本变白的问题。 / Fixed tooltip color-code issues causing some text to appear white.

## 1.0.3-rc1

### 内容与平衡性调整 / Content & Balance Changes
- 防御牌耐久消耗恢复为仅 PvP 生效：玩家攻击带骰子的玩家时，双方触发骰神赐福，并消耗被攻击方防御牌耐久（每次赐福仅一次）；怪物攻击不再消耗防御牌耐久。 / Defense card durability is now only consumed in PvP: when attacking another dice-holding player, both gain Dice Blessing and the defender consumes defense card durability once per blessing; monster attacks no longer consume it.
- 新增防御力/护甲值换算：1 防御力 = 2 护甲值。 / Added a defense-to-armor conversion: 1 defense = 2 armor.
- 效果牌、立牌、事件、筹码提供的防御力，无论是否触发骰神赐福，均按 1:2 折算为护甲值；只有战斗防御牌直接作为防御点加入骰神赐福。 / Defense bonuses from effect cards, signs, events, and chips always convert to armor at 1:2 regardless of Dice Blessing; only battle defense cards add directly to defense points.
- 重写“岿然不动”：使用后防御力 +2，并获得 抗性提升 II，持续 1:00。 / Reworked Unwavering: grants +2 defense and Resistance II for 1:00.

### 工程 / Project
- 版本号更新为 `1.0.3-rc1`。 / Version updated to `1.0.3-rc1`.

## 1.0.2-rc1

### 内容与平衡性调整 / Content & Balance Changes
- 调整骰子筹码栏位数量（按 0★~3★）：普通 0/1/2/3，黄金 1/2/3/4，钻石 2/3/4/5，下界合金 3/4/5/6。 / Adjusted dice chip slot counts (0★~3★): Basic 0/1/2/3, Golden 1/2/3/4, Diamond 2/3/4/5, Netherite 3/4/5/6.
- 调整骰子攻击/防御卡牌栏数量（每侧）：普通 3，黄金 4，钻石 5，下界合金 6。 / Adjusted per-side attack/defense card slots: Basic 3, Golden 4, Diamond 5, Netherite 6.
- 主动触发“骰神赐福”时不再消耗防御牌耐久。 / Actively triggering Dice Blessing no longer consumes defense card durability.
- 玩家若攻击带有骰子的玩家，则自动触发双方拥有骰子玩家的骰神赐福，并消耗被攻击方的防御牌耐久度（每次骰神赐福仅消耗一次）。 / When a player attacks another player with a dice, both dice-holding players automatically gain Dice Blessing, and the defender's defense card durability is consumed at most once per blessing.
- 秘密侦探立牌击杀奖励只返还攻击牌，攻击玩家不返还卡牌。 / Bonnie sign kill rewards now grant only attack cards, and attacking players grants no card.
- 所有效果牌、立牌、事件、筹码提供的防御力加成，默认只作为护甲值加成；触发骰神赐福后作为防御点加入。 / Defense bonuses from effect cards, signs, events, and chips now count as armor value by default, and become defense points while Dice Blessing is active.
- 防御卡仅在骰神赐福期间作为防御点生效。 / Defense cards only provide defense points during Dice Blessing.

### 界面与显示 / UI & Display
- 所有立牌 Tooltip 增加主动/被动技能名称，并将主动技能按键提示移至最上方。 / Added active/passive skill names to all sign tooltips and moved the key hint to the top.
- 主动技能按键提示整体改为白色，按键符本身为黄色。 / The sign key hint is now white, with the key symbol in yellow.
- 大当家立牌主动技能图标改为使用大当家立牌图标。 / The Fen sign active skill icon now uses the Fen sign icon.
- 统一 Tooltip 时间与数值格式：时间蓝色、数值黄色、不足 1:00 使用秒、药水时间使用括号。 / Unified tooltip formatting: blue time, yellow values, seconds for durations under 1:00, and parentheses around potion effect times.

### 已修复BUG / Bug Fixes
- 移除立牌主动技能触发成功后的通用“技能已激活”ActionBar，避免覆盖各立牌自身的特殊提示。 / Removed the generic “Skill activated” ActionBar after sign skills trigger, preventing it from overwriting sign-specific messages.
- 修复溅射/范围伤害未在被影响目标身上显示伤害数字的问题（大当家立牌扩散、定向爆破 AOE）。 / Fixed missing damage numbers on affected targets for splash/AOE damage (Fen cleave and Directional Blast AOE).
- 本 Mod 创建的自定义效果无法被牛奶、蜂蜜瓶或 `/effect clear` 清除，仅玩家死亡可清除。 / Custom effects created by this mod can no longer be removed by milk, honey bottles, or `/effect clear`; only player death can remove them.
- 修复玩家死亡时部分效果状态未正确重置的问题，统一清理立牌等待、扩散、出牌、魔法箭袋、命运指引、调查阶段等状态。 / Fixed some effect states not being reset on player death; sign-ready, cleave, play-count, Magic Quiver, Fate Guidance, and investigation states are now reset properly.

## 1.0.1-rc1

### 内容与平衡性调整 / Content & Balance Changes
- 重新设计治愈体系：为治愈增加独立计时器，默认每 30 秒触发一次治愈效果（仅在骰神赐福期间）。 / Reworked the healing system with an independent timer: healing triggers every 30 seconds by default (only during Dice Blessing).
- 触发骰神赐福时按治愈点×2 回血，计时结束治愈点减半。 / Healing Points heal ×2 when Dice Blessing triggers, and halve when the timer ends.
- 美工刀触发阈值改为最大生命值 60% 以上即可触发，避免大部分时候都触发不了。 / Cutter chips now trigger above 60% of max HP, preventing them from being unable to trigger in most situations.
- 护法立牌：主动技能攻击力加成提高至 +4，持续时间提高至 2:00。 / Misaki sign: active skill attack bonus increased to +4 and duration increased to 2:00.
- 吸血鬼立牌：主动效果调整第二项为：不论玩家当前血量为多少，都同时视为满血和半血以下状态。 / Papara sign: the second active effect now treats the player as both full HP and below half HP regardless of current health.
- 秘密侦探立牌：被动技能返还卡牌只对击杀 20 血以上敌对目标生效。 / Bonnie sign: passive card reward now only applies when killing hostile targets with more than 20 HP.
- 巧克力蛋糕/汉堡改为恢复最大生命值 20%/40%；奢华大餐改为恢复自身最大生命值 30%，并治疗同队/无队伍玩家。 / Chocolate Cake and Hamburger now heal 20%/40% of max HP; Luxury Feast heals 30% of the user's max HP and also heals teammates and teamless players.
- 治愈盾牌触发间隔改为 15 秒。 / Buffer Shield trigger cooldown changed to 15 seconds.
- 调整战斗牌耐久值：中/大/特大=10，暗影突袭=10，名刀=5，蓄力=1，全力攻击=2。 / Adjusted battle card durability values: Medium/Large/Epic=10, Shadow Strike=10, Meito=5, Charge=1, Full Power=2.
- 蓄力在赐福结束后返还全力攻击，并增加兜底检测与 ActionBar 提示。 / Charge now refunds Full Power after blessing, with fallback check and ActionBar message.

### 已修复BUG / Bug Fixes
- 修复骰神赐福的触发目标判定和武器判定问题：仅近战武器触发，且友好/被动/未激怒中立生物不再触发。 / Fixed Dice Blessing target and weapon checks: it now only triggers with melee weapons and no longer triggers on friendly, passive, or non-angered neutral mobs.
- 修复骰子/筹码/立牌下蹲右键装备不生效。 / Fixed sneak-right-click auto-equip for dice/chips/signs.
- 修复战斗牌耐久条显示异常、Tooltip 丢失问题，恢复剩余次数显示。 / Fixed battle card durability bar display issues and missing tooltip, restored remaining uses display.
- 修复大当家立牌触发伤害扩散时意外递归导致游戏崩溃。 / Fixed an unexpected recursion crash when Fen sign triggered damage cleave.
- 治愈效果不再产生药水粒子。 / Healing effect no longer shows potion particles.

### 质量更新 / Quality Improvements
- 为部分行为增加 ActionBar 提示，同时受到事件影响的玩家也会有 ActionBar 提示。 / Added ActionBar feedback for several actions, and players affected by events also receive ActionBar messages.
- 大侦探随机事件提示会显示具体事件名称。 / Fanny random event messages now show the triggered event name.
- 微调骰子卡牌选择界面的卡牌显示。 / Fine-tuned card display in the dice card selection GUI.
- 替换卡牌选择界面背景贴图。 / Replaced the card selection background texture.
- 修复 GitHub Actions 中 `gradlew` 无执行权限问题，并增加构建产物上传。 / Fixed gradlew permission in CI and added artifact upload.
- 更新 README 模组介绍。 / Updated README with mod introduction.
- 版本号更新为 `1.0.1-rc1`。 / Version updated to `1.0.1-rc1`.

## 1.0-rc1

### 卡牌选择界面重写 / Card Selection GUI Rewrite
- 使用了新卡牌容器贴图。 / Used the new card container texture.
- 右侧显示攻击/防御 `<下限>-<上限>` 范围，并实时随卡牌增删更新。 / Right side now shows attack/defense `<min>-<max>` ranges, updating live as cards are added or removed.
- 下方新增卡牌选择器：攻击左列、防御右列，按物品栏顺序排列，支持滚动。 / Added a card selector at the bottom: attack on the left, defense on the right, sorted by inventory order, with scrolling support.
- 支持点击下方存放区域/右键将手持卡牌放回物品栏。 / Added support for returning a held card to the inventory by clicking the storage area or right-clicking.
- 暗影突袭、名刀、蓄力、全力攻击图标缩放并向右下对齐。 / Shadow Strike, Meito, Charge, and Full Power icons are scaled and aligned to the bottom-right.

### 耐久度机制 / Durability System
- 战斗牌改用 MC 原生耐久数据（damage/maxDamage），可被 Durability Tooltip 模组识别。 / Battle cards now use vanilla Minecraft durability data (damage/maxDamage), recognized by Durability Tooltip mods.
- 移除战斗牌自定义“剩余次数”Tooltip 文本。 / Removed the custom "remaining uses" tooltip text from battle cards.
- 防御牌现在会在受到伤害时正确消耗耐久。 / Defense cards now correctly consume durability when the wearer takes damage.

### 配方调整 / Recipe Adjustments
- 忍者立牌：铁锭 → 回响碎片。 / Komachi sign: Iron Ingot → Echo Shard.
- 占星师立牌：青金石块 → 海晶砂砾。 / Haiqing sign: Lapis Block → Prismarine Crystals.
- 魔法秘典：中间书与笔 → 回响碎片。 / Magic Tome: center Book and Quill → Echo Shard.
- 魔法箭袋：中间光灵箭 → 回响碎片。 / Magic Quiver: center Spectral Arrow → Echo Shard.
- 忍术飞镖：磁石 → 红石块。 / Ninja Star: Lodestone → Redstone Block.

### 效果牌提示 / Effect Card Feedback
- 出牌数用完后显示 ActionBar 提示，冷却按所有效果牌中最长剩余时间计算。 / Shows an ActionBar message when play count is exhausted; cooldown uses the longest remaining time among all effect cards.

### 工程配置 / Project Configuration
- 版本号更新为 `1.0-rc1`。 / Version updated to `1.0-rc1`.

## 1.0-SNAPSHOT.23

### 物品 ID 与 Tag / Item IDs & Tags
- 效果牌统一为 `effect_card_*`。 / Effect cards unified to `effect_card_*`.
- 筹码统一为 `*_chip`。 / Chips unified to `*_chip`.
- 活体书页改为 `effect_card_living_page`。 / Living Book Page renamed to `effect_card_living_page`.
- 新增物品 Tag：`dices`、`combat_cards`、`effect_cards`、`is_exclusive`、`signs`、`chips`、`materials`。 / Added item tags: `dices`, `combat_cards`, `effect_cards`, `is_exclusive`, `signs`, `chips`, `materials`.

### 配方 / Recipes
- 基础骰子改为红石 + 石英块。 / Basic dice recipe changed to Redstone + Quartz Block.
- 黄金/钻石/下界合金骰子统一为 `dice_upgrade` 有序升级；移除下界合金骰子锻造配方。 / Golden/Diamond/Netherite dice now use the `dice_upgrade` shaped upgrade; removed the Netherite dice smithing recipe.
- 调整大量卡牌、效果牌、立牌、筹码配方。 / Adjusted many battle card, effect card, sign, and chip recipes.
- 筹码通用升级模板： / Chip generic upgrade templates:
  - 蓝→紫：`LGL/GTG/PPP` / Blue→Purple: `LGL/GTG/PPP`
  - 紫→金：`RDR/DTD/GGG` / Purple→Gold: `RDR/DTD/GGG`
- 定向爆破改为有序对称配方。 / Directional Blast changed to a shaped symmetrical recipe.

### 战利品 / Loot
- 星盘可从所有原版宝箱开出。 / Star Plates can now be found in all vanilla chests.
- 黄金星盘可从 `minecraft:chests/trial_chambers/reward_ominous` 开出。 / Golden Star Plates can be found in `minecraft:chests/trial_chambers/reward_ominous`.

### 创造栏 / Creative Tab
- 材料移至最前端。 / Materials moved to the front of the creative tab.
- 调整效果牌与活体书页顺序。 / Adjusted the order of effect cards and Living Book Page.

### 数值与 Tooltip / Values & Tooltip
- 拳击手套攻击：+2/+4/+8。 / Boxing Gloves attack: +2/+4/+8.
- 速度轮滑移速：+5%/+15%/+25%。 / Speed Skates movement speed: +5%/+15%/+25%.
- 摩托头盔防御：+2/+4/+6；高级额外盔甲韧性 +2。 / Moto Helmet defense: +2/+4/+6; High tier additionally grants +2 armor toughness.
- 夹心饼干生命：+4/+8/+12。 / Sandwich max health: +4/+8/+12.
- 缓冲盾牌冷却改为 30 秒。 / Buffer Shield cooldown changed to 30 seconds.
- 手电筒：每 4 点星光 +1 攻击力。 / Flashlight: +1 attack per 4 Starlight.
- 效果牌 Tooltip 新增出牌数与当前周期伤害加成提示。 / Added effect card tooltip hints for play count and current-cycle damage bonus.
