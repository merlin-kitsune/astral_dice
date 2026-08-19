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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COOLDOWN_REMAINING =
            DATA_COMPONENTS.registerComponentType("cooldown_remaining",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MISAKI_SIGN_CHARGE =
            DATA_COMPONENTS.registerComponentType("misaki_sign_charge",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> KOMACHI_SIGN_CHARGE =
            DATA_COMPONENTS.registerComponentType("komachi_sign_charge",
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PADMAN_CHARGE =
            DATA_COMPONENTS.registerComponentType("padman_charge",
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> JASMINE_ARMOR_PENALTY_END =
            DATA_COMPONENTS.registerComponentType("jasmine_armor_penalty_end",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    // 专属效果牌:获得者 UUID(空表示尚未绑定,首次使用时绑定)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> OWNER_UUID =
            DATA_COMPONENTS.registerComponentType("owner_uuid",
                    builder -> builder
                            .persistent(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                            .networkSynchronized(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC)));

    // 占星师立牌:主动技能已触发,下次攻击的第一个目标施加"虚弱印记"
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HAIQING_ACTIVE_PENDING =
            DATA_COMPONENTS.registerComponentType("haiqing_active_pending",
                    builder -> builder
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    // 秘密侦探立牌:主动技能已触发,下次攻击的第一个目标施加"隐匿调查"
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BONNIE_ACTIVE_PENDING =
            DATA_COMPONENTS.registerComponentType("bonnie_active_pending",
                    builder -> builder
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    // 立牌主动技能"待命"到期时刻(占星师/秘密侦探等需选择目标的技能:等待期内未释放则自动取消)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SKILL_READY_EXPIRE =
            DATA_COMPONENTS.registerComponentType("skill_ready_expire",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));
}
