package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.UUID;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AstralDiceMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponEnhancement>> WEAPON_ENHANCEMENT =
            DATA_COMPONENTS.registerComponentType("weapon_enhancement",
                    builder -> builder
                            .persistent(WeaponEnhancement.CODEC)
                            .networkSynchronized(WeaponEnhancement.STREAM_CODEC));

    // 护法立牌(misaki):触发骰神赐福累计的被动层数(最大 3 层)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MISAKI_SIGN_STACKS =
            DATA_COMPONENTS.registerComponentType("misaki_sign_stacks",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CARD_USES =
            DATA_COMPONENTS.registerComponentType("card_uses",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PADMAN_ATK_BONUS =
            DATA_COMPONENTS.registerComponentType("padman_atk_bonus",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PADMAN_DEF_BONUS =
            DATA_COMPONENTS.registerComponentType("padman_def_bonus",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    // 上班族立牌:被动攻防数值上次刷新的游戏时刻(用于主动重置计时器)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> PADMAN_LAST_REFRESH =
            DATA_COMPONENTS.registerComponentType("padman_last_refresh",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    // 上班族立牌:赐福期间骰点为1时置位,下次攻击骰点必为6
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PADMAN_FORCE_SIX =
            DATA_COMPONENTS.registerComponentType("padman_force_six",
                    builder -> builder
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> JASMINE_ATK_BONUS =
            DATA_COMPONENTS.registerComponentType("jasmine_atk_bonus",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> JASMINE_DEF_BONUS =
            DATA_COMPONENTS.registerComponentType("jasmine_def_bonus",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    // 专属效果牌:获得者 UUID(空表示尚未绑定,首次使用时绑定)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> OWNER_UUID =
            DATA_COMPONENTS.registerComponentType("owner_uuid",
                    builder -> builder
                            .persistent(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                            .networkSynchronized(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC)));

}
