# B32 — fabric-1.20.1 语言文案残留漂移对账与对齐报告

- 生成日期:2026-11-01
- 基准(权威):neoforge-1.21.1 主基准 / forge-1.20.1 次基准;依据 2026-11-01 用户裁决「以最新主线版本为准,所有旧版本差异全部放弃」
- 范围:纯文案(三语 lang json);未改动任何 .java / build.gradle / gradle.properties,未启动游戏,未部署
- 关键前提:neoforge-1.21.1 与 forge-1.20.1 在全部 1070 个共有键、三语上**逐字相同(差异 0 处)**,故以 forge-1.20.1 作主线原文取用源是安全的

## 1. 结论摘要

| 项 | 数量 |
|---|---|
| 漂移键总数(去重) | **109** |
| zh_cn 取值不同 | 101 |
| en_us 取值不同 | 108(其中 8 个是「zh/ja 相同、仅 en 不同」) |
| ja_jp 取值不同 | 93(无 ja 独有项) |
| **A 类**(陈旧数值/机制/措辞,已对齐主线) | **94** 键 / **261** 行(zh 88、en 93、ja 80) |
| **B 类**(键语义分叉,fabric 代码缺口,未覆盖) | **15** 键 |
| **C 类**(平台措辞差异,保留) | **7** 键 |
| 待裁决(仅 fabric 存在的键) | 17 键 |

对齐方向判定:把 9 个涉及漂移键的 Java 实现类做 fabric↔forge 逐行 diff,绝大多数差异只是 Curios shim / isClientSide() / 平台 API,即 fabric **代码**已随主线演进、**文案**仍停在移植前快照;另有 5 组确实存在的代码缺口,对应键归 B 类不予覆盖。

## 2. 漂移集计算口径

读 fabric 与 forge 同路径三语 lang,取**键交集(1070)**,逐键比**值**(不比行序、缩进):

- fabric 三语均 1094 键;forge 三语均 1070 键;交集 1070;fabric 独有 24;forge 独有 0。
- 直接 diff 两个文件会整份报差异(1,1096c1,1074),原因:**fabric 是 CRLF、forge 是 LF**。正确口径 = 剥行尾后逐键比值,再按 CRLF 写回 fabric。
- 明细已落盘:.b32-drift.json(101 条 zh 漂移 + 8 条 en 独有)、.b32-changes-applied.json(261 行逐处 旧值→新值)、.b32-seg.txt(逐键段级 diff)。

## 3. A 类修改总表(94 键)

替换方式:只替换值发生变化的行,取主线同键**整行内容**(含 § 染色码、%% 转义、占位符),fabric 行尾保持 CRLF;未触碰键序、缩进与其它行。表中旧值/新值为 zh_cn 摘要;若某键只在 en/ja 变,旧值列标注。

### 3.1 骰子卡牌栏模型(5 键,主线 = 品质决定格数)

主线口径:卡牌栏格数由**品质(品阶)**决定,星级只影响费用上限与赐福强度。代码证据:fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java:46-47,60(return tier != null ? tier.cardSlots() : GameplayConstants.CARD_SLOTS_TOTAL;)、item/dice/DiceTier.java:30-31(cardSlots = 卡牌栏总格数,按品阶固定、攻防各半、与星级无关)、item/ModItems.java:242(卡牌栏格数按品阶登记,第 4 参 cardSlots 与星级无关)。⇒ fabric 代码已是品阶固定,旧文案「星级决定格数」属陈旧。

| 键 | 语言 | 旧值(fabric) | 新值(主线) |
|---|---|---|---|
| `astral_dice.guide.category.dice.desc` | zh/en/ja | 骰子是核心装备：装备后，近战攻击会为自身施加「骰神赐福」；卡牌栏格数由骰子星级决定，筹码栏个数由骰子品阶… | 骰子是核心装备：装备后，近战攻击会为自身施加「骰神赐福」；卡牌栏格数由骰子品质决定，筹码栏个数由骰子品阶… |
| `astral_dice.guide.entry.battle_dice.3` | zh/en/ja | 骰子可经铁砧以星币升星，星级越高赐福加成越强，卡牌栏格数也越多（0★=4、1★=6、2★=8、3★=12… | 骰子可经铁砧以星币升星：星级越高赐福加成越强、卡牌费用上限越高（0★=3、1★=4、2★=5、3★=6）… |
| `astral_dice.guide.entry.diamond_dice.1` | zh/en/ja | 钻石骰子。更高品阶，解锁更多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。 | 钻石骰子。更高品阶，解锁更多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 5，共 10 格），与星级无关。 |
| `astral_dice.guide.entry.golden_dice.1` | zh/en/ja | 黄金骰子。比基础骰子品阶更高，解锁更多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。 | 黄金骰子。比基础骰子品阶更高，解锁更多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 4，共 8 格），与星… |
| `astral_dice.guide.entry.netherite_dice.1` | zh/en/ja | 下界合金骰子。最高阶的骰子，解锁最多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。 | 下界合金骰子。最高阶的骰子，解锁最多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 6，共 12 格），与星… |

### 3.2 法术伤害 → 魔法伤害(术语统一)

主线措辞 = **魔法伤害**。fabric 代码侧同名同类(combat/SpellDamageRegistry.java、damage/ModDamageTypes.java,伤害类型 id 为 astral_dice:card_spell),故这是纯文案术语差异,不含平台语义差别。

