package com.merlinkitsune.astral_dice.damage;

import com.merlinkitsune.astral_dice.AstralDiceMod;
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

    public static DamageSource diceDamage(Level level, Entity source) {
        var holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DICE_DAMAGE);
        return new DamageSource(holder, source);
    }
}
