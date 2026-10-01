package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;

/**
 * 「女王特权」(绿洲女王立牌 nardis 主动):**有限时长 3:00(3600 tick)的状态载体**。
 *
 * <h2>为什么本效果是被动的「真值」</h2>
 * <ul>
 *   <li>HUD 计时器 + 图标(用户需求「女王特权生效时,应显示立牌图标为效果计时器」)= 这条原生效果实例
 *       本身 —— 六参施加且 {@code showIcon=true},由 {@code EffectTimerGuard} 保证严格 20 t/s 流动;</li>
 *   <li>「临时牌是不是还在有效期内」的**唯一判据** = {@link #has(Player)}(效果实例是否存在);
 *       玩家级 tick 的临时牌自检就以此为条件(有临时牌但无本效果 ⇒ 立即清空),
 *       因此**不需要**新增任何玩家附件(1.20.1 侧也就不必改 {@code SYNCED_KEYS})。</li>
 * </ul>
 *
 * <p>本类**不**承载 apply/refresh/remove 的常驻逻辑:时长有限、由 {@link #DURATION_TICKS} 与
 * {@code EffectTimerGuard} 共同维持,到期即自然消失(先例:{@code ZhaoBlessingEffect} 的常驻版本是
 * 另一个极端,本效果是有限时长的普通效果)。
 *
 * <p>图标 = {@code images/绿洲女王立牌.png}(与立牌本体同一张图;实装路径
 * {@code textures/mob_effect/nardis_privilege.png},与 {@code textures/item/nardis_sign.png} 逐字节相同,
 * 先例:zhao / teru 两批的 {@code *_blessing.png} / {@code *_descent.png})。
 *
 * <h2>1.20.1 平台适配</h2>
 * 构造口径与 1.21.1 **完全相同**({@code (MobEffectCategory, int)});唯一差异是 {@link ModEffects}
 * 里的常量类型是 {@code RegistryObject<MobEffect>} ⇒ 所有引用处必须 {@code .get()}
 * (见 {@link #has(Player)} 与 {@code item/card/TemporaryCardUtil#tick}、{@code item/sign/NardisSignItem})。
 */
public class NardisPrivilegeEffect extends MobEffect {
    /** 效果时长:3:00 = 3600 tick(用户需求「有效期 3:00」) */
    public static final int DURATION_TICKS = 3600;

    public NardisPrivilegeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x2ECC71);
    }

    /** 是否处于「女王特权」生效期(临时牌的存活判据:唯一真值 = 效果实例是否存在) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.NARDIS_PRIVILEGE.get());
    }
}