| 键 | 语言 | 旧值(fabric) | 新值(主线) |
|---|---|---|---|
| `astral_dice.guide.entry.effect_card_directional_blast.1` | zh/en | 定向爆破：瞄准敌对目标使用，延迟 0.5 秒后引爆目标，对选中目标及其周围 6 格范围内的其他目标同时造… | 定向爆破：瞄准敌对目标使用，延迟 0.5 秒后引爆目标，对选中目标及其周围 6 格范围内的其他目标同时造… |
| `astral_dice.guide.entry.effect_card_living_page.2` | zh/en | 手持此牌（主手）自动开启目标选择器（可选择 32 格内的敌对目标，无倒计时），确认后书页高速飞向目标并必… | 手持此牌（主手）自动开启目标选择器（可选择 32 格内的敌对目标，无倒计时），确认后书页高速飞向目标并必… |
| `astral_dice.guide.entry.effect_card_monster_brick.1` | zh/en | 对怪板砖：瞄准敌对目标使用，向目标头顶上方投掷一块红砖（原版砖物品图标，约与僵尸头部同大），悬停片刻后竖… | 对怪板砖：瞄准敌对目标使用，向目标头顶上方投掷一块红砖（原版砖物品图标，约与僵尸头部同大），悬停片刻后竖… |
| `astral_dice.guide.entry.effect_card_monster_laser.1` | zh/en | 对怪激光：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成红色激光线（无限高）并射中目标，对其造成 4… | 对怪激光：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成红色激光线（无限高）并射中目标，对其造成 4… |
| `astral_dice.guide.entry.effect_card_orbital_strike.1` | zh/en | 轨道炮：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成金色信标光束（无限高）并射中目标，对其造成 8… | 轨道炮：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成金色信标光束（无限高）并射中目标，对其造成 8… |
| `astral_dice.guide.entry.magic_quiver_chip.1` | zh/en/ja | 使用伤害效果牌，并对已有标记的目标造成远程或法术伤害后，返还第一张使用的效果牌并对目标施加 1 层标记。… | 使用伤害效果牌，并对已有标记的目标造成远程或魔法伤害后，返还本次使用的效果牌并对目标施加 1 层标记。每… |
| `astral_dice.guide.entry.ninja_star_chip.1` | zh/en/ja | 效果牌出牌数 +1；对目标造成的远程和魔法伤害增加目标标记层数点。 | 效果牌出牌数 +1；使远程和魔法伤害额外增加目标标记层数的点数伤害。 |
| `astral_dice.guide.entry.piercing_gun_chip.1` | zh/en/ja | 对目标造成的远程或魔法伤害额外增加目标防御力点数的伤害。 | 对目标造成远程或魔法伤害时额外增加目标防御力点数的伤害。 |
| `astral_dice.guide.entry.playstyles.2` | zh/en/ja | 星光：手电筒、八面骰、ATM、银行卡、星币锤等产出与消耗，经商、看板娘立牌联动。 | 星光：手电筒、八面骰、银行卡、星币锤等产出与消耗，经商立牌（星光兑换星币、兑换 +50%%）、看板娘立牌… |
| `astral_dice.guide.entry.playstyles.4` | en | (zh 未变) | (同上) |
| `astral_dice.guide.entry.spell_damage.1` | en | (zh 未变) | (同上) |
| `astral_dice.guide.entry.spell_damage.3` | zh/en/ja | 魔法箭袋对带标记目标造成法伤时，施加 1 层标记并返还第一张使用的效果牌。 | 魔法箭袋对带标记目标造成法伤时，施加 1 层标记并返还本次使用的效果牌。 |
| `astral_dice.guide.entry.spell_damage.name` | en | (zh 未变) | (同上) |
| `tooltip.astral_dice.card.directional_blast` | zh/en | 瞄准敌对目标使用，延迟 §90.5§7 秒后引爆目标，对其及周围 §e6§7 格范围内的其他目标同时造成… | 瞄准敌对目标使用，延迟 §90.5§7 秒后引爆目标，对其及周围 §e6§7 格范围内的其他目标同时造成… |
| `tooltip.astral_dice.card.living_page` | zh/en | 瞄准敌对目标使用，对目标造成 §e%s§7 点法术伤害，并施加一层「标记」。命中已有 §e3§7 层及以… | 瞄准敌对目标使用，对目标造成 §e%s§7 点魔法伤害，并施加一层「标记」。命中已有 §e3§7 层及以… |
| `tooltip.astral_dice.card.monster_brick` | zh/en/ja | 瞄准敌对目标使用，向其投掷一块红砖，命中后对其造成 §e%s§7 点法术伤害 | 瞄准敌对目标使用，对其造成 §e%s§7 点魔法伤害 |
| `tooltip.astral_dice.card.monster_laser` | zh/en/ja | 瞄准敌对目标使用，延迟 §90.5§7 秒后以红色激光命中目标，对其造成 §e%s§7 点法术伤害 | 瞄准敌对目标使用，对其造成 §e%s§7 点魔法伤害 |
| `tooltip.astral_dice.card.orbital_strike` | zh/en/ja | 瞄准敌对目标使用，延迟 §90.5§7 秒后以金色信标光束命中目标，对其造成 §e%s§7 点法术伤害 | 瞄准敌对目标使用，对其造成 §e%s§7 点魔法伤害 |
| `tooltip.astral_dice.chip.magic_quiver` | zh/en/ja | 使用伤害效果牌，并对已有标记的目标造成远程或法术伤害后，返还第一张使用的效果牌并对目标施加 §e1§7 … | 使用伤害效果牌对敌对目标造成伤害时，返还此效果牌并对目标施加 §e1§7 层标记。每 §930 秒§7 … |
| `tooltip.astral_dice.chip.ninja_star` | zh/en/ja | 效果牌出牌数 §e+1§7。对目标造成的远程和魔法伤害增加目标标记层数点 | 效果牌出牌数 §e+1§7。使远程和魔法伤害额外增加目标标记层数的点数伤害 |
| `tooltip.astral_dice.chip.piercing_gun` | zh/en/ja | 对目标造成的远程或魔法伤害额外增加目标防御力点数的伤害 | 对目标造成远程或魔法伤害时额外增加目标防御力点数的伤害 |

### 3.3 卡牌说明括注删减(攻/防牌与 meito)

主线删除「(单次掷取;点数越高越难命中,中位数约在范围的一半处)」。掷点与命中机制两侧实现一致(combat/DiceCombatModifiers.java 掷取逻辑无平台差异),属主线精简后的权威文案。

