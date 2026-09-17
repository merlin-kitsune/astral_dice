package com.merlinkitsune.astral_dice.loot;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组在 1.20.1(Forge)侧自带的全局战利品修饰符序列化器。
 *
 * <p>Forge 47.4.10 的 {@code net.minecraftforge.common.loot} 未提供任何内置序列化器
 * (既没有 {@code forge:add_table},也没有 {@code GlobalLootModifierSerializers} 类),
 * 而 {@code LootModifierManager} 会按 {@code ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS}
 * 里的注册名去 dispatch json 的 {@code type};因此本模组必须自行注册一个,
 * 否则 {@code data/astral_dice/loot_modifiers/*.json} 全部解码失败(见 {@link AddTableLootModifier})。
 *
 * <p>注册名刻意使用本模组命名空间 {@code astral_dice:add_table}。
 */
public final class AstralLootModifiers {
    /** 全局战利品修饰符序列化器注册表(1.20.1 Forge 自带注册表里没有内置项,故本模组补一个) */
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, AstralDiceMod.MODID);

    /** {@code astral_dice:add_table} —— 追加另一张战利品表 */
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_TABLE =
            SERIALIZERS.register("add_table", () -> AddTableLootModifier.CODEC);

    private AstralLootModifiers() {
    }
}
