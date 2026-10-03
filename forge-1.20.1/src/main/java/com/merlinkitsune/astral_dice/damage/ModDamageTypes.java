package com.merlinkitsune.astral_dice.damage;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class ModDamageTypes {
    public static final ResourceKey<DamageType> DICE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "dice_damage")
    );

    /**
     * 真伤伤害类型(见 {@code data/astral_dice/damage_type/true_damage.json})。
     * <p>它被登记进 {@code data/minecraft/tags/damage_type/bypasses_armor.json},
     * 因此结算时**完全跳过护甲值与盔甲韧性**(原版 {@code LivingEntity#getDamageAfterArmorAbsorb} 的入口判定),
     * 但仍会被保护附魔与抗性提升减免——这两项分别由 {@code bypasses_enchantments}/{@code bypasses_resistance}
     * 标签控制,不在"无视防御力(护甲值/盔甲韧性)"的口径内。
     */
    public static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "true_damage")
    );

    /**
     * 「活体书页」命中伤害的类型(见 {@code data/astral_dice/damage_type/card_spell.json})。
     * <p><b>为什么需要独立类型</b>:该伤害必须**登记为法伤**(命中 {@code SpellDamageRegistry} 的
     * 作用域白名单),从而由 {@code event/DamageEffectCardHandler} 原样跑完整法伤修饰器链
     * (忍术飞镖 / 贯穿之铳 / 紫晶骰子 / 标记喷罐 / 魔法箭袋 + 忍者立牌 / 书签效果牌加成);
     * 复用 {@link #TRUE_DAMAGE} 不会被判定为法伤,复用 {@link #DICE_DAMAGE} 则会把一次卡牌命中
     * 变成一次骰战发起。
     * <p>它同样被登记进 {@code data/minecraft/tags/damage_type/bypasses_armor.json},
     * 因此基础伤害**完全跳过护甲值与盔甲韧性**(与旧实现「效果牌伤害走真伤」的口径等价),
     * 但仍会被保护附魔与抗性提升减免。
     */
    public static final ResourceKey<DamageType> CARD_SPELL = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "card_spell")
    );

    /**
     * 「技能类伤害」类型(见 {@code data/astral_dice/damage_type/skill_damage.json})。
     *
     * <p><b>为什么需要独立类型</b>(2026-09-24 用户裁决「为技能类伤害创建单独的伤害标签,
     * 避免与法伤混用」):立牌 / 技能打的**固定点数伤害**不应复用 {@link #CARD_SPELL} ——
     * 该类型是 {@code SpellDamageRegistry} 法伤白名单的第 4 条 matcher,复用它会让固定点数
     * 被忍术飞镖 / 贯穿之铳 / 紫晶骰子 / 标记喷罐 / 魔法箭袋 / 效果牌加成层层放大,并可能
     * 触发电击手套的范围波及。
     *
     * <p>本类型同样登记于 {@code data/minecraft/tags/damage_type/bypasses_armor.json}
     * (⇒ 无视护甲值与盔甲韧性,与卡片/技能固定点数口径一致),但**不在**法伤白名单内
     * ⇒ 只结算自身点数。⚠️ **禁止**把它加进 {@code SpellDamageRegistry} 的
     * {@code MAGIC_DAMAGE_TYPES} 或任何 matcher。
     */
    public static final ResourceKey<DamageType> SKILL_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "skill_damage")
    );

    /**
     * **额外加伤**(2026-09-25 新增):手电筒星光 / 星币锤星币 / 美工刀治愈点三项加伤的**独立结算通道**
     * —— 它们**不进「攻击力」**(否则会污染教主立牌降神「狐光攻击基数」的攻击力快照),
     * 而是命中落地后按本类型单独造成一段伤害。登记于 {@code minecraft:bypasses_armor} 与
     * {@code minecraft:bypasses_cooldown} ⇒ 无视护甲与受击无敌帧(与 {@link #SKILL_DAMAGE} 同口径)。
     */
    public static final ResourceKey<DamageType> EXTRA_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "extra_damage")
    );

    /**
     * **卡牌代价**（2026-09-30 新增）:效果牌「王之力」使用时的**自伤代价**（受到 8 点伤害）。
     *
     * <p><b>为什么不能复用 {@link #DICE_DAMAGE}</b>:骰子伤害类型不在
     * {@code minecraft:tags/damage_type/bypasses_cooldown} 里 ⇒ 受原版受击无敌帧约束
     * （原版 {@code LivingEntity#hurt} 在 {@code invulnerableTime > 10} 且 {@code amount <= lastHurt} 时
     * <b>直接 return false</b>）⇒ 战斗中（刚被其它伤害打过）使用王之力**完全不掉血**，代价形同虚设；
     * 创造模式下 {@code abilities.invulnerable} 亦会整体免疫。本类型登记于 {@code bypasses_cooldown}
     * ⇒ 无敌帧内照常结算，代价必定生效。
     *
     * <p>⚠️ <b>2026-10-02 口径变更（用户裁决）</b>：本类型**改为登记** {@code bypasses_armor} +
     * {@code bypasses_enchantments} + {@code bypasses_resistance} —— 旧口径（只登记 {@code bypasses_cooldown}、
     * 仍受护甲/抗性/保护减免）实测「代价极易被减伤抵消、形同虚设」，故改为**必须真扣 8 点**；
     * 同时 {@code event/ChipDamageHandler} 把本类型识别为**不可削减**，本模组的固定点数减伤
     * （磨刀石 -2、怪力侦探立牌 -N）对它一律跳过 —— **保命**机制照常（安全气囊、不死图腾、
     * 末影骰子、磨刀石「不可被一次击倒」）。
     * <p><b>仍不</b>登记 {@code bypasses_invulnerability}：创造模式 / 重生无敌期仍然免疫。
     */
    public static final ResourceKey<DamageType> CARD_COST = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "card_cost")
    );

    /**
     * **不可削减真伤**（2026-10-02 新增）：「伤害增加」类机制（如狂暴「受到任意伤害 +1/层」）
     * 在伤害**落地后**另行结算的那一段**独立真伤**。
     *
     * <p>登记于 {@code bypasses_armor} + {@code bypasses_enchantments} + {@code bypasses_resistance}
     * + {@code bypasses_cooldown} ⇒ 护甲 / 盔甲韧性 / 保护附魔 / 抗性提升**全部跳过**，且不受受击无敌帧约束。
     * 另外 {@code event/ChipDamageHandler} 把它（与 {@link #CARD_COST}）识别为**不可削减** ⇒
     * 本模组的固定点数减伤（磨刀石 -2、怪力侦探立牌 -N）对它一律跳过；**保命**机制照常
     * （安全气囊、不死图腾、末影骰子、磨刀石「不可被一次击倒」）。
     *
     * <p><b>不</b>登记 {@code bypasses_invulnerability}：创造模式 / 重生无敌期仍然免疫，与 {@link #CARD_COST} 同口径。
     */
    public static final ResourceKey<DamageType> UNREDUCIBLE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(AstralDiceMod.MODID, "unreducible_damage")
    );

    public static DamageSource diceDamage(Level level, Entity source) {
        return new DamageSource(holder(level, DICE_DAMAGE), source);
    }

    /**
     * 真伤伤害源,**无来源实体**(与电磁炮雷击的原版闪电一致:不构成任何玩家的直接/间接攻击,
     * 因此不会被本模组或其它模组当成"玩家攻击"重走命中判定,且不产生击杀归属)。
     */
    public static DamageSource trueDamage(Level level) {
        return new DamageSource(holder(level, TRUE_DAMAGE));
    }

    /**
     * 真伤伤害源,**直接伤害实体为空、击杀归属 {@code causing}**——与旧实现
     * {@code damageSources().explosion(null, player)} 同一形状(无直接伤害实体 → 不算玩家的直接攻击,
     * 同时保留击杀归属:掉落/经验/联动)。
     */
    public static DamageSource trueDamage(Level level, Entity causing) {
        return new DamageSource(holder(level, TRUE_DAMAGE), null, causing);
    }

    /**
     * 「活体书页」命中伤害源:**直接伤害实体为空、击杀归属 {@code causing}**
     * (与 {@link #trueDamage(Level, Entity)} 同形状 ⇒ 不会被当成玩家的直接攻击而重走骰战;
     * 但伤害类型本身是法伤,故完整法伤修饰器链照常生效)。
     */
    public static DamageSource cardSpell(Level level, Entity causing) {
        return new DamageSource(holder(level, CARD_SPELL), null, causing);
    }

    /**
     * 技能类伤害源:**直接伤害实体为空、击杀归属 {@code causing}** ——
     * 与 {@link #trueDamage(Level, Entity)} 同形状(不会被当成玩家的直接攻击而重走命中判定,
     * 同时保留击杀归属),但伤害类型是独立的 {@link #SKILL_DAMAGE} ⇒ **不进入法伤链**。
     */
    public static DamageSource skillDamage(Level level, Entity causing) {
        return new DamageSource(holder(level, SKILL_DAMAGE), null, causing);
    }

    /**
     * 额外加伤伤害源:**直接伤害实体为空、击杀归属 {@code causing}** ——
     * 与 {@link #trueDamage(Level, Entity)} 同形状(不会被当成玩家的直接攻击而重走骰战),
     * 但伤害类型是独立的 {@link #EXTRA_DAMAGE} ⇒ 单独结算一段「额外加伤」。
     */
    public static DamageSource extraDamage(Level level, Entity causing) {
        return new DamageSource(holder(level, EXTRA_DAMAGE), null, causing);
    }


    /**
     * 卡牌代价伤害源:**直接伤害实体 = 使用者本人**（与 {@link #diceDamage} 同形，
     * 沿用「玩家自伤」的既有链路判定）。骰战对 {@code target == attacker} 已在
     * {@code DiceCombatEvents#onLivingDamagePre} 中先行 {@code return} ⇒ 不会重走骰战；
     * 赐福/降神计时器与隐匿解除两处挂点都有 {@code attacker != target} / {@code !(target instanceof Player)}
     * 前置 ⇒ 自伤不误触发。
     */
    public static DamageSource cardCost(Level level, Entity source) {
        return new DamageSource(holder(level, CARD_COST), source);
    }

    /**
     * 不可削减真伤伤害源：**直接伤害实体为空、击杀归属 {@code causing}** —— 与
     * {@link #trueDamage(Level, Entity)} 同形状（不算玩家的直接攻击、不重走骰战，但保留击杀归属）。
     */
    public static DamageSource unreducibleDamage(Level level, Entity causing) {
        return new DamageSource(holder(level, UNREDUCIBLE_DAMAGE), null, causing);
    }

    private static Holder<DamageType> holder(Level level, ResourceKey<DamageType> key) {
        return level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
    }
}