| 键 | 语言 | 旧值(fabric) | 新值(主线) |
|---|---|---|---|
| `astral_dice.guide.entry.attack_card_epic.1` | zh/en/ja | 攻击牌（特大）：骰战中提供 1~10 点攻击力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 攻击牌（特大）：骰战中提供 1~10 点攻击力。 |
| `astral_dice.guide.entry.attack_card_large.1` | zh/en/ja | 攻击牌（大）：骰战中提供 1~6 点攻击力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 攻击牌（大）：骰战中提供 1~6 点攻击力。 |
| `astral_dice.guide.entry.attack_card_medium.1` | zh/en/ja | 攻击牌（中）：骰战中提供 1~3 点攻击力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 攻击牌（中）：骰战中提供 1~3 点攻击力。 |
| `astral_dice.guide.entry.attack_card_meito.1` | zh/en/ja | 名刀·嘎呜切：骰战中提供 1~20 点攻击（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 名刀·嘎呜切：骰战中提供 1~20 点攻击。 |
| `astral_dice.guide.entry.defense_card_epic.1` | zh/en/ja | 防御牌（特大）：骰战中提供 1~10 点防御力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 防御牌（特大）：骰战中提供 1~10 点防御力。 |
| `astral_dice.guide.entry.defense_card_large.1` | zh/en/ja | 防御牌（大）：骰战中提供 1~6 点防御力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 防御牌（大）：骰战中提供 1~6 点防御力。 |
| `astral_dice.guide.entry.defense_card_medium.1` | zh/en/ja | 防御牌（中）：骰战中提供 1~3 点防御力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | 防御牌（中）：骰战中提供 1~3 点防御力。 |
| `tooltip.astral_dice.card.attack_epic` | zh/en/ja | 攻击力 §e1~10§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 攻击力 §e1~10§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.attack_large` | zh/en/ja | 攻击力 §e1~6§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 攻击力 §e1~6§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.attack_medium` | zh/en/ja | 攻击力 §e1~3§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 攻击力 §e1~3§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.defense_epic` | zh/en/ja | 防御力 §e1~10§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 防御力 §e1~10§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.defense_large` | zh/en/ja | 防御力 §e1~6§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 防御力 §e1~6§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.defense_medium` | zh/en/ja | 防御力 §e1~3§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 防御力 §e1~3§7 | 剩余次数：§e%s§r |

### 3.4 其余数值 / 机制 / 措辞逐字对齐主线

这些键对应实现类经 fabric↔forge 逐行 diff 未发现机制分叉(差异仅 Curios shim / isClientSide / 平台 API),按 2026-11-01 裁决取主线同键逐字全文。

| 键 | 语言 | 旧值(fabric) | 新值(主线) |
|---|---|---|---|
| `astral_dice.guide.entry.bonnie_sign.1` | zh/en/ja | 主动技能-隐匿行动：指定一个敌对目标，向其施加「隐匿调查」；若目标带「标记」，按标记层数 §e×2§7 … | 主动技能-隐匿行动：指定一个敌对目标，向其施加 1 层标记和「隐匿调查」；随后按目标当前标记层数 §e×… |
| `astral_dice.guide.entry.candy_chip.1` | zh/en/ja | 每使用一张效果牌，+1 层治愈并恢复 1 点生命；生命值已满时，本轮出牌数 +1（每轮最多一次）。 | 每使用一张效果牌，+1 层治愈并恢复 1 点生命；生命值已满时，本轮出牌数 +1（当前出牌期间最多触发一… |
| `astral_dice.guide.entry.haiqing_sign.2` | en | (zh 未变) | (同上) |
| `astral_dice.guide.entry.komachi_sign.2` | zh/en/ja | 被动技能-复制者：每使用 3 张效果牌时：复制最后一张使用的效果牌到物品栏；主动技能冷却立即减少 30%… | 被动技能-复制者：使用 3 张效果牌后：复制最后一张使用的效果牌到物品栏；主动技能冷却立即减少 30%%… |
| `astral_dice.guide.entry.lulu_sign.1` | zh/en | 主动技能-治愈粘液：指定友方玩家或自身，向其施加 治愈粘液：目标获得 3 层治愈并瞬间治疗；并以目标为中… | 主动技能-治愈粘液：指定友方玩家或自身，向其施加「治愈粘液」：目标获得 3 层治愈并瞬间治疗；并以目标为… |
| `astral_dice.guide.entry.lulu_sign.2` | zh/en/ja | 被动技能-细胞分裂：受到敌对目标攻击时，获得 1 层治愈，并使主动技能冷却 -10 秒。 | 被动技能-细胞分裂：受到敌对目标攻击时，获得 1 层治愈，并使主动技能冷却 -10 秒（每 15 秒最多… |
| `astral_dice.guide.entry.magic_tome_chip.1` | zh/en/ja | 每使用 3 张效果牌，复制最后一张使用的效果牌并返回到物品栏。 | 使用 3 张效果牌后，复制最后一张使用的效果牌并返回到物品栏。 |
| `astral_dice.guide.entry.megas_sign.3` | zh/en/ja | 配方：金锭 ×2 + 发射器 + 下界合金块 ×2 + 空白立牌 + 黄金星盘 ×3（传奇档）。效果「精… | 效果「精准打击」：每层使该目标受到的轨道轰炸伤害 +1，永久持续直到目标死亡。 |
| `astral_dice.guide.entry.misaki_sign.3` | zh/en/ja | 神秘遗物+联动：佩戴七咒之戒并处于「爆发」状态时，修正第四诅咒——总能造成全额伤害。 | 神秘遗物+联动：佩戴七咒之戒，处于「爆发」状态（由主动技能触发，持续 2:00）时，修正第四诅咒——总能… |
| `astral_dice.guide.entry.moses_sign.3` | en | (zh 未变) | (同上) |
| `astral_dice.guide.entry.piggy_bank_chip.1` | zh/en/ja | 每使用 2 张效果牌，获得 3 星币。 | 使用 2 张效果牌后，获得 3 星币。 |
| `astral_dice.guide.entry.rin_sign.1` | zh/en/ja | 主动技能-活体书页：获得一张「活体书页」；并使自身获得「书页射程」（活体书页使用射程 +50%%，持续 … | 主动技能-活体书页：获得一张「活体书页」；并使自身获得「书页射程」（活体书页使用距离 +50%%，持续 … |
| `astral_dice.guide.entry.teru_sign.2` | zh/en/ja | 被动「狐光」：使用主动技能后，立即获得 3 层「狐光」。当自身或「降神」目标合成、获得或装备攻击牌时，使… | 被动「狐光」：使用主动技能后，立即获得 3 层「狐光」。当自身或「降神」目标获得或装备攻击牌时，使自身获… |
| `astral_dice.guide.entry.vitamin_pill_chip.1` | zh/en/ja | 合成或获得任意卡牌时，治愈 +1。 | 获得任意卡牌时，治愈 +1。 |
| `tooltip.astral_dice.card.berserk` | zh/en/ja | §e左键§7 对准非敌对生物使用（敌对生物不可选），§e右键§7 对自身使用 | §e左键§7 对准友方玩家使用，§e右键§7 对自身使用 |
| `tooltip.astral_dice.card.bite` | zh/en/ja | 装备该战斗牌，并触发「骰神赐福」后，获得 §e1§7 层「觉醒」，使玩家攻击力增加当前觉醒层数（最多 §… | 攻击力增加当前觉醒层数（最多 §e4§7 点），触发「骰神赐福」后，获得 §e1§7 层「觉醒」 | 剩… |
| `tooltip.astral_dice.card.charge` | zh/en/ja | 骰神赐福期间攻击力固定 §e+5§7，赐福结束后返还全力攻击 | 剩余次数：§e%s§r | 攻击力 §e+5§7，赐福结束后返还全力攻击 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.express_delivery` | zh/en/ja | §e左键§7 对准非敌对生物使用（敌对生物不可选），§e右键§7 对自身使用\n使目标获得 §9迅捷 I… | §e左键§7 对准友方玩家使用，§e右键§7 对自身使用\n使目标获得 §9迅捷 II (1:00)§7 |
| `tooltip.astral_dice.card.king_power` | zh/en/ja | 受到 §e8§7 点伤害，攻击力 §e+5§7/层（最多叠 §e3§7 层），持续 §93:00§7（重… | 受到 §e8§7 点伤害，攻击力 §e+5§7/层（最多叠 §e3§7 层），持续 §93:00§7 |
| `tooltip.astral_dice.card.luxury_feast` | zh/en/ja | §e左键§7 对准非敌对生物使用（敌对生物不可选），§e右键§7 对自身使用\n治疗目标及周围 §e6§… | §e左键§7 对准友方玩家使用，§e右键§7 对自身使用\n治疗目标及周围 §e6§7 格内的友方玩家，… |
| `tooltip.astral_dice.card.meito` | zh/en/ja | 攻击力 §e1~20§7（单次掷取，点数越高越难命中） | 剩余次数：§e%s§r | 攻击力 §e1~20§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.card.shadow_strike` | zh/en/ja | 固定攻击力 §e+3§7 + §9黑暗 (0:03)§7 | 剩余次数：§e%s§r | 攻击力 §e+3§7 + §9黑暗 (0:03)§7 | 剩余次数：§e%s§r |
| `tooltip.astral_dice.chip.bank_card_unlimited` | zh/en/ja | 装备时，获得 §e3§7 层星光。每次骰神赐福效果结束后，使自身及友方玩家（无队伍时为全体玩家）获得 §… | 装备时，获得 §e3§7 层星光。每次骰神赐福效果结束后，使自身及友方玩家获得 §e3§7 星币 |
| `tooltip.astral_dice.chip.candy` | zh/en/ja | 每使用一张效果牌，增加 §e1§7 层治愈并恢复 §e1§7 点生命值；当生命值已满时，本轮出牌数 §e… | 每使用一张效果牌，增加 §e1§7 层治愈并恢复 §e1§7 点生命值；当生命值已满时，本轮出牌数 §e… |
| `tooltip.astral_dice.chip.current_core` | zh/en/ja | 使用主动技能时，获得 §e1§7 层充能\n主动技能冷却中按下主动技能键时：按剩余冷却时长占比消耗充能，… | 使用主动技能时，获得 §e1§7 层充能\n主动技能冷却中按下主动技能键时：按剩余冷却时长占比消耗充能使… |
| `tooltip.astral_dice.chip.magic_tome` | zh/en/ja | 每使用 §e3§7 张效果牌，复制最后一张使用的效果牌并返回到物品栏 | 使用 §e3§7 张效果牌后，返还最后一张使用的效果牌 |
| `tooltip.astral_dice.chip.piggy_bank` | zh/en/ja | 每使用 §e2§7 张效果牌后，获得 §e3§7 星币 | 使用 §e2§7 张效果牌后，获得 §e3§7 星币 |
| `tooltip.astral_dice.chip.primordial_core` | zh/en/ja | 每消耗 §e1§7 层充能：获得 §e1§7 层赋能（上限 §e10§7 层）\n每层赋能：攻击力 §e… | 每消耗 §e1§7 层充能：获得 §e1§7 层赋能（上限 §e10§7 层）\n每层赋能：攻击力 §e… |
| `tooltip.astral_dice.chip.vitamin_pill` | zh/en/ja | 合成或获得任意卡牌时，治愈 §e+1§r | 获得任意卡牌时，治愈 §e+1§r |
| `tooltip.astral_dice.fu_card` | zh/en/ja | 对玩家（不限队伍）或自身使用：目标恢复 §e%s§7 点生命值\n使用后出牌数 §e+1 | 对玩家或自身使用：目标恢复 §e%s§7 点生命值\n使用后出牌数 §e+1 |
| `tooltip.astral_dice.huo_card` | zh/en/ja | 只能对敌对目标（含非同队玩家）使用：目标受到 §e%s§7 点伤害 | 只能对敌对目标使用：目标受到 §e%s§7 点伤害 |
| `tooltip.astral_dice.sign.bonnie_active` | zh/en/ja | 指定一个敌对目标，向其施加「隐匿调查」\n若目标带「标记」，按标记层数 §e×2§7 获得星币 | 指定一个敌对目标，向其施加 §e1§7 层标记和「隐匿调查」\n随后按目标当前标记层数 §e×2§7 获… |
| `tooltip.astral_dice.sign.haiqing_passive` | en | (zh 未变) | (同上) |
| `tooltip.astral_dice.sign.investigation_desc` | zh/en/ja | 调查阶段事件（由击杀「隐匿调查」目标触发，共 §e4§7 阶段，均为 §91:00§r）：\n  I：自… | 调查阶段事件（由击杀「隐匿调查」目标触发，共 §e4§7 阶段，均为 §91:00§r）：\n  I：自… |
| `tooltip.astral_dice.sign.komachi_passive` | zh/en/ja | 每使用 §e3§7 张效果牌时：\n  复制最后一张使用的效果牌到物品栏\n  主动技能冷却时间立即减少… | 使用 §e3§7 张效果牌后：\n  复制最后一张使用的效果牌到物品栏\n  主动技能冷却时间立即减少 … |
| `tooltip.astral_dice.sign.lulu_active` | zh/en/ja | 指定友方玩家或自身，向其施加 治愈粘液：目标获得 §e3§7 层治愈 + 瞬间治疗；\n对周围 §e%s… | 指定友方玩家或自身，向其施加「治愈粘液」：目标获得 §e3§7 层治愈 + 瞬间治疗；\n对目标周围 §… |
| `tooltip.astral_dice.sign.lulu_passive` | zh/en/ja | 受到敌对目标攻击时：\n  获得 §e1§7 层治愈\n  主动技能冷却 §9-10 秒§r | 受到敌对目标攻击时：\n  获得 §e1§7 层治愈\n  主动技能冷却 §9-10 秒\n  每 §9… |
| `tooltip.astral_dice.sign.padman_passive` | zh/en/ja | 每 §91:00§7 使基础攻击/防御点数变动（§e-2~4§7 点）\n骰神赐福期间骰点为 §e6§7… | 每 §91:00§7 使基础攻击/防御点数变动（§e-2~4§7 点）\n骰神赐福期间骰点为 §e6§7… |
| `tooltip.astral_dice.sign.rin_active` | zh/ja | 获得一张「活体书页」\n若玩家物品栏中没有「活体书页」，则额外获得一张\n活体书页使用射程 §e+50%… | 获得一张「活体书页」\n若玩家物品栏中没有「活体书页」，则额外获得一张\n活体书页使用距离 §e+50%… |
| `tooltip.astral_dice.sign.teru_passive` | zh/en/ja | 使用主动技能后，立即获得 §e3§7 层「狐光」\n  当自身或「降神」目标合成、获得或装备攻击牌时，使… | 使用主动技能后，立即获得 §e3§7 层「狐光」\n  当自身或「降神」目标获得或装备攻击牌时，使自身获… |

### 3.5 逐键代码取证组(15 键)

| 键 | 语言 | 旧值(fabric) | 新值(主线) |
|---|---|---|---|
| `astral_dice.guide.entry.atm_chip.1` | zh/en/ja | 装备时获得 1 层星光；使用星光兑换星币时，兑换量 +40%%（向下取整；按「下限为 1」策略，加成至少… | 装备后，转账时使双方各获得 1 层星光与 2 星币。 |
| `astral_dice.guide.entry.electric_glove_chip.1` | zh/en/ja | 使用效果牌时，充能 +1。充能不少于 4 层时，使用伤害类效果牌会消耗 4 层充能，使本次远程和魔法伤害… | 使用伤害类效果牌攻击目标时，使目标 3 格内所有目标（含主目标）受到 2 点魔法伤害，延迟 1 秒生效；… |
| `astral_dice.guide.entry.mimi_sign.2` | zh/en/ja | 被动技能-过期回收：合成或返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累… | 被动技能-过期回收：返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 1… |
| `astral_dice.guide.entry.parunan_sign.1` | zh/en/ja | 主动技能-套现：按 2:1 将星光兑换为星币，并随机获得以下任一效果：饱和 (0:30)、幸运 (5:0… | 经商立牌。主动「网络购物」：远程打开卡牌商店界面（购买卡牌、星光兑换、向其他在线玩家转账星币）；主动本身… |
| `astral_dice.guide.entry.parunan_sign.2` | zh/en/ja | 被动技能-传奇商人：每隔一段时间 +1 星光（有上限）；触发骰神赐福时，获得首次骰点 ×2 的星光。 | 被动「传奇商人」：每次进入商店时获得 1 星币；自身与团队成员的商店中可购买战斗牌数量 +1；进入商店后… |
| `astral_dice.guide.entry.revenge_halberd_chip.1` | zh/en/ja | 获得以下任一效果时，攻击力 +6：虚弱、缓慢、挖掘疲劳、失明、黑暗、青之诅咒。 | 获得以下任一效果时，攻击力 +6：虚弱、缓慢、挖掘疲劳、失明、黑暗、蓄风、盘丝、渗浆、寄生、青之诅咒。 |
| `astral_dice.guide.entry.revenge_halberd_chip.2` | zh/en/ja | 获得以下任一效果时，防御力 +6：饥饿、反胃、中毒、凋零、标记。 | 获得以下任一效果时，防御力 +6：饥饿、反胃、中毒、凋零、袭击之兆、试炼之兆、标记。 |
| `astral_dice.guide.entry.weird_dice.1` | zh/en/ja | 诡异骰子。与钻石骰子同阶，可由钻石骰子升级；立牌主动技能冷却时间减半，但你的战斗骰更容易骰出低点数（1-… | 诡异骰子。与钻石骰子同阶，可由钻石骰子升级；你的战斗骰更容易骰出低点数（1-3）。 |
| `tooltip.astral_dice.chip.atm` | zh/en/ja | 装备时获得 §e1§7 层星光。使用星光兑换星币时，兑换量增加 §e40%%§7（向下取整，至少 §e1… | 转账后，使双方各获得 §e1§7 层星光与 §e2§7 星币 |
| `tooltip.astral_dice.chip.electric_glove` | zh/en/ja | 使用效果牌时：充能 §e+1§r\n充能不少于 §e4§7 层时使用伤害类效果牌：消耗 §e4§7 层充… | 使用伤害效果牌攻击目标时：目标 §e3§7 格内所有目标受到 §e2§7 点魔法伤害\n每使 §e1§7… |
| `tooltip.astral_dice.chip.railgun` | zh/en/ja | 充能不少于 §e6§7 层时：攻击力 §e+5§r\n近战攻击敌对目标时：延迟 §91 秒§7对目标 §… | 充能不少于 §e6§7 层时：攻击力 §e+5§r\n近战攻击敌对目标时：延迟 §91 秒§7对目标 §… |
| `tooltip.astral_dice.chip.satellite` | zh/en/ja | 物品栏中「轨道炮」少于 §e6§7 张时，每 §91:00§7 获得一张「轨道炮」\n使用「轨道炮」后，… | 物品栏中「轨道炮」少于 §e6§7 张时，每 §91:00§7 获得一张「轨道炮」\n使用「轨道炮」后，… |
| `tooltip.astral_dice.sign.mimi_passive` | zh/en/ja | 合成或返还卡牌时，每获得一张战斗牌获得 §e1§7 星币\n装备时筹码栏位 §e+1§r\n每主动返还累… | 返还卡牌时，每获得一张战斗牌获得 §e1§7 星币\n装备时筹码栏位 §e+1§r\n每主动返还累计 §… |
| `tooltip.astral_dice.sign.parunan_active` | zh/en/ja | 按 §e2:1§7 比例将持有的星光兑换为星币。\n随机获得以下任一效果：\n  §9饱和 (0:30)… | 远程打开卡牌商店 |
| `tooltip.astral_dice.sign.parunan_passive` | zh/en/ja | 每 §e%s§7 增加 §e1§7 层星光（上限 §e%s§7 层）。\n触发骰神赐福时，获得首次骰点 … | 进入商店时获得 §e1§7 星币\n自身与团队成员的商店：可购买战斗牌数量 §e+1§7\n商店内每张卡… |

逐键代码证据:

- `astral_dice.guide.entry.atm_chip.1` — fabric CardShopMenu.java:660-663 与 :123-124(ATM_TRANSFER_STARLIGHT=1, ATM_TRANSFER_COINS=2)已实现主线转账结算;旧「兑换 +40%%」在两侧 ResourceConversion.java 均已不存在
- `astral_dice.guide.entry.electric_glove_chip.1` — fabric ElectricGloveChipItem.java 与主线逻辑等价(AOE_RADIUS=3 / SHOCK_DAMAGE=2.0F / CHARGE_PER_HIT=1 / COOLDOWN_TICKS=1200,import 差异仅 Curios shim)
- `astral_dice.guide.entry.mimi_sign.2` — 两侧 MimiSignItem.RETURNED_CARD_THRESHOLD = 100(fabric :45 / forge :47)⇒ 旧「25 张」陈旧
- `astral_dice.guide.entry.parunan_sign.1` — fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/shop/CardShopMenu.java:121,582(PARUNAN_EXCHANGE_BONUS_RATE=0.50F)与 :657,678 forceDiscountOnOwnShelf() 已含主线商店经济语义 ⇒ 非语义分叉
- `astral_dice.guide.entry.parunan_sign.2` — 同上(CardShopMenu.java:121/582/657/678);fabric ParunanSignItem.java:38,45 的 javadoc 与主线五条被动一致
- `astral_dice.guide.entry.revenge_halberd_chip.1` — 两侧 RevengeHalberdChipItem 判定集一致;主线文案追加 1.21 专属效果,见 O2
- `astral_dice.guide.entry.revenge_halberd_chip.2` — 同上(O2)
- `astral_dice.guide.entry.weird_dice.1` — fabric WeirdDiceHandler.signCooldownTicks 的 javadoc:「不提供诡异骰子的 -50%%(用户需求 2026-10-21:该能力已删除)」⇒ 旧句为假
- `tooltip.astral_dice.chip.atm` — 同上(CardShopMenu.java:660-663);代码残留见观察项 O4
- `tooltip.astral_dice.chip.electric_glove` — 同上
- `tooltip.astral_dice.chip.railgun` — 两侧 RailgunChipItem 一致(5 + 本次攻击伤害 × 50%,下限 5 点;astral_dice:true_damage);主线改写为「无视防御力」,见 O3
- `tooltip.astral_dice.chip.satellite` — 两侧 SatelliteChipItem 均含「效果牌的目标选择距离 +50%%(2026-10-03 裁决,取代原轨道炮版本)」⇒ 旧文案陈旧
- `tooltip.astral_dice.sign.mimi_passive` — 同上
- `tooltip.astral_dice.sign.parunan_active` — fabric ParunanSignItem.java 已按 1.21.1 新版语义整体重写(与 forge 仅 22 行 diff,全为 shim/javadoc)⇒ 旧文案陈旧
- `tooltip.astral_dice.sign.parunan_passive` — 同上;主线被动(进入商店 +1 星币 / 战斗牌 +1 / 10%% 降价 / 兑换 +50%%)在 fabric 代码中均已实现(CardShopMenu.java:121,582,678)

## 4. B 类结论(15 键:键语义分叉,fabric 代码未跟上,不覆盖)

流程:同键在 fabric 侧定位 Java 引用 → 在 forge 侧定位同键引用 → 比对两侧实现 → 若主线文案描述的机制在 fabric 侧**不存在**,覆盖就会让文案承诺不存在的功能,故保留 fabric 原文并登记为**待移植代码**项(本批禁止改 .java)。

| 键 | fabric 侧证据 | 主线对应文案(zh 摘要) | 结论 |
|---|---|---|---|
| `astral_dice.guide.entry.fanny_sign.1` | fabric 主动技能仍是旧「随机事件」设计:fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FannySignItem.java:169-170 仍 Component.translatable(msg.astral_dice.fanny_event, Component.translatable(msg.astral_dice.fanny_event. + roll));主线(forge-1.20.1 与 neoforge-1.21.1 的 src/main)grep fanny_event = 0 命中,已改为「2 选 1 事件选择」UI | 主动技能-事件选择：从当前事件池随机抽出 2 个全体事件，选择其中 1 个并触发；必须选中并点击确认才会触发。 | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.fanny_sign.2` | 同上(FannySignItem.java:169-170) | （接上）15 秒内未选择、死亡或退出游戏时强制触发第一个；确认后立即关闭界面并进入事件流程；技能音效在按下技能键时播… | 保留 fabric 原文,待代码移植后再改文案 |
| `tooltip.astral_dice.sign.fanny_active` | 同上(FannySignItem.java:169-170) | 在2个全体事件中选择1个全体事件并触发 | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.airbag_chip.1` | 主线新增「受到伤害时充能 +1(15 秒冷却)」:forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AirbagChipItem.java 有 HIT_CHARGE_GAIN = 1、HIT_CHARGE_COOLDOWN_TICKS = 20 * 15、@SubscribeEvent public static void onIncomingDamage(LivingHurtEvent);fabric 同名文件 89 行(forge 134 行)无该逻辑 | 受到致命伤害时，消耗 6 层充能，使本次伤害无效（冷却 1:00）。该保命效果优先于磨刀石、不死图腾与末影骰子；无视… | 保留 fabric 原文,待代码移植后再改文案 |
| `tooltip.astral_dice.chip.airbag` | 同上 | 受到致命伤害时：消耗 §e6§7 层充能，使本次伤害无效 优先于磨刀石、不死图腾与末影骰子 冷却 §91:00§7 … | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.member_recommendation_chip.1` | 主线新增「手牌 < 5 张才生效」与「商店价格 -1」:forge 同名类有 private static final int HAND_CARD_LIMIT = 5; 与 countHandCards(Player) 闸门;fabric MemberRecommendationChipItem.java 共 42 行,无闸门、无 shopPriceReduction | 触发骰神赐福时，若物品栏卡牌数少于 5 张，获得一张随机卡牌。装备期间，自身商店内所有卡牌价格 -1 星币（可与经商… | 保留 fabric 原文,待代码移植后再改文案 |
| `tooltip.astral_dice.chip.member_recommendation` | 同上 | 触发骰神赐福时，获得一张随机卡牌 仅在物品栏卡牌数少于 §e5§7 张时触发 装备期间，自身商店内所有卡牌价格 §e… | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.bank_card_high_chip.1` | 主线「每次星光兑换成功后补充星光」对应 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/StarLightManager.java:143 的 public static int replenishOnExchange(Player player),由 resource/ResourceConversion.java:44 调用;fabric StarLightManager.java 无此方法(成员止于 spend(),另有主线没有的 grantStarlightFloorOnEquip:126) | 装备时获得 7 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —… | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.bank_card_low_chip.1` | 同上 | 装备时获得 4 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —… | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.bank_card_unlimited_chip.1` | 同上(fabric BankCardUnlimitedChipItem.java:48 装备给 3 星光与主线一致,但缺兑换后补充) | 装备时获得 3 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —… | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.star_coin_hammer_chip.1` | 同上(缺 replenishOnExchange 链路) | 装备时获得 5 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —… | 保留 fabric 原文,待代码移植后再改文案 |
| `tooltip.astral_dice.chip.bank_card_high` | 同上;仅 en_us 漂移(zh/ja 已同主线),en 旧值描述 fabric 的 floor 模型(7 base Starlight, increases base value) | (zh 与主线相同,仅 en 不同) | 保留 fabric 原文,待代码移植后再改文案 |
| `tooltip.astral_dice.chip.bank_card_low` | 同上;仅 en_us 漂移 | (zh 与主线相同,仅 en 不同) | 保留 fabric 原文,待代码移植后再改文案 |
| `astral_dice.guide.entry.eagle_scope_chip.1` | 主线把鹰眼瞄具从「攻击力(ap)通道」改到「额外加伤通道」:forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java:476 用 ctx.eagleScopeMarkLevel、:483-484 注释、:494 return ctx.eagleScopeMarkLevel * EagleScopeChipItem.DAMAGE_PER_MARK;;fabric 同名文件 :415 注释仍为「按标记层数 ×2 加攻击力」,:429 if (hasCurio(ctx.attacker, ModItems.EAGLE_SCOPE_CHIP.get())) 在攻击力修饰器内 | 攻击时对目标施加 1 层标记；对带标记目标额外造成其标记层数 ×2 的攻击伤害。 | 保留 fabric 原文,待代码移植后再改文案 |
| `tooltip.astral_dice.chip.eagle_scope` | 同上 | 攻击时对目标施加 §e1§7 层标记，对带标记目标额外造成其标记层数 §e2§7 倍的攻击伤害 | 保留 fabric 原文,待代码移植后再改文案 |

