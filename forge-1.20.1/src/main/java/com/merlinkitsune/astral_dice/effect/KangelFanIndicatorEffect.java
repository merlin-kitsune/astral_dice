package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「粉丝数」(超天酱立牌 kangel 佩戴者的**计数指示器**;2026-10-17 由 {@link KangelFanEffect} 拆出)。
 *
 * <h3>为什么单独一个 id</h3>
 * 拆前「目标标记」与「佩戴者指示器」共用 {@code kangel_fan} 一个注册 id,而两者的判据口径完全不同:
 * <ul>
 *   <li><b>目标标记</b>({@link KangelFanEffect})是 {@code ChipDamageHandler} −1 减伤与「是否粉丝」的
 *       <b>唯一真值</b>;**玩家身上出现该效果 = 该玩家是被标记的粉丝**;</li>
 *   <li><b>本效果</b>只是佩戴者自己的 HUD 镜像(**等级 = 粉丝数**),**不参与任何战斗判定**。</li>
 * </ul>
 * 共用 id 时有两条真实缺陷(见交接欠账 #2):① 两个超天酱佩戴者互殴时,攻击方身上的指示器被
 * {@code hasEffect(kangel_fan)} 当成「她是粉丝」⇒ 误判 −1;② 被标记成粉丝的超天酱佩戴者,
 * 其自身的计数镜像(先移除再施加)会**清掉别人给她的标记**。拆成两个 id 后两条同时消失。
 *
 * <p>语义边界(唯一读取点):
 * <ul>
 *   <li>施加/刷新 = {@code item/sign/KangelSignItem} 的 {@code setFanCount} / {@code onCurioTick}
 *       (粉丝数真值在附件 {@code ModAttachments#KANGEL_FAN_COUNT});</li>
 *   <li>移除 = 同一类的 {@code clearSignData}(卸下立牌)或粉丝数归 0;</li>
 *   <li>**没有任何战斗判据读本效果** —— −1 减伤只读 {@link KangelFanEffect#has}。</li>
 * </ul>
 *
 * <p>无属性修饰符、无周期反噬;无限时长({@link MobEffectInstance#INFINITE_DURATION}),
 * 计数不随时间衰减,只在粉丝数变化时按「先移除再施加」刷新(照 {@code SherryReasoningEffect} 的镜像口径)。
 * 配色:{@link MobEffectCategory#BENEFICIAL}(对佩戴者而言是展示性状态)。
 * 图标 = {@code textures/mob_effect/kangel_fan_indicator.png}(= {@code images/粉丝.png} 加金色描边的
 * 视觉变体,由 {@code tools/gen_kangel_fan_indicator_texture.py} 确定性生成)。
 */
public class KangelFanIndicatorEffect extends MobEffect {
    /** 无限时长(计数镜像常驻,随粉丝数变化/卸下立牌刷新) */
    public static final int DURATION_TICKS = MobEffectInstance.INFINITE_DURATION;

    public KangelFanIndicatorEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFCD3C);
    }

    /** 当前指示器层数(= 粉丝数镜像;无效果为 0) */
    public static int getCount(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.KANGEL_FAN_INDICATOR.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 把粉丝数镜像为 HUD 指示器(**等级 = 粉丝数**,即 {@code amplifier = count - 1})。 */
    public static void mirrorCount(Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        if (getCount(player) == count) return;
        ModEffectRemoval.remove(player, ModEffects.KANGEL_FAN_INDICATOR.get());
        if (count > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.KANGEL_FAN_INDICATOR.get(),
                    DURATION_TICKS, count - 1, false, false, true));
        }
    }

    /** 移除指示器(卸下立牌或粉丝数归 0 时)。⚠️ 只动指示器,**不得**顺带移除目标标记。 */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.KANGEL_FAN_INDICATOR.get());
    }
}
