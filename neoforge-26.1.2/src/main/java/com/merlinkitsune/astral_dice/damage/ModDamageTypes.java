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
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "dice_damage")
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
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "true_damage")
    );

    public static DamageSource diceDamage(Level level, Entity source) {
        return new DamageSource(trueHolder(level, DICE_DAMAGE), source);
    }

    /**
     * 真伤伤害源,**无来源实体**(与电磁炮雷击的原版闪电一致:不构成任何玩家的直接/间接攻击,
     * 因此不会被本模组或其它模组当成"玩家攻击"重走命中判定,且不产生击杀归属)。
     */
    public static DamageSource trueDamage(Level level) {
        return new DamageSource(trueHolder(level, TRUE_DAMAGE));
    }

    /**
     * 真伤伤害源,**直接伤害实体为空、击杀归属 {@code causing}**——与旧实现
     * {@code damageSources().explosion(null, player)} 同一形状(无直接伤害实体 → 不算玩家的直接攻击,
     * 同时保留击杀归属:掉落/经验/联动)。
     */
    public static DamageSource trueDamage(Level level, Entity causing) {
        return new DamageSource(trueHolder(level, TRUE_DAMAGE), null, causing);
    }

    private static Holder<DamageType> trueHolder(Level level, ResourceKey<DamageType> key) {
        return level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
    }
}