### 4.1 关于 tooltip.astral_dice.sign.parunan_passive(用户指定的 B 类重点核项)

**结论:不是语义分叉,是 A 类陈旧文案 —— 已按主线全文替换。** 依据:

1. fabric 侧该键语义由 fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java(第 38/45 行 javadoc 列五条被动)与商店逻辑共同产生;
2. fabric shop/CardShopMenu.java:121 定义 PARUNAN_EXCHANGE_BONUS_RATE = 0.50F, :582 为 bonus = Math.max(1, (int) (gained * PARUNAN_EXCHANGE_BONUS_RATE));, :657 调用 :678 的 forceDiscountOnOwnShelf() —— 与 forge 同名、同值、同实现;
3. fabric ParunanSignItem.java 与 forge 仅 22 行 diff,全为 Curios shim 与 javadoc(其 javadoc 明写「1.20.1 Fabric 移植(按 1.21.1 新版语义整体重写本文件),删除了旧版主动…」)。

即主线「在线购物开商店 + 商店经济被动(进入商店 +1 星币 / 战斗牌 +1 / 10%% 降价 / 兑换 +50%%)」在 fabric 代码中已完整存在,旧文案只是没跟着换 ⇒ 已改 `tooltip.astral_dice.sign.parunan_active` / `tooltip.astral_dice.sign.parunan_passive` / `astral_dice.guide.entry.parunan_sign.1` / `astral_dice.guide.entry.parunan_sign.2`。

