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

    /**
     * 临时牌标记(绿洲女王 nardis 主动「女王特权」):带此组件的卡牌为「临时牌」——
     * 只有 3:00 有效期、不可丢弃、不可移入其它容器、带附魔光效,效果结束即整体清空。
     *
     * <p>装备进骰子后**不会**丢失临时性:装配会销毁物品栈,标记改由
     * {@link AppliedStone#temporary()} 承载,两者由 {@code screen/CardInventoryMenu} 双向透传
     * (见 {@link AppliedStone} 的类注释)。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> TEMPORARY_CARD =
            DATA_COMPONENTS.registerComponentType("temporary_card",
                    builder -> builder
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * 临时牌的**到期刻**(绝对 {@code gameTime};{@code 0} = 未设限,判据见
     * {@code item/card/TemporaryCardUtil#NO_EXPIRY})。
     *
     * <p><b>为什么必须存在物品上</b>(2026-09-24 用户裁决「为临时效果牌增加自毁机制」):第三方容器
     * (AE2 存储总线 / 机械动力物品舱口这类**不经槽位校验**的插入路径)收走临时牌后,玩家级的效果实例
     * 就"够不着"它了 —— 牌必须**自带**一个与玩家无关也能判定的到期刻,取回时才能立刻自毁,
     * 否则它可以在容器里过夜、跨过整轮有效期再被取回来继续用。
     *
     * <p>语义 = 「发放时刻 + 3:00」;再次释放 / 玩家登录时对**够得着的**牌做确定性对齐
     * (见 {@code TemporaryCardUtil#realignExpiry})。骰子已装配的牌**不写**本组件 —— 那份临时性在
     * {@code AppliedStone#temporary()} 里,生命周期由效果实例直接承载。
     *
     * <p>**刻意不 {@code networkSynchronized}**({@code 0} 字节额外带宽):纯服务端判定,
     * 客户端不读它 —— 客户端可见性由已同步的 {@link #TEMPORARY_CARD} 承载,
     * tooltip 提示行也不显示剩余时间。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> TEMPORARY_CARD_EXPIRES =
            DATA_COMPONENTS.registerComponentType("temporary_card_expires",
                    builder -> builder
                            .persistent(Codec.LONG));

    // 专属效果牌:获得者 UUID(空表示尚未绑定,首次使用时绑定)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> OWNER_UUID =
            DATA_COMPONENTS.registerComponentType("owner_uuid",
                    builder -> builder
                            .persistent(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                            .networkSynchronized(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC)));

}