## 5. C 类:保留的平台差异(7 键)

这 7 键**仅存在于 fabric**,是饰品后端(Trinkets / Accessories)自身命名空间的槽位名,由第三方模组按 id 读取显示;主线(Forge / NeoForge 走 Curios)不存在对应后端,故无「主线原文」可对齐。

| 键 | 保留理由 |
|---|---|
| `accessories.slot.chip` | Accessories(Fabric 饰品后端)槽位显示名,由 Accessories 模组按该 id 读取;主线走 Curios,无此后端。证据 en_us.json:211 |
| `accessories.slot.dice` | 同上,en_us.json:212 |
| `accessories.slot.stand` | 同上,en_us.json:213 |
| `accessories.slot_group.astral_dice` | Accessories 槽位分组名,en_us.json:214;实体依据 compat/accessories/AccessoriesCompat.java:102,190 |
| `trinkets.slot.astral_dice.chip` | Trinkets(Fabric 饰品后端)槽位显示名,由 Trinkets 模组按该 id 读取,en_us.json:218 |
| `trinkets.slot.astral_dice.dice` | 同上,en_us.json:219 |
| `trinkets.slot.astral_dice.stand` | 同上,en_us.json:220 |

## 6. 待裁决项(键集差异;未增删任何键,三语键数仍 1094)

| 键 | 说明 |
|---|---|
| `msg.astral_dice.fanny_event` | fabric 旧随机事件系统专用键;主线无此键(主线改为事件选择) |
| `msg.astral_dice.fanny_event.1` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.2` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.3` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.4` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.5` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.6` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.7` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.8` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.9` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.10` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.fanny_event.11` | 同上,fabric 旧 12 选 1 事件文本 |
| `msg.astral_dice.parunan_active_no_starlight` | fabric 旧派鲁南主动技能(星光不足提示)残留;主线无此键 |
| `astral_dice.guide.entry.teru_sign.3` | fabric 独有手册配方行,主线同条目无此行 |
| `astral_dice.guide.entry.nardis_sign.3` | 同上 |
| `astral_dice.guide.entry.sherry_sign.3` | 同上 |
| `astral_dice.guide.entry.hanna_sign.3` | 同上 |

这 17 键随 fabric 旧实现(随机事件版菲妮主动、旧派鲁南提示、四条手册配方行)存在;要清理需先移植对应代码再删键,属独立批次。

## 7. 观察项 / 无法判定项

1. **O1 诡异骰子**:旧文案「立牌主动技能冷却时间减半,但…」被删是**正确**的 —— fabric event/WeirdDiceHandler.java 的 javadoc 明写「不提供诡异骰子的 -50%%(用户需求 2026-10-21:该能力已删除)」。
2. **O2 复仇戟(主线侧缺陷,建议裁处)**:主线文案列出「蓄风 / 盘丝 / 渗浆 / 寄生」与「袭击之兆 / 试炼之兆」,这些是 1.21 才有的效果;forge-1.20.1 与 fabric 的 item/chip/RevengeHalberdChipItem.java 都只判定 WEAKNESS / MOVEMENT_SLOWDOWN / DIG_SLOWDOWN / BLINDNESS / DARKNESS 与 HUNGER / CONFUSION / POISON / WITHER。已按裁决对齐 forge-1.20.1 原文(两线逐字一致),但该文案对 1.20.1 代码是超集。
3. **O3 轨道炮(主线删掉真实细节)**:两侧 item/chip/RailgunChipItem.java 一致(5 + 本次攻击伤害 × 50%,下限 5 点;astral_dice:true_damage);主线文案改为「伤害无视目标防御力」并删掉「下限 5 点」。已按裁决对齐。
4. **O4 ATM 筹码代码残留(非文案,本批不动)**:fabric item/chip/AtmChipItem.java:40 仍调用 StarLightManager.grantStarlightOnEquip(player, StarLightManager.GRANT_BIT_ATM, 1)、:47 调用 revokeStarlightOnUnequip(...),即装备额外 +1 星光;主线这两项能力已被裁决移除。文案已按主线(转账版)对齐,代码残留需另批处理。
5. **O5 仅 en 漂移的 B 键**:tooltip.astral_dice.chip.bank_card_high 与 tooltip.astral_dice.chip.bank_card_low 的 zh/ja 与主线相同、只有 en 描述 fabric 的 floor 模型,故仍归 B 未改(zh 相同不等于语义一致)。
6. 无法判定项:**无**。109 个漂移键全部归类完毕。

## 8. 验收(守门原始末行)

```
$ python tools/check_lang_sync.py
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致

$ python tools/verify_fabric_assets.py
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
=== PASS: 8 项闭环全部通过 ===

$ python tools/audit_patchouli_keys.py   # 收尾复跑(两次结果一致)
合计：引用 2057 处，悬空 0 处
✅ 无悬空引用。
```

同一工作树在本批早期(改文案后、收尾前)该脚本输出「合计：引用 2000 处」,其中 fabric 为 473 处;收尾复跑 fabric 变为 530 处、合计 2057 处,连跑两次一致 —— 期间 fabric 手册侧条目数被并行批次改动过(见下方「并行改动声明」),故「合计引用数」不作为本批判据,本批判据是**悬空 0 处**及逐项目「全部命中三语 lang ✓」。

收尾复跑完整原始输出:

```
=== neoforge-1.21.1 ===
   手册引用 530 处，全部命中三语 lang ✓
=== forge-1.20.1 ===
   手册引用 530 处，全部命中三语 lang ✓
=== neoforge-26.1.2 ===
   手册引用 467 处，全部命中三语 lang ✓
=== fabric-1.20.1 ===
   手册引用 530 处，全部命中三语 lang ✓
--------------------------------------------------------------------
合计：引用 2057 处，悬空 0 处
✅ 无悬空引用。
```

### 并行改动声明(非本批产物,未触碰)

本批只改 fabric 三份 lang 与 porting/ 下的报告/脚本。执行期间 `git status` 显示工作树上另有并行改动:`fabric-1.20.1/build.gradle` 被修改、`fabric-1.20.1/src/generated/resources/**` 下大量 recipe/advancement 文件被删除、fabric 手册条目数增加(473→530 引用)。这些均**不是本批所为**,本批未 add/commit/stash/checkout,也未修改任何 .java / 构建脚本 / 手册 json。

字节自检(.b32_check.py,逐字节):

```
zh_cn bytes=145060 CRLF=1096 LF=1096 bareLF=0 BOM=False keys=1094 endsCRLF=True
en_us bytes=150307 CRLF=1096 LF=1096 bareLF=0 BOM=False keys=1094 endsCRLF=True
ja_jp bytes=185178 CRLF=1096 LF=1096 bareLF=0 BOM=False keys=1094 endsCRLF=True
```

残留不变量(.b32_post.py):对 1070 个共有键逐键比值,三语 nonB_diff 均为 **0**;仍不同的恰为 15 个 B 键(zh 13、en 15、ja 13)。

